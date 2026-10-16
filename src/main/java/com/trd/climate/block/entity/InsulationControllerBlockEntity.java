package com.trd.climate.block.entity;

import com.trd.block.entity.ModBlockEntities;
import com.trd.climate.block.InsulationControllerBlock;
import com.trd.climate.room.AirtightHelper;
import com.trd.climate.room.SealedRoom;
import com.trd.climate.room.SealedRoomManager;
import com.trd.climate.room.ValidationResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import java.util.HashSet;
import java.util.Set;

/**
 * BlockEntity контроллера термоизоляции (Tier 1 Bounding Box).
 * Хранит координаты куба, проверяет герметичность внешней оболочки (6 граней),
 * рассчитывает тепловой баланс источников тепла/холода внутри помещения и поддерживает
 * стабильную температуру воздуха.
 */
public class InsulationControllerBlockEntity extends BlockEntity implements SealedRoom {

    public static final int MIN_SIZE = 2;
    public static final int MAX_SIZE = 48;
    private static final float AIR_HEAT_CAPACITY = 0.04f;
    private static final float BASELINE_TEMP = 20.0f; // Базовая комнатная комфортная температура

    private BlockPos cornerA;
    private BlockPos cornerB;
    private boolean isSealed = false;
    private int volume = 0;
    private float roomTemperature = BASELINE_TEMP;
    private AABB interiorBox;
    private int tickCounter = 0;

    // Кеш позиций источников тепла/холода внутри комнаты для оптимизации тика
    private final Set<BlockPos> cachedThermalSources = new HashSet<>();
    private int scanCooldown = 0;

    public InsulationControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INSULATION_CONTROLLER_BE.get(), pos, state);
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

    /**
     * Валидация и герметизация помещения по его внутренним граням (Interior Bounds).
     * Две точки определяют внутренний объем воздуха комнаты (от пола до потолка, от стены до стены).
     * Проверяется герметичность внешней оболочки (пол под комнатой, потолок над комнатой и 4 стены вокруг нее).
     */
    public ValidationResult validateAndSeal(ServerLevel level, BlockPos oppositeCorner, Player player) {
        int minX = Math.min(worldPosition.getX(), oppositeCorner.getX());
        int minY = Math.min(worldPosition.getY(), oppositeCorner.getY());
        int minZ = Math.min(worldPosition.getZ(), oppositeCorner.getZ());
        int maxX = Math.max(worldPosition.getX(), oppositeCorner.getX());
        int maxY = Math.max(worldPosition.getY(), oppositeCorner.getY());
        int maxZ = Math.max(worldPosition.getZ(), oppositeCorner.getZ());

        int dx = maxX - minX + 1;
        int dy = maxY - minY + 1;
        int dz = maxZ - minZ + 1;

        if (dx < MIN_SIZE || dy < MIN_SIZE || dz < MIN_SIZE) {
            return ValidationResult.fail("Помещение слишком мало! Минимальный размер помещения: 2x2x2 блока.");
        }

        if (dx > MAX_SIZE || dy > MAX_SIZE || dz > MAX_SIZE) {
            return ValidationResult.fail("Помещение слишком велико! Максимальный размер стороны: 48 блоков.");
        }

        this.cornerA = this.worldPosition;
        this.cornerB = oppositeCorner.immutable();

        // Проверяем внешнюю оболочку комнаты на герметичность
        LeakInfo leak = findFirstLeak(level);
        if (leak != null) {
            BlockState leakState = level.getBlockState(leak.pos());
            return ValidationResult.leak(leak.pos(), leakState);
        }

        // Все грани оболочки герметичны!
        this.isSealed = true;
        this.volume = dx * dy * dz;
        this.interiorBox = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);

        // Обновляем визуальное состояние блока
        level.setBlock(worldPosition, getBlockState().setValue(InsulationControllerBlock.SEALED, true), 3);

        // Регистрируем в глобальном менеджере
        SealedRoomManager.register(level, worldPosition, this);

        // Первичное сканирование внутренних источников тепла
        rescanInteriorThermalSources(level);

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return ValidationResult.success(this.volume);
    }

    public BlockPos getCornerA() {
        return cornerA;
    }

    public BlockPos getCornerB() {
        return cornerB;
    }

    public record LeakInfo(BlockPos pos, boolean isDoor, String blockName) {}

    /**
     * Поиск первой обнаруженной утечки во внешней оболочке комнаты (пол, потолок, стены).
     * Проверяет грани в строго фиксированном порядке: Пол -> Потолок -> Стены X -> Стены Z.
     * Возвращает информацию о первой утечке (открытая дверь или разрушенный блок).
     */
    @Nullable
    public LeakInfo findFirstLeak(Level level) {
        if (cornerA == null || cornerB == null) {
            return null;
        }

        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int minY = Math.min(cornerA.getY(), cornerB.getY());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int maxY = Math.max(cornerA.getY(), cornerB.getY());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());

        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();

        // 1. Пол под помещением (Y = minY - 1)
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                checkPos.set(x, minY - 1, z);
                BlockState state = level.getBlockState(checkPos);
                if (!AirtightHelper.isAirtight(state, level, checkPos)) {
                    boolean isDoor = isDoorOrTrapdoor(state);
                    return new LeakInfo(checkPos.immutable(), isDoor, state.isAir() ? "Air" : state.getBlock().getName().getString());
                }
            }
        }

        // 2. Потолок над помещением (Y = maxY + 1)
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                checkPos.set(x, maxY + 1, z);
                BlockState state = level.getBlockState(checkPos);
                if (!AirtightHelper.isAirtight(state, level, checkPos)) {
                    boolean isDoor = isDoorOrTrapdoor(state);
                    return new LeakInfo(checkPos.immutable(), isDoor, state.isAir() ? "Air" : state.getBlock().getName().getString());
                }
            }
        }

        // 3. Боковые стены X (Западная X = minX - 1, Восточная X = maxX + 1)
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                // Западная стена
                checkPos.set(minX - 1, y, z);
                BlockState stateW = level.getBlockState(checkPos);
                if (!AirtightHelper.isAirtight(stateW, level, checkPos)) {
                    boolean isDoor = isDoorOrTrapdoor(stateW);
                    return new LeakInfo(checkPos.immutable(), isDoor, stateW.isAir() ? "Air" : stateW.getBlock().getName().getString());
                }

                // Восточная стена
                checkPos.set(maxX + 1, y, z);
                BlockState stateE = level.getBlockState(checkPos);
                if (!AirtightHelper.isAirtight(stateE, level, checkPos)) {
                    boolean isDoor = isDoorOrTrapdoor(stateE);
                    return new LeakInfo(checkPos.immutable(), isDoor, stateE.isAir() ? "Air" : stateE.getBlock().getName().getString());
                }
            }
        }

        // 4. Боковые стены Z (Северная Z = minZ - 1, Южная Z = maxZ + 1)
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                // Северная стена
                checkPos.set(x, y, minZ - 1);
                BlockState stateN = level.getBlockState(checkPos);
                if (!AirtightHelper.isAirtight(stateN, level, checkPos)) {
                    boolean isDoor = isDoorOrTrapdoor(stateN);
                    return new LeakInfo(checkPos.immutable(), isDoor, stateN.isAir() ? "Air" : stateN.getBlock().getName().getString());
                }

                // Южная стена
                checkPos.set(x, y, maxZ + 1);
                BlockState stateS = level.getBlockState(checkPos);
                if (!AirtightHelper.isAirtight(stateS, level, checkPos)) {
                    boolean isDoor = isDoorOrTrapdoor(stateS);
                    return new LeakInfo(checkPos.immutable(), isDoor, stateS.isAir() ? "Air" : stateS.getBlock().getName().getString());
                }
            }
        }

        return null;
    }

    private boolean isDoorOrTrapdoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock;
    }

    /**
     * Формирует лаконичный статус герметичности для HUD / действия игрока.
     */
    public Component getStatusComponent(Level level) {
        if (cornerA == null || cornerB == null) {
            return Component.literal("§eПомещение не настроено. Используйте отвертку для разметки.");
        }

        LeakInfo leak = findFirstLeak(level);
        if (leak != null) {
            if (leak.isDoor()) {
                return Component.literal(String.format(
                        Locale.ROOT,
                        "§cПомещение не герметично! Открыта дверь в [%d, %d, %d]",
                        leak.pos().getX(), leak.pos().getY(), leak.pos().getZ()
                ));
            } else {
                return Component.literal(String.format(
                        Locale.ROOT,
                        "§cПомещение не герметично! Разгерметизация в [%d, %d, %d]",
                        leak.pos().getX(), leak.pos().getY(), leak.pos().getZ()
                ));
            }
        }

        return Component.literal(String.format(
                Locale.ROOT,
                "§aПомещение герметично. Объем: %d бл. Темп: %.1f°C",
                this.volume, this.roomTemperature
        ));
    }

    /**
     * Повторная автоматическая герметизация помещения при устранении всех утечек.
     */
    public void reseal(ServerLevel level) {
        if (this.isSealed || cornerA == null || cornerB == null) return;
        this.isSealed = true;
        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int minY = Math.min(cornerA.getY(), cornerB.getY());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int maxY = Math.max(cornerA.getY(), cornerB.getY());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());
        int dx = maxX - minX + 1;
        int dy = maxY - minY + 1;
        int dz = maxZ - minZ + 1;
        this.volume = dx * dy * dz;
        this.interiorBox = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);

        level.setBlock(worldPosition, getBlockState().setValue(InsulationControllerBlock.SEALED, true), 3);
        SealedRoomManager.register(level, worldPosition, this);
        rescanInteriorThermalSources(level);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /**
     * Быстрая фоновая проверка целостности оболочки комнаты.
     * Если герметичность нарушена (пробит блок, открыта дверь), комната разгерметизируется.
     */
    public boolean checkShellIntegrity(ServerLevel level) {
        if (!isSealed || cornerA == null || cornerB == null) {
            return false;
        }
        LeakInfo leak = findFirstLeak(level);
        if (leak != null) {
            breakSeal(level);
            return false;
        }
        return true;
    }

    /**
     * Разгерметизация помещения.
     */
    public void breakSeal(ServerLevel level) {
        if (!this.isSealed) return;
        this.isSealed = false;
        level.setBlock(worldPosition, getBlockState().setValue(InsulationControllerBlock.SEALED, false), 3);
        SealedRoomManager.unregister(level, worldPosition);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /**
     * Сканирование источников тепла и холода внутри комнаты.
     */
    private void rescanInteriorThermalSources(ServerLevel level) {
        cachedThermalSources.clear();
        if (interiorBox == null) return;

        int minX = (int) Math.floor(interiorBox.minX);
        int minY = (int) Math.floor(interiorBox.minY);
        int minZ = (int) Math.floor(interiorBox.minZ);
        int maxX = (int) Math.floor(interiorBox.maxX);
        int maxY = (int) Math.floor(interiorBox.maxY);
        int maxZ = (int) Math.floor(interiorBox.maxZ);

        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    checkPos.set(x, y, z);
                    BlockState state = level.getBlockState(checkPos);
                    if (isThermalSource(state)) {
                        cachedThermalSources.add(checkPos.immutable());
                    }
                }
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
        for (BlockPos pos : cachedThermalSources) {
            BlockState state = level.getBlockState(pos);
            // Костры
            if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
                totalQ += 15.0f;
            } else if (state.is(Blocks.SOUL_CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
                totalQ += 10.5f;
            }
            // Печи
            else if ((state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER))
                    && state.getValue(AbstractFurnaceBlock.LIT)) {
                totalQ += 12.0f;
            }
            // Лава и огонь
            else if (state.is(Blocks.LAVA)) {
                totalQ += 20.0f;
            } else if (state.is(Blocks.FIRE)) {
                totalQ += 10.0f;
            }
            // Факелы
            else if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
                totalQ += 0.5f;
            }
            // Охладители (лед)
            else if (state.is(Blocks.BLUE_ICE)) {
                totalQ -= 10.0f;
            } else if (state.is(Blocks.PACKED_ICE)) {
                totalQ -= 5.0f;
            } else if (state.is(Blocks.ICE)) {
                totalQ -= 3.0f;
            }
        }
        return totalQ;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, InsulationControllerBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        be.tickCounter++;

        // 1 раз в секунду (20 тиков)
        if (be.tickCounter % 20 == 0) {
            // Проверка целостности и авто-герметизация
            if (be.cornerA != null && be.cornerB != null) {
                if (be.isSealed) {
                    LeakInfo leak = be.findFirstLeak(serverLevel);
                    if (leak != null) {
                        be.breakSeal(serverLevel);
                    }
                } else {
                    LeakInfo leak = be.findFirstLeak(serverLevel);
                    if (leak == null) {
                        be.reseal(serverLevel);
                    }
                }
            }

            if (be.isSealed) {
                // Убеждаемся, что комната зарегистрирована в менеджере (например, после прогрузки чанка)
                SealedRoomManager.register(level, be.worldPosition, be);

                // Каждые 60 тиков обновляем кеш источников внутри комнаты
                if (be.scanCooldown++ > 3) {
                    be.scanCooldown = 0;
                    be.rescanInteriorThermalSources(serverLevel);
                }

                // Расчет температуры по формуле: ΔT = ΣQ / (V * C_air)
                float heatQ = be.calculateHeatBalance(serverLevel);
                float deltaT = heatQ / Math.max(1.0f, be.volume * AIR_HEAT_CAPACITY);
                // Ограничиваем максимальный нагрев/охлаждение воздуха
                deltaT = Math.max(-25.0f, Math.min(60.0f, deltaT));

                float targetTemp = BASELINE_TEMP + deltaT;
                // Плавная адаптация температуры воздуха в помещении
                be.roomTemperature += (targetTemp - be.roomTemperature) * 0.1f;
                be.setChanged();
                level.sendBlockUpdated(be.worldPosition, be.getBlockState(), be.getBlockState(), 3);
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
        if (level != null && !level.isClientSide() && isSealed && interiorBox != null) {
            SealedRoomManager.register(level, worldPosition, this);
        }
    }

    public void onRemoved() {
        if (level != null && isSealed) {
            SealedRoomManager.unregister(level, worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("isSealed", this.isSealed);
        tag.putInt("volume", this.volume);
        tag.putFloat("roomTemp", this.roomTemperature);

        if (cornerA != null) {
            tag.putLong("cornerA", cornerA.asLong());
        }
        if (cornerB != null) {
            tag.putLong("cornerB", cornerB.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.isSealed = tag.getBoolean("isSealed");
        this.volume = tag.getInt("volume");
        if (tag.contains("roomTemp")) {
            this.roomTemperature = tag.getFloat("roomTemp");
        }

        if (tag.contains("cornerA") && tag.contains("cornerB")) {
            this.cornerA = BlockPos.of(tag.getLong("cornerA"));
            this.cornerB = BlockPos.of(tag.getLong("cornerB"));

            int minX = Math.min(cornerA.getX(), cornerB.getX());
            int minY = Math.min(cornerA.getY(), cornerB.getY());
            int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
            int maxX = Math.max(cornerA.getX(), cornerB.getX());
            int maxY = Math.max(cornerA.getY(), cornerB.getY());
            int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());

            this.interiorBox = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        }
    }
}
