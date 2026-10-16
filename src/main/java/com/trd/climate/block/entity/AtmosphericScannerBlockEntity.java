package com.trd.climate.block.entity;

import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.climate.block.AtmosphericScannerBlock;
import com.trd.climate.network.ScannerVisualScanPayload;
import com.trd.climate.room.AirtightHelper;
import com.trd.climate.room.SealedRoom;
import com.trd.climate.room.SealedRoomManager;
import com.trd.climate.room.ValidationResult;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;

/**
 * BlockEntity атмосферного сканера герметичности (Tier 2).
 * Поддерживает объединение нескольких сканеров в кластер для герметизации огромных баз.
 * Каждый сканер в кластере добавляет 3000 блоков к общему лимиту объема и расширяет зону действия (R <= 32).
 * Огибает колонны и мебель (#trd:interior_equipment), рассчитывает тепловой баланс
 * и передает клиенту геометрию первого слоя проверенных блоков для визуализации.
 */
public class AtmosphericScannerBlockEntity extends BlockEntity implements SealedRoom {

    public static final int MAX_RADIUS = 32;
    public static final int MAX_RADIUS_SQ = MAX_RADIUS * MAX_RADIUS; // 1024
    public static final int MAX_VOLUME = 3000;
    private static final float AIR_HEAT_CAPACITY = 0.04f;
    private static final float BASELINE_TEMP = 20.0f;
    private static final int DIRTY_DEBOUNCE_TICKS = 60; // 3 секунды дебаунса для пересчета геометрии

    private boolean isSealed = false;
    private boolean wasSuccessfullySealed = false;
    private int volume = 0;
    private float roomTemperature = BASELINE_TEMP;
    private AABB interiorBox = null;
    private int tickCounter = 0;
    private int scanCooldown = 0;

    /** Флаг и таймер дебаунса при разрушении/установке блоков стен (пересчет раз в 3 сек) */
    private boolean isDirty = false;
    private int dirtyTimer = 0;

    /** Кластер сканеров: хранит координаты всех связанных контроллеров в помещении */
    private final Set<BlockPos> clusterScanners = new HashSet<>();
    private BlockPos masterControllerPos = null;
    private boolean isMaster = true;

    /** Набор всех внутренних позиций воздуха и интерьера комнаты */
    private final LongSet interiorPositions = new LongOpenHashSet();

    /** Набор внешних граничных блоков (пол, потолок, стены, закрытые двери) */
    private final LongSet boundaryPositions = new LongOpenHashSet();

    /** Кеш позиций источников тепла/холода внутри комнаты */
    private final Set<BlockPos> cachedThermalSources = new HashSet<>();

    private BlockPos lastLeakPos = null;
    private boolean lastLeakIsDoor = false;

    public AtmosphericScannerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ATMOSPHERIC_SCANNER_BE.get(), pos, state);
    }

    @Override
    public BlockPos getControllerPos() {
        return this.worldPosition;
    }

    @Override
    public AABB getInteriorBox() {
        return this.interiorBox;
    }

    @Override
    public int getVolume() {
        return this.volume;
    }

    @Override
    public float getRoomTemperature() {
        return this.roomTemperature;
    }

    @Override
    public boolean isSealed() {
        return this.isSealed;
    }

    public boolean wasSuccessfullySealed() {
        return this.wasSuccessfullySealed;
    }

    public boolean isMaster() {
        return this.isMaster;
    }

    public int getClusterSize() {
        return Math.max(1, this.clusterScanners.size());
    }

    /**
     * Поблочная проверка нахождения внутри помещения сложной геометрии.
     * Сначала выполняется отсечение по AABB, затем мгновенный O(1) поиск в LongSet.
     */
    @Override
    public boolean contains(BlockPos pos) {
        if (!isStructureValid() || this.interiorBox == null) {
            return false;
        }
        if (!this.interiorBox.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
            return false;
        }
        return this.interiorPositions.contains(pos.asLong());
    }

    @Override
    public boolean isStructureValid() {
        return this.isSealed || (this.wasSuccessfullySealed && !this.interiorPositions.isEmpty());
    }

    @Override
    public boolean hasLeak() {
        return !this.isSealed && this.wasSuccessfullySealed;
    }

    @Override
    public boolean isBoundary(BlockPos pos) {
        return this.boundaryPositions.contains(pos.asLong());
    }

    @Override
    public void markDirty() {
        if (!this.isDirty) {
            this.isDirty = true;
            this.dirtyTimer = DIRTY_DEBOUNCE_TICKS;
        }
    }

    @Override
    public boolean isDirty() {
        return this.isDirty;
    }

    public record LeakInfo(BlockPos pos, boolean isDoor, String blockName) {}

    /**
     * Запуск волнового сканирования (BFS) и кластеризации сканеров для больших помещений.
     */
    public ValidationResult scanAndSeal(ServerLevel level, @Nullable Player player) {
        // Резервная копия геометрии: если попытка расширения выйдет в бесконечную пещеру,
        // мы сохраняем комнату в статусе утечки, а не уничтожаем ее!
        LongSet backupInterior = new LongOpenHashSet(this.interiorPositions);
        LongSet backupBoundary = new LongOpenHashSet(this.boundaryPositions);
        Set<BlockPos> backupCluster = new java.util.HashSet<>(this.clusterScanners);
        int backupVolume = this.volume;
        AABB backupBox = this.interiorBox;

        interiorPositions.clear();
        boundaryPositions.clear();
        cachedThermalSources.clear();
        lastLeakPos = null;

        BlockPos origin = this.worldPosition;
        LongSet visited = new LongOpenHashSet();
        Queue<BlockPos> queue = new ArrayDeque<>();

        // Сброс и инициализация кластера (начинаем с себя)
        clusterScanners.clear();
        clusterScanners.add(origin.immutable());
        this.isMaster = true;
        this.masterControllerPos = origin.immutable();

        // 1. Немедленный поиск всех вплотную соприкасающихся атмосферных сканеров (встроенных в пол/стену)
        Queue<BlockPos> scannerFloodQueue = new ArrayDeque<>();
        scannerFloodQueue.add(origin.immutable());
        while (!scannerFloodQueue.isEmpty()) {
            BlockPos sPos = scannerFloodQueue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = sPos.relative(dir);
                if (!clusterScanners.contains(neighbor)) {
                    BlockState nState = level.getBlockState(neighbor);
                    if (nState.is(ModBlocks.ATMOSPHERIC_SCANNER.get())) {
                        clusterScanners.add(neighbor.immutable());
                        scannerFloodQueue.add(neighbor.immutable());
                    }
                }
            }
        }

        // Помечаем все сканеры кластера как посещенные и герметичные устройства
        for (BlockPos sPos : clusterScanners) {
            visited.add(sPos.asLong());
            boundaryPositions.add(sPos.asLong());
        }

        int minX = origin.getX();
        int minY = origin.getY();
        int minZ = origin.getZ();
        int maxX = origin.getX();
        int maxY = origin.getY();
        int maxZ = origin.getZ();

        BlockPos doorLeakPos = null;
        BlockState doorLeakState = null;

        // Стартовые точки вокруг всех найденных сканеров кластера
        for (BlockPos sPos : clusterScanners) {
            for (Direction dir : Direction.values()) {
                BlockPos start = sPos.relative(dir);
                if (clusterScanners.contains(start)) {
                    continue;
                }
                long startLong = start.asLong();
                if (visited.contains(startLong)) {
                    continue;
                }

                BlockState state = level.getBlockState(start);
                if (state.is(ModBlocks.ATMOSPHERIC_SCANNER.get())) {
                    clusterScanners.add(start.immutable());
                    visited.add(startLong);
                    boundaryPositions.add(startLong);
                    continue;
                }

                if (AirtightHelper.isAirtight(state, level, start) && !AirtightHelper.isInteriorEquipment(state)) {
                    boundaryPositions.add(startLong);
                    visited.add(startLong);
                } else {
                    if (isDoorOrTrapdoor(state) && !AirtightHelper.isAirtight(state, level, start)) {
                        if (doorLeakPos == null) {
                            doorLeakPos = start;
                            doorLeakState = state;
                        }
                    }
                    visited.add(startLong);
                    interiorPositions.add(startLong);
                    queue.add(start);

                    minX = Math.min(minX, start.getX());
                    minY = Math.min(minY, start.getY());
                    minZ = Math.min(minZ, start.getZ());
                    maxX = Math.max(maxX, start.getX());
                    maxY = Math.max(maxY, start.getY());
                    maxZ = Math.max(maxZ, start.getZ());
                }
            }
        }

        if (interiorPositions.isEmpty()) {
            if (this.wasSuccessfullySealed && !backupInterior.isEmpty()) {
                return handleBreachFallback(level, origin, false, backupInterior, backupBoundary, backupCluster, backupVolume, backupBox);
            }
            resetScanFailure(level, player, this.interiorPositions.toLongArray());
            return ValidationResult.fail("Сканер заблокирован со всех сторон! Обеспечьте доступ к воздуху помещения.");
        }

        // Основной цикл BFS
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                long nextLong = next.asLong();

                if (visited.contains(nextLong)) {
                    continue;
                }

                BlockState state = level.getBlockState(next);

                // Если встретили атмосферный сканер в помещении во время распространения волны
                if (state.is(ModBlocks.ATMOSPHERIC_SCANNER.get())) {
                    visited.add(nextLong);
                    boundaryPositions.add(nextLong);

                    if (!clusterScanners.contains(next)) {
                        boolean inRangeOfCluster = false;
                        for (BlockPos sPos : clusterScanners) {
                            if (sPos.distSqr(next) <= MAX_RADIUS_SQ) {
                                inRangeOfCluster = true;
                                break;
                            }
                        }
                        if (inRangeOfCluster) {
                            clusterScanners.add(next.immutable());
                            // Проверяем, есть ли рядом с найденным сканером еще сканеры впритык
                            for (Direction sDir : Direction.values()) {
                                BlockPos sn = next.relative(sDir);
                                if (!clusterScanners.contains(sn) && level.getBlockState(sn).is(ModBlocks.ATMOSPHERIC_SCANNER.get())) {
                                    clusterScanners.add(sn.immutable());
                                }
                            }
                        }
                    }
                    continue;
                }

                // Если блок герметичен и не является внутренней мебелью/оборудованием — это оболочка (стена)
                if (AirtightHelper.isAirtight(state, level, next) && !AirtightHelper.isInteriorEquipment(state)) {
                    boundaryPositions.add(nextLong);
                    visited.add(nextLong);
                    continue;
                }

                // Запоминаем открытую дверь/люк как вероятное место утечки
                if (isDoorOrTrapdoor(state) && !AirtightHelper.isAirtight(state, level, next)) {
                    if (doorLeakPos == null) {
                        doorLeakPos = next;
                        doorLeakState = state;
                    }
                }

                // Проверка радиуса: блок должен быть в пределах 32 блоков хотя бы от одного сканера кластера
                boolean withinClusterRange = false;
                for (BlockPos sPos : clusterScanners) {
                    if (sPos.distSqr(next) <= MAX_RADIUS_SQ) {
                        withinClusterRange = true;
                        break;
                    }
                }

                if (!withinClusterRange) {
                    if (this.wasSuccessfullySealed && !backupInterior.isEmpty()) {
                        return handleBreachFallback(level, (doorLeakPos != null) ? doorLeakPos : next, (doorLeakPos != null), backupInterior, backupBoundary, backupCluster, backupVolume, backupBox);
                    }
                    long[] scanned = interiorPositions.toLongArray();
                    resetScanFailure(level, player, scanned);
                    if (doorLeakPos != null) {
                        this.lastLeakPos = doorLeakPos;
                        this.lastLeakIsDoor = true;
                        return ValidationResult.leak(doorLeakPos, doorLeakState);
                    }
                    return ValidationResult.fail(String.format(Locale.ROOT,
                            "Помещение не герметично! Волна вышла за пределы радиуса 32 блоков от сканеров кластера возле [%d, %d, %d].",
                            next.getX(), next.getY(), next.getZ()));
                }

                // Проверка максимального объема с учетом суммирования мощности всех сканеров кластера
                int maxClusterVolume = clusterScanners.size() * MAX_VOLUME;
                if (interiorPositions.size() >= maxClusterVolume) {
                    if (this.wasSuccessfullySealed && !backupInterior.isEmpty()) {
                        return handleBreachFallback(level, (doorLeakPos != null) ? doorLeakPos : next, (doorLeakPos != null), backupInterior, backupBoundary, backupCluster, backupVolume, backupBox);
                    }
                    long[] scanned = interiorPositions.toLongArray();
                    resetScanFailure(level, player, scanned);
                    if (doorLeakPos != null) {
                        this.lastLeakPos = doorLeakPos;
                        this.lastLeakIsDoor = true;
                        return ValidationResult.leak(doorLeakPos, doorLeakState);
                    }
                    return ValidationResult.fail(String.format(Locale.ROOT,
                            "Помещение не герметично! Объем превысил лимит (%d бл. на %d сканер(ов)). Помещение слишком большое или не изолировано.",
                            maxClusterVolume, clusterScanners.size()));
                }

                visited.add(nextLong);
                interiorPositions.add(nextLong);
                queue.add(next);

                minX = Math.min(minX, next.getX());
                minY = Math.min(minY, next.getY());
                minZ = Math.min(minZ, next.getZ());
                maxX = Math.max(maxX, next.getX());
                maxY = Math.max(maxY, next.getY());
                maxZ = Math.max(maxZ, next.getZ());
            }
        }

        // Сканирование успешно завершено — помещение полностью герметично!
        if (!this.wasSuccessfullySealed) {
            this.roomTemperature = com.trd.climate.calculation.TemperatureCalculator.calculateOutdoorAmbient(level, worldPosition);
        }
        this.isSealed = true;
        this.wasSuccessfullySealed = true;
        this.volume = interiorPositions.size();
        this.interiorBox = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        this.lastLeakPos = null;

        level.setBlock(worldPosition, getBlockState().setValue(AtmosphericScannerBlock.SEALED, true), 3);
        SealedRoomManager.register(level, worldPosition, this);
        rescanInteriorThermalSources(level);

        // Синхронизируем все ведомые сканеры в кластере
        for (BlockPos sPos : clusterScanners) {
            if (!sPos.equals(this.worldPosition)) {
                BlockEntity otherBe = level.getBlockEntity(sPos);
                if (otherBe instanceof AtmosphericScannerBlockEntity otherScanner) {
                    otherScanner.isMaster = false;
                    otherScanner.masterControllerPos = this.worldPosition;
                    otherScanner.isSealed = true;
                    otherScanner.wasSuccessfullySealed = true;
                    otherScanner.volume = this.volume;
                    otherScanner.roomTemperature = this.roomTemperature;
                    otherScanner.clusterScanners.clear();
                    otherScanner.clusterScanners.addAll(this.clusterScanners);
                    otherScanner.setChanged();
                    level.setBlock(sPos, otherScanner.getBlockState().setValue(AtmosphericScannerBlock.SEALED, true), 3);
                    level.sendBlockUpdated(sPos, otherScanner.getBlockState(), otherScanner.getBlockState(), 3);
                }
            }
        }

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

        // Отправляем пакет визуализации на клиент
        sendVisualScan(level, player, true, this.interiorPositions.toLongArray());

        return ValidationResult.success(this.volume);
    }

    /**
     * Обработка разгерметизации ранее изолированного помещения (пробоина в стене в соседнюю пещеру или открыта дверь).
     * Комната НЕ уничтожается: восстанавливается исходная геометрия, комната остается зарегистрированной,
     * а температура начинает медленно утекать пропорционально объему пещеры.
     */
    private ValidationResult handleBreachFallback(
            ServerLevel level,
            BlockPos leakPos,
            boolean isDoor,
            LongSet backupInterior,
            LongSet backupBoundary,
            Set<BlockPos> backupCluster,
            int backupVolume,
            AABB backupBox
    ) {
        this.interiorPositions.clear();
        this.interiorPositions.addAll(backupInterior);
        this.boundaryPositions.clear();
        this.boundaryPositions.addAll(backupBoundary);
        this.clusterScanners.clear();
        this.clusterScanners.addAll(backupCluster);
        this.volume = backupVolume;
        this.interiorBox = backupBox;

        // Определяем точную координату утечки на границе комнаты
        LeakInfo exactLeak = findFirstBoundaryLeak(level);
        if (exactLeak != null) {
            leakPos = exactLeak.pos();
            isDoor = exactLeak.isDoor();
        }

        this.isSealed = false;
        this.lastLeakPos = leakPos;
        this.lastLeakIsDoor = isDoor;

        level.setBlock(worldPosition, getBlockState().setValue(AtmosphericScannerBlock.SEALED, false), 3);
        SealedRoomManager.register(level, worldPosition, this);

        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                leakPos.getX() + 0.5, leakPos.getY() + 0.5, leakPos.getZ() + 0.5,
                15, 0.25, 0.25, 0.25, 0.05);

        for (BlockPos sPos : clusterScanners) {
            if (!sPos.equals(this.worldPosition)) {
                BlockEntity otherBe = level.getBlockEntity(sPos);
                if (otherBe instanceof AtmosphericScannerBlockEntity otherScanner) {
                    otherScanner.isSealed = false;
                    otherScanner.lastLeakPos = leakPos;
                    otherScanner.lastLeakIsDoor = isDoor;
                    otherScanner.volume = this.volume;
                    otherScanner.clusterScanners.clear();
                    otherScanner.clusterScanners.addAll(this.clusterScanners);
                    otherScanner.setChanged();
                    level.setBlock(sPos, otherScanner.getBlockState().setValue(AtmosphericScannerBlock.SEALED, false), 3);
                    level.sendBlockUpdated(sPos, otherScanner.getBlockState(), otherScanner.getBlockState(), 3);
                }
            }
        }

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

        BlockState leakState = level.getBlockState(leakPos);
        return ValidationResult.leak(leakPos, leakState);
    }

    /**
     * Сброс состояния при неудачном сканировании.
     * Очищает память комнаты, чтобы сервер не считал незамкнутый объем герметичным.
     */
    private void resetScanFailure(ServerLevel level, @Nullable Player player, long[] scannedPositions) {
        this.isSealed = false;
        this.wasSuccessfullySealed = false;
        this.volume = 0;
        this.interiorBox = null;
        this.lastLeakPos = null;

        sendVisualScan(level, player, false, scannedPositions);

        this.interiorPositions.clear();
        this.boundaryPositions.clear();
        this.cachedThermalSources.clear();

        level.setBlock(worldPosition, getBlockState().setValue(AtmosphericScannerBlock.SEALED, false), 3);
        for (BlockPos sPos : clusterScanners) {
            if (!sPos.equals(this.worldPosition)) {
                BlockEntity otherBe = level.getBlockEntity(sPos);
                if (otherBe instanceof AtmosphericScannerBlockEntity otherScanner) {
                    otherScanner.isSealed = false;
                    otherScanner.wasSuccessfullySealed = false;
                    otherScanner.volume = 0;
                    otherScanner.interiorPositions.clear();
                    otherScanner.boundaryPositions.clear();
                    otherScanner.setChanged();
                    level.setBlock(sPos, otherScanner.getBlockState().setValue(AtmosphericScannerBlock.SEALED, false), 3);
                    level.sendBlockUpdated(sPos, otherScanner.getBlockState(), otherScanner.getBlockState(), 3);
                }
            }
        }
        this.clusterScanners.clear();
        this.isMaster = true;
        this.masterControllerPos = null;

        SealedRoomManager.unregister(level, worldPosition);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /**
     * Отправляет пакет визуализации объема игроку и всем игрокам вокруг.
     */
    private void sendVisualScan(ServerLevel level, @Nullable Player player, boolean isSuccess, long[] positions) {
        if (positions == null || positions.length == 0) {
            return;
        }
        ScannerVisualScanPayload payload = new ScannerVisualScanPayload(
                this.worldPosition,
                positions,
                isSuccess,
                this.volume > 0 ? this.volume : positions.length,
                Math.max(1, this.clusterScanners.size())
        );
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, payload);
        }
        PacketDistributor.sendToPlayersTrackingChunk(
                level, new ChunkPos(worldPosition), payload
        );
    }

    /**
     * Запуск сканирования с отправкой сообщений и воспроизведением эффектов.
     */
    public void triggerScan(ServerLevel level, @Nullable Player player) {
        ValidationResult result = scanAndSeal(level, player);
        if (result.isSuccess()) {
            if (player != null) {
                if (this.clusterScanners.size() > 1) {
                    player.displayClientMessage(Component.literal(String.format(
                            Locale.ROOT,
                            "§aПомещение успешно герметизировано! Кластер: %d сканеров. Объем: %d / %d блоков.",
                            this.clusterScanners.size(), result.volume(), this.clusterScanners.size() * MAX_VOLUME
                    )), false);
                } else {
                    player.displayClientMessage(Component.translatable(
                            "message.trd.insulation.scanner_sealed_success", result.volume()
                    ), false);
                }
            }
            level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5,
                    10, 0.3, 0.3, 0.3, 0.05);
        } else {
            BlockPos leak = result.leakPos();
            if (leak != null) {
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        leak.getX() + 0.5, leak.getY() + 0.5, leak.getZ() + 0.5,
                        15, 0.25, 0.25, 0.25, 0.05);

                if (result.isDoor()) {
                    if (player != null) {
                        player.displayClientMessage(Component.literal(String.format(
                                Locale.ROOT,
                                "§cПомещение не герметично! Открыта дверь в [%d, %d, %d]",
                                leak.getX(), leak.getY(), leak.getZ()
                        )), false);
                    }
                } else {
                    if (player != null) {
                        player.displayClientMessage(Component.literal(String.format(
                                Locale.ROOT,
                                "§cПомещение не герметично! Разгерметизация в [%d, %d, %d] (%s)",
                                leak.getX(), leak.getY(), leak.getZ(), result.leakBlockName() != null ? result.leakBlockName() : "Unknown"
                        )), false);
                    }
                }
            } else {
                if (player != null) {
                    player.displayClientMessage(Component.literal(
                            result.errorMessage() != null ? "§c" + result.errorMessage() : "§cОшибка герметизации!"
                    ), false);
                }
            }
            level.playSound(null, worldPosition, SoundEvents.SHIELD_BLOCK, SoundSource.BLOCKS, 1.0f, 0.8f);
        }
    }

    /**
     * Поиск первой обнаруженной утечки в границах помещения.
     */
    @Nullable
    public LeakInfo findFirstBoundaryLeak(Level level) {
        if (boundaryPositions.isEmpty()) {
            return null;
        }
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        for (long posLong : boundaryPositions) {
            checkPos.set(BlockPos.getX(posLong), BlockPos.getY(posLong), BlockPos.getZ(posLong));
            BlockState state = level.getBlockState(checkPos);
            if (!AirtightHelper.isAirtight(state, level, checkPos)) {
                boolean isDoor = isDoorOrTrapdoor(state);
                return new LeakInfo(checkPos.immutable(), isDoor, state.isAir() ? "Air" : state.getBlock().getName().getString());
            }
        }
        return null;
    }

    private boolean isDoorOrTrapdoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock;
    }

    /**
     * Разгерметизация помещения и всего связанного кластера сканеров.
     */
    public void breakSeal(ServerLevel level, @Nullable BlockPos leakPos, boolean isDoor) {
        if (!this.isSealed) return;
        this.isSealed = false;
        this.lastLeakPos = leakPos;
        this.lastLeakIsDoor = isDoor;
        level.setBlock(worldPosition, getBlockState().setValue(AtmosphericScannerBlock.SEALED, false), 3);
        // Сохраняем комнату в менеджере: она переходит в статус разгерметизации с медленной утечкой
        SealedRoomManager.register(level, worldPosition, this);

        for (BlockPos sPos : clusterScanners) {
            if (!sPos.equals(this.worldPosition)) {
                BlockEntity otherBe = level.getBlockEntity(sPos);
                if (otherBe instanceof AtmosphericScannerBlockEntity otherScanner) {
                    otherScanner.isSealed = false;
                    otherScanner.lastLeakPos = leakPos;
                    otherScanner.lastLeakIsDoor = isDoor;
                    otherScanner.setChanged();
                    level.setBlock(sPos, otherScanner.getBlockState().setValue(AtmosphericScannerBlock.SEALED, false), 3);
                    level.sendBlockUpdated(sPos, otherScanner.getBlockState(), otherScanner.getBlockState(), 3);
                }
            }
        }

        if (leakPos != null) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    leakPos.getX() + 0.5, leakPos.getY() + 0.5, leakPos.getZ() + 0.5,
                    8, 0.2, 0.2, 0.2, 0.05);
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void breakSeal(ServerLevel level) {
        breakSeal(level, null, false);
    }

    /**
     * Автоматическая повторная герметизация при устранении утечек.
     * Срабатывает ТОЛЬКО если комната ранее была успешно герметизирована.
     */
    public void reseal(ServerLevel level) {
        if (this.isSealed || !this.wasSuccessfullySealed || boundaryPositions.isEmpty() || interiorPositions.isEmpty()) return;
        this.isSealed = true;
        this.lastLeakPos = null;
        this.volume = interiorPositions.size();

        level.setBlock(worldPosition, getBlockState().setValue(AtmosphericScannerBlock.SEALED, true), 3);
        SealedRoomManager.register(level, worldPosition, this);
        rescanInteriorThermalSources(level);

        for (BlockPos sPos : clusterScanners) {
            if (!sPos.equals(this.worldPosition)) {
                BlockEntity otherBe = level.getBlockEntity(sPos);
                if (otherBe instanceof AtmosphericScannerBlockEntity otherScanner) {
                    otherScanner.isSealed = true;
                    otherScanner.wasSuccessfullySealed = true;
                    otherScanner.lastLeakPos = null;
                    otherScanner.volume = this.volume;
                    otherScanner.setChanged();
                    level.setBlock(sPos, otherScanner.getBlockState().setValue(AtmosphericScannerBlock.SEALED, true), 3);
                    level.sendBlockUpdated(sPos, otherScanner.getBlockState(), otherScanner.getBlockState(), 3);
                }
            }
        }

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /**
     * Полное отключение герметизации помещения вручную.
     * Сбрасывает сохраненную геометрию комнаты.
     */
    public void unsealManual(ServerLevel level) {
        this.isSealed = false;
        this.wasSuccessfullySealed = false;
        this.volume = 0;
        this.interiorBox = null;
        this.lastLeakPos = null;

        this.interiorPositions.clear();
        this.boundaryPositions.clear();
        this.cachedThermalSources.clear();

        level.setBlock(worldPosition, getBlockState().setValue(AtmosphericScannerBlock.SEALED, false), 3);
        for (BlockPos sPos : clusterScanners) {
            if (!sPos.equals(this.worldPosition)) {
                BlockEntity otherBe = level.getBlockEntity(sPos);
                if (otherBe instanceof AtmosphericScannerBlockEntity otherScanner) {
                    otherScanner.isSealed = false;
                    otherScanner.wasSuccessfullySealed = false;
                    otherScanner.volume = 0;
                    otherScanner.interiorPositions.clear();
                    otherScanner.boundaryPositions.clear();
                    otherScanner.setChanged();
                    level.setBlock(sPos, otherScanner.getBlockState().setValue(AtmosphericScannerBlock.SEALED, false), 3);
                    level.sendBlockUpdated(sPos, otherScanner.getBlockState(), otherScanner.getBlockState(), 3);
                }
            }
        }
        this.clusterScanners.clear();
        this.isMaster = true;
        this.masterControllerPos = null;

        SealedRoomManager.unregister(level, worldPosition);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /**
     * Формирует лаконичный статус герметичности для HUD и сообщений.
     */
    public Component getStatusComponent(Level level) {
        if (isDirty) {
            return Component.literal(String.format(
                    Locale.ROOT,
                    "§eАдаптация геометрии... Объем: %d бл. Темп: %.1f°C",
                    this.volume, this.roomTemperature
            ));
        }

        if (!isSealed && wasSuccessfullySealed && !boundaryPositions.isEmpty()) {
            LeakInfo leak = findFirstBoundaryLeak(level);
            BlockPos lPos = (leak != null) ? leak.pos() : (lastLeakPos != null ? lastLeakPos : worldPosition);
            boolean isDoor = (leak != null) ? leak.isDoor() : lastLeakIsDoor;
            String name = (leak != null) ? leak.blockName() : (level.getBlockState(lPos).isAir() ? "Air" : level.getBlockState(lPos).getBlock().getName().getString());
            if (isDoor) {
                return Component.literal(String.format(
                        Locale.ROOT,
                        "§cРазгерметизация! Открыта дверь в [%d, %d, %d]. Объем: %d бл. Темп: %.1f°C",
                        lPos.getX(), lPos.getY(), lPos.getZ(), this.volume, this.roomTemperature
                ));
            } else {
                return Component.literal(String.format(
                        Locale.ROOT,
                        "§cРазгерметизация! Утечка в [%d, %d, %d] (%s). Объем: %d бл. Темп: %.1f°C",
                        lPos.getX(), lPos.getY(), lPos.getZ(), name, this.volume, this.roomTemperature
                ));
            }
        }

        if (!isSealed) {
            return Component.literal("§eПомещение не изолировано. Нажмите ПКМ по сканеру для сканирования.");
        }

        if (this.clusterScanners.size() > 1) {
            return Component.literal(String.format(
                    Locale.ROOT,
                    "§aПомещение герметично (Кластер: %d сканеров). Объем: %d / %d бл. Темп: %.1f°C",
                    this.clusterScanners.size(), this.volume, this.clusterScanners.size() * MAX_VOLUME, this.roomTemperature
            ));
        }

        return Component.literal(String.format(
                Locale.ROOT,
                "§aПомещение герметично (Сканер). Объем: %d / %d бл. Темп: %.1f°C",
                this.volume, MAX_VOLUME, this.roomTemperature
        ));
    }

    /**
     * Сканирование источников тепла и холода внутри объема комнаты.
     */
    private void rescanInteriorThermalSources(ServerLevel level) {
        cachedThermalSources.clear();
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        for (long posLong : interiorPositions) {
            checkPos.set(BlockPos.getX(posLong), BlockPos.getY(posLong), BlockPos.getZ(posLong));
            BlockState state = level.getBlockState(checkPos);
            if (isThermalSource(state)) {
                cachedThermalSources.add(checkPos.immutable());
            }
        }
    }

    private static boolean isThermalSource(BlockState state) {
        return state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.FURNACE)
                || state.is(Blocks.BLAST_FURNACE)
                || state.is(Blocks.SMOKER)
                || state.is(Blocks.LAVA)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.TORCH)
                || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.ICE);
    }

    /**
     * Расчет суммарного теплового баланса источников внутри помещения.
     */
    private float calculateHeatBalance(ServerLevel level) {
        float totalQ = 0.0f;
        boolean isLargeRoom = this.volume > 500;

        for (BlockPos pos : cachedThermalSources) {
            BlockState state = level.getBlockState(pos);
            // Костры и факелы не имеют импакта в комнатах больше 500 блоков!
            if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
                if (!isLargeRoom) {
                    totalQ += 15.0f;
                }
            } else if (state.is(Blocks.SOUL_CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
                if (!isLargeRoom) {
                    totalQ += 10.5f;
                }
            } else if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
                if (!isLargeRoom) {
                    totalQ += 0.5f;
                }
            } else if ((state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER))
                    && state.getValue(AbstractFurnaceBlock.LIT)) {
                totalQ += 12.0f;
            } else if (state.is(Blocks.LAVA)) {
                totalQ += 20.0f;
            } else if (state.is(Blocks.FIRE)) {
                totalQ += 10.0f;
            } else if (state.is(Blocks.BLUE_ICE)) {
                totalQ -= 10.0f;
            } else if (state.is(Blocks.PACKED_ICE)) {
                totalQ -= 5.0f;
            } else if (state.is(Blocks.ICE)) {
                totalQ -= 3.0f;
            }
        }
        return totalQ;
    }

    /**
     * Серверный тик: проверка целостности оболочки и расчет температуры.
     * Ведомые сканеры не тратят ресурсы процессора — проверку выполняет только Master-сканер!
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, AtmosphericScannerBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Если сканер ведомый в кластере, он не выполняет расчеты повторно
        if (!be.isMaster) {
            return;
        }

        be.tickCounter++;

        // Обработка 3-секундного дебаунса изменения геометрии
        if (be.isDirty) {
            be.dirtyTimer--;
            if (be.dirtyTimer <= 0) {
                be.isDirty = false;
                // Запускаем пересчет геометрии (BFS) под новую форму пещеры/комнаты
                ValidationResult result = be.scanAndSeal(serverLevel, null);
                if (!result.isSuccess()) {
                    // Сканер физически не смог заполнить пространство (пробили стену в мир / превышен объем)
                    be.breakSeal(serverLevel, result.leakPos(), result.isDoor());
                }
            }
        }

        // 1 раз в секунду (каждые 20 тиков)
        if (be.tickCounter % 20 == 0) {
            if (be.isSealed) {
                // Если таймер dirty активен, мы ждем завершения 3 секунд и не разгерметизируем комнату
                if (!be.isDirty) {
                    LeakInfo leak = be.findFirstBoundaryLeak(serverLevel);
                    if (leak != null) {
                        if (leak.isDoor()) {
                            be.breakSeal(serverLevel, leak.pos(), true);
                        } else {
                            // Обнаружен сломанный блок стены/породы, запускаем дебаунс-пересчет
                            be.markDirty();
                        }
                    }
                }
            } else if (be.wasSuccessfullySealed && !be.boundaryPositions.isEmpty() && !be.interiorPositions.isEmpty()) {
                // Комната в статусе разгерметизации (утечка):
                LeakInfo leak = be.findFirstBoundaryLeak(serverLevel);
                if (leak == null) {
                    // Утечка устранена (игрок закрыл пробоину блоком)!
                    be.reseal(serverLevel);
                } else {
                    be.lastLeakPos = leak.pos();
                    be.lastLeakIsDoor = leak.isDoor();
                    serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            leak.pos().getX() + 0.5, leak.pos().getY() + 0.5, leak.pos().getZ() + 0.5,
                            4, 0.2, 0.2, 0.2, 0.04);
                }
            }

            // РАСЧЕТ ТЕМПЕРАТУРЫ:
            // Работает и для полностью герметичной комнаты, и для комнаты в статусе разгерметизации!
            if (be.isStructureValid()) {
                SealedRoomManager.register(level, be.worldPosition, be);

                // 1. Физика климата: расчет естественной температуры среды снаружи
                float ambientTemp = com.trd.climate.calculation.TemperatureCalculator.calculateOutdoorAmbient(serverLevel, be.worldPosition);

                if (be.isSealed) {
                    if (be.scanCooldown++ > 3) {
                        be.scanCooldown = 0;
                        be.rescanInteriorThermalSources(serverLevel);
                    }

                    // 2. Обновление температуры с учетом объема воздуха, термоинерции, буста малых баз и потолка каждого источника
                    be.roomTemperature = com.trd.climate.room.RoomClimateHelper.updateRoomTemperature(
                            be.roomTemperature, ambientTemp, be.volume, serverLevel, be.cachedThermalSources
                    );
                } else {
                    // 3. При разгерметизации: утечка тепла в окружающую среду с учетом открытой двери
                    be.roomTemperature = com.trd.climate.room.RoomClimateHelper.updateBreachedRoomTemperature(
                            be.roomTemperature, ambientTemp, be.volume, be.lastLeakIsDoor
                    );
                }

                be.setChanged();
                level.sendBlockUpdated(be.worldPosition, be.getBlockState(), be.getBlockState(), 3);

                for (BlockPos sPos : be.clusterScanners) {
                    if (!sPos.equals(be.worldPosition)) {
                        BlockEntity otherBe = serverLevel.getBlockEntity(sPos);
                        if (otherBe instanceof AtmosphericScannerBlockEntity otherScanner) {
                            otherScanner.roomTemperature = be.roomTemperature;
                            otherScanner.volume = be.volume;
                            otherScanner.setChanged();
                            level.sendBlockUpdated(sPos, otherScanner.getBlockState(), otherScanner.getBlockState(), 3);
                        }
                    }
                }
            }
        }
    }


    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide() && isStructureValid() && isMaster) {
            SealedRoomManager.register(level, worldPosition, this);
        }
    }

    public void onRemoved() {
        if (level instanceof ServerLevel serverLevel && isSealed) {
            unsealManual(serverLevel);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("isSealed", this.isSealed);
        tag.putBoolean("wasSuccessfullySealed", this.wasSuccessfullySealed);
        tag.putInt("volume", this.volume);
        tag.putFloat("roomTemp", this.roomTemperature);
        tag.putBoolean("isMaster", this.isMaster);

        if (masterControllerPos != null) {
            tag.putLong("masterPos", masterControllerPos.asLong());
        }

        if (!clusterScanners.isEmpty()) {
            long[] clusterArr = new long[clusterScanners.size()];
            int idx = 0;
            for (BlockPos p : clusterScanners) {
                clusterArr[idx++] = p.asLong();
            }
            tag.putLongArray("cluster", clusterArr);
        }

        tag.putLongArray("interior", this.interiorPositions.toLongArray());
        tag.putLongArray("boundary", this.boundaryPositions.toLongArray());

        if (interiorBox != null) {
            tag.putDouble("minX", interiorBox.minX);
            tag.putDouble("minY", interiorBox.minY);
            tag.putDouble("minZ", interiorBox.minZ);
            tag.putDouble("maxX", interiorBox.maxX);
            tag.putDouble("maxY", interiorBox.maxY);
            tag.putDouble("maxZ", interiorBox.maxZ);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.isSealed = tag.getBoolean("isSealed");
        this.wasSuccessfullySealed = tag.getBoolean("wasSuccessfullySealed");
        this.volume = tag.getInt("volume");
        if (tag.contains("roomTemp")) {
            this.roomTemperature = tag.getFloat("roomTemp");
        }
        if (tag.contains("isMaster")) {
            this.isMaster = tag.getBoolean("isMaster");
        }
        if (tag.contains("masterPos")) {
            this.masterControllerPos = BlockPos.of(tag.getLong("masterPos"));
        }

        this.clusterScanners.clear();
        if (tag.contains("cluster")) {
            for (long l : tag.getLongArray("cluster")) {
                this.clusterScanners.add(BlockPos.of(l));
            }
        }

        this.interiorPositions.clear();
        for (long l : tag.getLongArray("interior")) {
            this.interiorPositions.add(l);
        }

        this.boundaryPositions.clear();
        for (long l : tag.getLongArray("boundary")) {
            this.boundaryPositions.add(l);
        }

        if (tag.contains("minX") && tag.contains("maxX")) {
            this.interiorBox = new AABB(
                    tag.getDouble("minX"), tag.getDouble("minY"), tag.getDouble("minZ"),
                    tag.getDouble("maxX"), tag.getDouble("maxY"), tag.getDouble("maxZ")
            );
        }
    }
}
