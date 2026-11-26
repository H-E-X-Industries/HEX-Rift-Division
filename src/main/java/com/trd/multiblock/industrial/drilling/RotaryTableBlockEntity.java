package com.trd.multiblock.industrial.drilling;

import com.trd.api.industiral.rotation.KineticNetworkManager;
import com.trd.api.industiral.rotation.Rotational;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.deco.BeamCollisionBlockEntity;
import com.trd.block.entity.industrial.rotation.KineticNodeBlockEntity;
import com.trd.multiblock.industrial.drilling.oilrig.core.*;
import com.trd.multiblock.system.MultiblockPartEntity;
import com.trd.multiblock.system.MultiblockStructureHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BlockEntity роторного стола нефтевышки (RotaryTable).
 * Реализует кинетику (оптимум 45..75 RPM), опрос редстоуна со всех блоков структуры,
 * авто-сборку каркаса вышки из верхнего кольца 5×5 + 4 P2P-балок с сохранением и возвратом ресурсов,
 * а также управление буровой колонной и синхронизацию 340-тиковой анимации.
 */
public class RotaryTableBlockEntity extends KineticNodeBlockEntity implements IDrillDriveModule {

    public static final BlockPos LOCAL_HOLE_FROM_CTRL = new BlockPos(0, 0, -1);
    public static final BlockPos LOCAL_KINETIC_PORT_FROM_CTRL = new BlockPos(1, 0, -1);
    public static final int RING_HEIGHT_FROM_PLATFORM = 20;

    private static final int[][] STRUCTURE_OFFSETS_3X3 = {
            {-1, -2}, {0, -2}, {1, -2},
            {-1, -1},          {1, -1},
            {-1,  0}, {0,  0}, {1,  0}
    };

    private boolean redstoneEnabled = false;
    private boolean derrickAssembled = false;
    private boolean platformValid = false;
    private boolean hasPipeDrumAttached = false;
    private boolean extractingStopped = false;

    private int savedDecoSteelCount = 0;
    private int savedBeamBlockCount = 0;
    private final List<BlockPos> derrickCollisionPositions = new ArrayList<>();

    private int towerAnimTick = 0;
    private boolean towerAnimPaused = true;
    private boolean holdingPipe = false;
    private int pipeCountInWell = 0;

    // Состояние плавного спуска 4-блочной трубы
    private boolean descendingPipe = false;
    private int descentStepIndex = 0;
    private float descentStepFraction = 0.0f;
    private int rockDelayTicks = 0;
    private boolean waitingOnRock = false;
    @Nullable
    private BlockPos detectedPocketPos;

    // Клиентская интерполяция
    private float visualAnimTick = 0.0f;
    private float visualDescentBlocks = 0.0f;
    private float frozenDiskAngle = 0.0f;
    private boolean lastVisualAssembled = false;
    private boolean lastVisualHologram = false;

    private boolean brokenInCreative = false;
    private boolean detonatingBlowout = false;
    private int structureScanCooldown = 0;
    private int syncCounter = 0;

    @Nullable
    private transient OilRigAssembly assembly;
    private final BlockPos.MutableBlockPos scratchScanPos = new BlockPos.MutableBlockPos();

    public RotaryTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROTARY_TABLE_BE.get(), pos, state);
    }

    // ==================== КИНЕТИКА ====================

    @Override public long getMaxTorqueTolerance() { return Long.MAX_VALUE; }
    @Override public long getMaxTorque() { return Long.MAX_VALUE; }
    @Override public double getInertiaContribution() { return 30.0; }
    @Override public long getMaxSpeed() { return 2000L; }
    @Override public long getTorque() { return 0L; }
    @Override public boolean isSource() { return false; }

    @Override
    public long getConsumedTorque() {
        if (!derrickAssembled || !redstoneEnabled || extractingStopped) {
            return 0L;
        }
        return descendingPipe ? 60L : 15L;
    }

    public Direction getKineticPortSide() {
        return getDriveFacing().getClockWise();
    }

    public BlockPos getKineticPortPos() {
        return worldPosition.offset(MultiblockStructureHelper.rotate(LOCAL_KINETIC_PORT_FROM_CTRL, getDriveFacing()));
    }

    @Override
    public Direction[] getPropagationDirections() {
        Direction portSide = getKineticPortSide();
        return new Direction[]{portSide, portSide.getOpposite()};
    }

    @Override
    public List<BlockPos> getPotentialConnections(Level level, BlockPos myPos) {
        return List.of(getKineticPortPos());
    }

    @Override
    public boolean canConnectMechanically(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        if (neighbor instanceof MultiblockPartEntity part) {
            return part.isKineticPort()
                    && part.getControllerPos() != null
                    && part.getControllerPos().equals(myPos);
        }
        return false;
    }

    @Override
    public long getVisualSpeed() {
        return extractingStopped ? 0L : this.speed;
    }

    // ==================== МОДУЛЬ НЕФТЕВЫШКИ (IDrillDriveModule) ====================

    @Override
    public BlockPos getRigPos() {
        return worldPosition;
    }

    @Override
    public Direction getDriveFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(RotaryTableBlock.FACING) ? state.getValue(RotaryTableBlock.FACING) : Direction.NORTH;
    }

    @Override
    public BlockPos getWellCenterPos() {
        return worldPosition.offset(MultiblockStructureHelper.rotate(LOCAL_HOLE_FROM_CTRL, getDriveFacing()));
    }

    @Override
    public int getPlatformY() {
        return worldPosition.getY() - 1;
    }

    @Override
    public WellboreColumn getWellboreColumn() {
        BlockPos hole = getWellCenterPos();
        return new WellboreColumn(hole.getX(), hole.getZ());
    }

    @Override
    public RigModuleRole getModuleRole() {
        return RigModuleRole.DRILL_DRIVE;
    }

    @Override
    public void setAssembly(@Nullable OilRigAssembly assembly) {
        this.assembly = assembly;
    }

    @Override
    @Nullable
    public OilRigAssembly getAssembly() {
        return assembly;
    }

    @Override
    public boolean isModuleValid() {
        return !this.isRemoved() && !detonatingBlowout && level != null;
    }

    @Override
    public long getCurrentRpm() {
        return this.speed;
    }

    @Override
    public boolean isRedstoneEnabled() {
        return redstoneEnabled;
    }

    @Override
    public boolean isDerrickAssembled() {
        return derrickAssembled;
    }

    @Override
    public boolean isPlatformValid(Level level) {
        return checkPlatformBlocks(level);
    }

    public boolean isPlatformValidClient() {
        return platformValid;
    }

    public boolean hasPipeDrumAttachedClient() {
        return hasPipeDrumAttached;
    }

    public boolean isExtractingStopped() {
        return extractingStopped;
    }

    public boolean shouldRenderStage2Hologram() {
        return platformValid && (!derrickAssembled || !hasPipeDrumAttached);
    }

    @Override public int getTowerAnimTick() { return towerAnimTick; }

    @Override
    public void setTowerAnimTick(int tick) {
        if (this.towerAnimTick != tick) {
            this.towerAnimTick = tick;
            setChanged();
            if (tick == 0
                    || tick == OilRigAssembly.TICK_GRAB_PIPE
                    || tick == OilRigAssembly.TICK_INDEX_DRUM
                    || tick == OilRigAssembly.TICK_FEED_ROTOR
                    || tick >= OilRigAssembly.TOWER_CYCLE_TICKS) {
                syncToClient();
            }
        }
    }

    @Override public boolean isTowerAnimPaused() { return towerAnimPaused; }

    @Override
    public void setTowerAnimPaused(boolean paused) {
        if (this.towerAnimPaused != paused) {
            this.towerAnimPaused = paused;
            setChanged();
            syncToClient();
        }
    }

    @Override public boolean isHoldingPipe() { return holdingPipe; }

    @Override
    public void setHoldingPipe(boolean holding) {
        if (this.holdingPipe != holding) {
            this.holdingPipe = holding;
            setChanged();
            syncToClient();
        }
    }

    @Override public int getPipeCountInWell() { return pipeCountInWell; }
    @Override public boolean isDescendingPipe() { return descendingPipe; }

    public float getVisualAnimTick() { return visualAnimTick; }

    public float getVisualDescentBlocks(float partialTick) {
        if (!descendingPipe) return 0.0f;
        boolean moving = !waitingOnRock && isOptimalRpm() && redstoneEnabled && !extractingStopped;
        float extra = moving ? partialTick * 0.05f : 0.0f;
        return Math.min(4.0f, Math.min((float) (descentStepIndex + 1), visualDescentBlocks + extra));
    }

    public boolean isWaitingOnRock() { return waitingOnRock; }
    public float getFrozenDiskAngle() { return frozenDiskAngle; }
    public void setFrozenDiskAngle(float angle) { this.frozenDiskAngle = angle; }

    @Override
    public void beginPipeDescent(ServerLevel level) {
        if (descendingPipe) return;
        descendingPipe = true;
        descentStepIndex = 0;
        descentStepFraction = 0.0f;
        rockDelayTicks = 0;
        waitingOnRock = false;
        detectedPocketPos = null;
        setWellborePipesHidden(level, true);
        setChanged();
        syncToClient();
    }

    @Override
    public boolean tickPipeDescent(ServerLevel level) {
        if (!descendingPipe) return false;
        BlockPos wellCenter = getWellCenterPos();
        int stepY = worldPosition.getY() - 4 * pipeCountInWell - 1 - descentStepIndex;
        BlockPos targetPos = new BlockPos(wellCenter.getX(), stepY, wellCenter.getZ());
        BlockState targetState = level.getBlockState(targetPos);

        boolean skipBlock = (stepY == worldPosition.getY() - 1) || isMudCrossOrHoleBlock(level, targetPos, targetState);
        if (!skipBlock && !targetState.isAir() && !targetState.canBeReplaced()) {
            float hardness = targetState.getDestroySpeed(level, targetPos);
            if (hardness < 0.0f) {
                if (!waitingOnRock) {
                    waitingOnRock = true;
                    setChanged();
                    syncToClient();
                }
                return false;
            }
            if (rockDelayTicks < 10) {
                if (!waitingOnRock) {
                    waitingOnRock = true;
                    setChanged();
                    syncToClient();
                }
                rockDelayTicks++;
                return false;
            }
            level.destroyBlock(targetPos, false);
            if (waitingOnRock) {
                waitingOnRock = false;
                setChanged();
                syncToClient();
            }
        } else if (!skipBlock && !targetState.isAir() && targetState.canBeReplaced()) {
            level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
        }

        waitingOnRock = false;
        descentStepFraction += 0.05f;
        if (descentStepFraction >= 1.0f - 1.0E-5f) {
            descentStepFraction = 0.0f;
            rockDelayTicks = 0;
            scanHorizontalPocket(level, targetPos);
            descentStepIndex++;
            if (descentStepIndex >= 4) {
                finishPipeDescent(level);
                return true;
            }
            syncToClient();
        }
        return false;
    }

    private void finishPipeDescent(ServerLevel level) {
        descendingPipe = false;
        descentStepIndex = 0;
        descentStepFraction = 0.0f;
        waitingOnRock = false;
        pipeCountInWell++;

        BlockPos wellCenter = getWellCenterPos();
        int newBottomY = worldPosition.getY() - 4 * pipeCountInWell;
        BlockPos newPipePos = new BlockPos(wellCenter.getX(), newBottomY, wellCenter.getZ());

        // Освобождаем 4 блока под структуру DrillPipe (если попались заменяемые остатки)
        for (int dy = 0; dy < 4; dy++) {
            BlockPos p = newPipePos.above(dy);
            BlockState st = level.getBlockState(p);
            if (!st.isAir() && (st.canBeReplaced() || !isMudCrossOrHoleBlock(level, p, st))) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        DrillPipeBlock pipeBlock = ModBlocks.DRILL_PIPE.get();
        Direction pipeFacing = getDriveFacing();
        level.setBlock(newPipePos, pipeBlock.defaultBlockState().setValue(DrillPipeBlock.FACING, pipeFacing), 3);
        pipeBlock.getStructureHelper().placeStructure(level, newPipePos, pipeFacing, pipeBlock);

        // Включаем все трубы колонны и запускаем синхронный инерционный выбег
        setWellborePipesHidden(level, false);
        for (int i = 1; i <= pipeCountInWell; i++) {
            BlockPos pipePos = new BlockPos(wellCenter.getX(), worldPosition.getY() - 4 * i, wellCenter.getZ());
            if (level.getBlockEntity(pipePos) instanceof DrillPipeBlockEntity pipeBe) {
                pipeBe.setInWellbore(true);
                pipeBe.setHiddenForColumnDescent(false);
                pipeBe.startInertialCoastFromDrive(worldPosition, this.speed);
            }
        }

        setChanged();
        syncToClient();
    }

    private void setWellborePipesHidden(ServerLevel level, boolean hidden) {
        BlockPos wellCenter = getWellCenterPos();
        for (int i = 1; i <= pipeCountInWell; i++) {
            BlockPos pipePos = new BlockPos(wellCenter.getX(), worldPosition.getY() - 4 * i, wellCenter.getZ());
            if (level.getBlockEntity(pipePos) instanceof DrillPipeBlockEntity pipeBe) {
                pipeBe.setInWellbore(true);
                pipeBe.setHiddenForColumnDescent(hidden);
            }
        }
    }

    private boolean isMudCrossOrHoleBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.MUD_CROSS.get())) return true;
        if (state.is(ModBlocks.MULTIBLOCK_PART.get()) && level.getBlockEntity(pos) instanceof MultiblockPartEntity part) {
            return part.getControllerPos() != null && level.getBlockEntity(part.getControllerPos()) instanceof MudCrossBlockEntity;
        }
        return false;
    }

    private void scanHorizontalPocket(ServerLevel level, BlockPos center) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adj = center.relative(dir);
            if (OilPocketHelper.isReservoirBlock(level.getBlockState(adj))) {
                detectedPocketPos = adj.immutable();
                return;
            }
        }
    }

    @Override
    @Nullable
    public BlockPos getDetectedPocketPos() {
        return detectedPocketPos;
    }

    @Override
    public void clearDetectedPocketPos() {
        this.detectedPocketPos = null;
    }

    @Override
    public void stopRotaryForExtraction(ServerLevel level) {
        if (!extractingStopped) {
            extractingStopped = true;
            setChanged();
            syncToClient();
        }
    }

    @Override
    public void resumeDrillingFromExtraction(ServerLevel level) {
        if (extractingStopped) {
            extractingStopped = false;
            setChanged();
            syncToClient();
        }
    }

    @Override
    public int purgeWellborePipesForBlowout(ServerLevel level) {
        detonatingBlowout = true;
        BlockPos wellCenter = getWellCenterPos();
        for (int i = 1; i <= pipeCountInWell; i++) {
            BlockPos pipePos = new BlockPos(wellCenter.getX(), worldPosition.getY() - 4 * i, wellCenter.getZ());
            BlockState st = level.getBlockState(pipePos);
            if (st.getBlock() instanceof DrillPipeBlock pipeBlock) {
                pipeBlock.getStructureHelper().destroyStructure(level, pipePos, st.getValue(DrillPipeBlock.FACING));
                level.setBlock(pipePos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        int total = pipeCountInWell + (descendingPipe ? 1 : 0);
        pipeCountInWell = 0;
        descendingPipe = false;
        descentStepIndex = 0;
        descentStepFraction = 0.0f;
        waitingOnRock = false;
        setChanged();
        return total;
    }

    @Override
    @Nullable
    public BlockPos findAdjacentPocketBlock(ServerLevel level) {
        if (pipeCountInWell <= 0) return null;
        BlockPos wellCenter = getWellCenterPos();
        int lowestY = worldPosition.getY() - 4 * pipeCountInWell;
        for (int dy = 0; dy < 4; dy++) {
            BlockPos checkCenter = new BlockPos(wellCenter.getX(), lowestY + dy, wellCenter.getZ());
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos adj = checkCenter.relative(dir);
                if (OilPocketHelper.isReservoirBlock(level.getBlockState(adj))) {
                    return adj;
                }
            }
        }
        return null;
    }

    // ==================== ЖИЗНЕННЫЙ ЦИКЛ И ТИК ====================

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel && !detonatingBlowout) {
            OilRigManager.get(serverLevel).onModuleLoaded(serverLevel, this);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            OilRigManager.get(serverLevel).onModuleRemoved(serverLevel, worldPosition);
        }
        super.setRemoved();
    }

    public void clientTick() {
        if (derrickAssembled && !towerAnimPaused && isOptimalRpm() && redstoneEnabled) {
            visualAnimTick = Math.min(OilRigAssembly.TOWER_CYCLE_TICKS, visualAnimTick + 1.0f);
            if (visualAnimTick >= OilRigAssembly.TOWER_CYCLE_TICKS && !descendingPipe) {
                visualAnimTick = 0.0f;
            }
        } else {
            visualAnimTick = towerAnimTick;
        }
        if (descendingPipe) {
            if (!waitingOnRock && isOptimalRpm() && redstoneEnabled && !extractingStopped) {
                visualDescentBlocks = Math.min((float) (descentStepIndex + 1), visualDescentBlocks + 0.05f);
            } else if (waitingOnRock) {
                visualDescentBlocks = descentStepIndex;
            }
        } else {
            visualDescentBlocks = 0.0f;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RotaryTableBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel) || be.detonatingBlowout) return;

        be.pollRedstoneSignal(serverLevel);

        if (--be.structureScanCooldown <= 0) {
            be.structureScanCooldown = 10;
            be.checkPlatformAndDerrickAssembly(serverLevel);
        }

        OilRigAssembly asm = be.assembly;
        if (asm != null) {
            asm.serverTick(serverLevel);
        }

        if (++be.syncCounter >= 10) {
            be.syncCounter = 0;
            if (!be.towerAnimPaused || be.descendingPipe) {
                be.syncToClient();
            }
        }
    }

    private void pollRedstoneSignal(ServerLevel level) {
        Direction facing = getDriveFacing();
        boolean powered = false;
        for (int[] off : STRUCTURE_OFFSETS_3X3) {
            BlockPos rot = MultiblockStructureHelper.rotate(new BlockPos(off[0], 0, off[1]), facing);
            scratchScanPos.set(worldPosition.getX() + rot.getX(), worldPosition.getY(), worldPosition.getZ() + rot.getZ());
            if (level.hasNeighborSignal(scratchScanPos)) {
                powered = true;
                break;
            }
        }
        if (powered != this.redstoneEnabled) {
            this.redstoneEnabled = powered;
            setChanged();
            syncToClient();
            var net = KineticNetworkManager.get(level).getNetworkFor(worldPosition);
            if (net != null) net.requestRecalculation();
        }
    }

    // ==================== ПРОВЕРКА ПЛАТФОРМЫ И СБОРКА ВЫШКИ ====================

    private boolean checkPlatformBlocks(Level level) {
        BlockPos wellCenter = getWellCenterPos();
        int platformY = getPlatformY();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                scratchScanPos.set(wellCenter.getX() + dx, platformY, wellCenter.getZ() + dz);
                boolean isSteel = level.getBlockState(scratchScanPos).is(ModBlocks.DECO_STEEL.get());
                if (dx == 0 && dz == 0) {
                    if (isSteel) return false;
                } else if (!isSteel) {
                    return false;
                }
            }
        }
        return true;
    }

    private void checkPlatformAndDerrickAssembly(ServerLevel level) {
        boolean platOk = checkPlatformBlocks(level);
        boolean drumOk = assembly != null && assembly.getFeeder() != null && assembly.getFeeder().isModuleValid();

        boolean changed = false;
        if (platOk != this.platformValid || drumOk != this.hasPipeDrumAttached) {
            this.platformValid = platOk;
            this.hasPipeDrumAttached = drumOk;
            changed = true;
        }

        if (!derrickAssembled && platOk) {
            if (tryAssembleDerrickFromWorld(level)) {
                changed = true;
            }
        }

        if (changed) {
            setChanged();
            syncToClient();
        }
    }

    private boolean tryAssembleDerrickFromWorld(ServerLevel level) {
        BlockPos wellCenter = getWellCenterPos();
        int platformY = getPlatformY();
        int ringY = platformY + RING_HEIGHT_FROM_PLATFORM;

        // 1. Проверяем верхнее полое кольцо 5×5 (16 блоков DECO_STEEL) на высоте Y_platform + 20
        List<BlockPos> ringBlocks = new ArrayList<>(16);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
                BlockPos ringPos = new BlockPos(wellCenter.getX() + dx, ringY, wellCenter.getZ() + dz);
                if (!level.getBlockState(ringPos).is(ModBlocks.DECO_STEEL.get())) {
                    return false;
                }
                ringBlocks.add(ringPos);
            }
        }

        // 2. Ищем все 4 наклонные P2P-балки между углами 9×9 платформы и углами 5×5 кольца
        record MatchedBeam(Vec3 start, Vec3 end, int cost) {}
        List<MatchedBeam> matchedBeams = new ArrayList<>(4);
        int[] signs = {-1, 1};
        for (int sx : signs) {
            for (int sz : signs) {
                Vec3 cornerPlat = new Vec3(wellCenter.getX() + sx * 4 + 0.5, platformY + 0.5, wellCenter.getZ() + sz * 4 + 0.5);
                Vec3 cornerRing = new Vec3(wellCenter.getX() + sx * 2 + 0.5, ringY + 0.5, wellCenter.getZ() + sz * 2 + 0.5);
                BeamCollisionBlockEntity.BeamData found = findMatchingBeam(level, cornerPlat, cornerRing);
                if (found == null) {
                    return false;
                }
                int beamCost = (int) Math.ceil(found.startPos.distanceTo(found.endPos));
                matchedBeams.add(new MatchedBeam(found.startPos, found.endPos, beamCost));
            }
        }

        // 3. Полное совпадение! Сохраняем точное количество ресурсов
        this.savedDecoSteelCount = 16;
        int totalBeamCost = 0;
        for (MatchedBeam beam : matchedBeams) {
            totalBeamCost += beam.cost;
        }
        this.savedBeamBlockCount = totalBeamCost;

        // 4. Сначала чисто удаляем данные P2P-балок БЕЗ срабатывания дропа при снятии якоря-кольца
        for (MatchedBeam beam : matchedBeams) {
            clearP2PBeamWithoutDrops(level, beam.start, beam.end);
        }

        // 5. Удаляем из мира 16 блоков верхнего кольца 5×5 (платформа 9×9 остаётся)
        for (BlockPos rp : ringBlocks) {
            level.setBlock(rp, Blocks.AIR.defaultBlockState(), 3);
        }

        // 6. Создаём неразрушимую коллизию каркаса по форме модели fracking_tower.gltf
        this.derrickCollisionPositions.clear();
        Set<BlockPos> recorded = new HashSet<>();

        // Верхняя рама 5×5 толщиной 1 блок
        for (BlockPos rp : ringBlocks) {
            if (level.getBlockState(rp).canBeReplaced() || level.getBlockState(rp).is(ModBlocks.DERRICK_COLLISION.get())) {
                if (!level.getBlockState(rp).is(ModBlocks.DERRICK_COLLISION.get())) {
                    level.setBlock(rp, ModBlocks.DERRICK_COLLISION.get().defaultBlockState(), 3);
                }
                if (level.getBlockEntity(rp) instanceof DerrickCollisionBlockEntity col) {
                    col.setControllerPos(worldPosition);
                    col.setFullRingBlock(true);
                }
                if (recorded.add(rp)) {
                    derrickCollisionPositions.add(rp.immutable());
                }
            }
        }

        // 4 наклонные ноги (Liang–Barsky) от углов 9×9 над платформой до углов верхнего кольца 5×5
        for (int sx : signs) {
            for (int sz : signs) {
                Vec3 legStart = new Vec3(wellCenter.getX() + sx * 4 + 0.5, worldPosition.getY(), wellCenter.getZ() + sz * 4 + 0.5);
                Vec3 legEnd = new Vec3(wellCenter.getX() + sx * 2 + 0.5, ringY + 0.5, wellCenter.getZ() + sz * 2 + 0.5);
                placeDerrickLegCollision(level, legStart, legEnd, recorded);
            }
        }

        this.derrickAssembled = true;
        return true;
    }

    @Nullable
    private BeamCollisionBlockEntity.BeamData findMatchingBeam(ServerLevel level, Vec3 a, Vec3 b) {
        double dist = a.distanceTo(b);
        Vec3 dir = b.subtract(a).normalize();
        int steps = (int) Math.ceil(dist / 0.25);

        for (int i = 1; i < steps; i++) {
            Vec3 p = a.add(dir.scale(i * 0.25));
            BlockPos bp = BlockPos.containing(p);
            BeamCollisionBlockEntity.BeamData match = checkBeamAtPos(level, bp, a, b);
            if (match != null) return match;
            for (Direction side : Direction.values()) {
                match = checkBeamAtPos(level, bp.relative(side), a, b);
                if (match != null) return match;
            }
        }
        return null;
    }

    @Nullable
    private static BeamCollisionBlockEntity.BeamData checkBeamAtPos(ServerLevel level, BlockPos pos, Vec3 a, Vec3 b) {
        if (!(level.getBlockEntity(pos) instanceof BeamCollisionBlockEntity be)) return null;
        for (BeamCollisionBlockEntity.BeamData data : be.getBeams()) {
            boolean forward = data.startPos.distanceToSqr(a) < 0.05 && data.endPos.distanceToSqr(b) < 0.05;
            boolean reverse = data.startPos.distanceToSqr(b) < 0.05 && data.endPos.distanceToSqr(a) < 0.05;
            if (forward || reverse) {
                return data;
            }
        }
        return null;
    }

    private static void clearP2PBeamWithoutDrops(ServerLevel level, Vec3 start, Vec3 end) {
        double dist = start.distanceTo(end);
        Vec3 dir = end.subtract(start).normalize();
        int steps = (int) Math.ceil(dist / 0.2);

        Set<BlockPos> candidates = new HashSet<>();
        for (int i = 0; i <= steps; i++) {
            double t = Math.min(i * 0.2, dist);
            BlockPos bp = BlockPos.containing(start.add(dir.scale(t)));
            candidates.add(bp);
            for (Direction d : Direction.values()) {
                candidates.add(bp.relative(d));
            }
        }

        for (BlockPos candidate : candidates) {
            if (level.getBlockEntity(candidate) instanceof BeamCollisionBlockEntity col) {
                col.removeBeamData(start, end);
            }
        }
    }

    private void placeDerrickLegCollision(ServerLevel level, Vec3 legStart, Vec3 legEnd, Set<BlockPos> recorded) {
        double dist = legStart.distanceTo(legEnd);
        Vec3 dir = legEnd.subtract(legStart).normalize();
        int steps = (int) Math.ceil(dist / 0.15);

        Set<BlockPos> legBlocks = new HashSet<>();
        for (int i = 0; i <= steps; i++) {
            double d = Math.min(i * 0.15, dist);
            Vec3 pt = legStart.add(dir.scale(d));
            addCornerSampledPositions(legBlocks, pt.x, pt.y, pt.z);
        }

        for (BlockPos bp : legBlocks) {
            if (bp.getY() < worldPosition.getY()) continue;
            BlockState existing = level.getBlockState(bp);
            if (!existing.canBeReplaced() && !existing.is(ModBlocks.DERRICK_COLLISION.get())) {
                continue;
            }
            if (!existing.is(ModBlocks.DERRICK_COLLISION.get())) {
                level.setBlock(bp, ModBlocks.DERRICK_COLLISION.get().defaultBlockState(), 3);
            }
            if (level.getBlockEntity(bp) instanceof DerrickCollisionBlockEntity col) {
                col.setControllerPos(worldPosition);
                col.addLegSegment(legStart, legEnd);
            }
            if (recorded.add(bp)) {
                derrickCollisionPositions.add(bp.immutable());
            }
        }
    }

    private static void addCornerSampledPositions(Set<BlockPos> out, double x, double y, double z) {
        final double r = 0.22;
        out.add(BlockPos.containing(x, y, z));
        out.add(BlockPos.containing(x - r, y, z - r));
        out.add(BlockPos.containing(x + r, y, z - r));
        out.add(BlockPos.containing(x - r, y, z + r));
        out.add(BlockPos.containing(x + r, y, z + r));
    }

    public void markBrokenInCreative() {
        this.brokenInCreative = true;
    }

    public void onControllerRemoved(ServerLevel level) {
        if (!detonatingBlowout) {
            setWellborePipesHidden(level, false);
        }
        for (BlockPos colPos : derrickCollisionPositions) {
            if (level.getBlockState(colPos).is(ModBlocks.DERRICK_COLLISION.get())) {
                level.setBlock(colPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        derrickCollisionPositions.clear();

        if (derrickAssembled && !brokenInCreative && !detonatingBlowout) {
            BlockPos dropPos = getWellCenterPos();
            dropChunked(level, dropPos, ModBlocks.DECO_STEEL.get(), savedDecoSteelCount);
            dropChunked(level, dropPos, ModBlocks.BEAM_BLOCK.get(), savedBeamBlockCount);
        }

        derrickAssembled = false;
        savedDecoSteelCount = 0;
        savedBeamBlockCount = 0;
    }

    private static void dropChunked(ServerLevel level, BlockPos pos, Block block, int count) {
        int remaining = count;
        int maxStack = block.asItem().getDefaultMaxStackSize();
        while (remaining > 0) {
            int take = Math.min(remaining, maxStack);
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(block, take));
            remaining -= take;
        }
    }

    // ==================== NBT И СИНХРОНИЗАЦИЯ ====================

    @Override
    protected void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("RedstoneEnabled", redstoneEnabled);
        tag.putBoolean("DerrickAssembled", derrickAssembled);
        tag.putBoolean("PlatformValid", platformValid);
        tag.putBoolean("HasPipeDrum", hasPipeDrumAttached);
        tag.putBoolean("ExtractingStopped", extractingStopped);
        tag.putInt("SavedDecoSteel", savedDecoSteelCount);
        tag.putInt("SavedBeamBlocks", savedBeamBlockCount);
        tag.putInt("TowerAnimTick", towerAnimTick);
        tag.putBoolean("TowerAnimPaused", towerAnimPaused);
        tag.putBoolean("HoldingPipe", holdingPipe);
        tag.putInt("PipeCountInWell", pipeCountInWell);
        tag.putBoolean("DescendingPipe", descendingPipe);
        tag.putInt("DescentStepIdx", descentStepIndex);
        tag.putFloat("DescentStepFrac", descentStepFraction);
        tag.putInt("RockDelay", rockDelayTicks);
        tag.putBoolean("WaitingOnRock", waitingOnRock);
        if (detectedPocketPos != null) {
            tag.putLong("DetectedPocket", detectedPocketPos.asLong());
        }
        long[] packedCols = new long[derrickCollisionPositions.size()];
        for (int i = 0; i < derrickCollisionPositions.size(); i++) {
            packedCols[i] = derrickCollisionPositions.get(i).asLong();
        }
        tag.put("DerrickCols", new LongArrayTag(packedCols));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        redstoneEnabled = tag.getBoolean("RedstoneEnabled");
        derrickAssembled = tag.getBoolean("DerrickAssembled");
        platformValid = tag.getBoolean("PlatformValid");
        hasPipeDrumAttached = tag.getBoolean("HasPipeDrum");
        extractingStopped = tag.getBoolean("ExtractingStopped");
        savedDecoSteelCount = tag.getInt("SavedDecoSteel");
        savedBeamBlockCount = tag.getInt("SavedBeamBlocks");
        towerAnimTick = tag.getInt("TowerAnimTick");
        towerAnimPaused = !tag.contains("TowerAnimPaused") || tag.getBoolean("TowerAnimPaused");
        holdingPipe = tag.getBoolean("HoldingPipe");
        pipeCountInWell = tag.getInt("PipeCountInWell");
        descendingPipe = tag.getBoolean("DescendingPipe");
        descentStepIndex = tag.getInt("DescentStepIdx");
        descentStepFraction = tag.getFloat("DescentStepFrac");
        rockDelayTicks = tag.getInt("RockDelay");
        waitingOnRock = tag.getBoolean("WaitingOnRock");
        detectedPocketPos = tag.contains("DetectedPocket") ? BlockPos.of(tag.getLong("DetectedPocket")) : null;

        derrickCollisionPositions.clear();
        if (tag.contains("DerrickCols")) {
            for (long packed : tag.getLongArray("DerrickCols")) {
                derrickCollisionPositions.add(BlockPos.of(packed));
            }
        }

        if (level != null && level.isClientSide && FMLEnvironment.dist.isClient()) {
            if (Math.abs(visualAnimTick - towerAnimTick) > 3.0f || towerAnimPaused) {
                visualAnimTick = towerAnimTick;
            }
            float targetDescent = descendingPipe ? (descentStepIndex + descentStepFraction) : 0.0f;
            if (!descendingPipe || waitingOnRock || Math.abs(visualDescentBlocks - targetDescent) > 0.35f) {
                visualDescentBlocks = targetDescent;
            }
            boolean currentHologram = shouldRenderStage2Hologram();
            if (derrickAssembled != lastVisualAssembled || currentHologram != lastVisualHologram) {
                lastVisualAssembled = derrickAssembled;
                lastVisualHologram = currentHologram;
                com.trd.client.render.FlywheelFallbackHelper.queueBlockEntityUpdate(this);
            }
        }
    }
}
