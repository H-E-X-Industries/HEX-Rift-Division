package com.trd.multiblock.industrial.drilling;

import com.trd.api.industiral.energy.EnergyNetworkManager;
import com.trd.api.industiral.energy.IEnergyReceiver;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.multiblock.industrial.drilling.oilrig.core.*;
import com.trd.multiblock.system.MultiblockStructureHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BlockEntity револьверного барабана на 6 буровых труб (PipeDrum).
 * Питается JE строго с внешней/северной грани контроллера '@', тратит 1000 JE на шаг 60°.
 * Расчёт мировых AABB 6 слотов напрямую связан с костями slot_pipe_1..6 и углом карусели.
 */
public class PipeDrumBlockEntity extends BlockEntity implements IPipeFeederModule, IEnergyReceiver {

    public static final int SLOT_COUNT = 6;
    public static final long MAX_ENERGY = 10000L;
    public static final long RECEIVE_SPEED = 1000L;
    public static final long INDEX_COST_JE = 1000L;
    public static final int INDEX_ANIM_TICKS = 40; // 2.0 секунды (60° в drum_index)

    private static final double CAROUSEL_PIVOT_Z = 0.9;
    private static final double CAROUSEL_RADIUS = 1.0663625;
    private static final double PIPE_Y_MIN = 1.397;
    private static final double PIPE_Y_MAX = 5.397;
    private static final double PIPE_HALF_WIDTH = 0.46;

    /** Реестр активных барабанов на клиенте/сервере для 3D-рейкаста 6 навесных слотов. */
    public static final Set<PipeDrumBlockEntity> CLIENT_DRUMS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static final Set<PipeDrumBlockEntity> SERVER_DRUMS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final boolean[] boneHasPipe = new boolean[SLOT_COUNT];
    private long energyStored = 0L;
    private int carouselStep = 0; // 0..5, каждый шаг = +60°
    private boolean pendingIndex = false;
    private boolean indexingAnimActive = false;
    private int indexAnimTicks = 0;

    private int clientIndexTicks = 0;
    private boolean brokenInCreative = false;

    @Nullable
    private transient OilRigAssembly assembly;

    public PipeDrumBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIPE_DRUM_BE.get(), pos, state);
    }

    public static boolean isDrillPipeItem(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModBlocks.DRILL_PIPE.asItem());
    }

    public static Item getDrillPipeItem() {
        return ModBlocks.DRILL_PIPE.asItem();
    }

    // ==================== МОДУЛЬ НЕФТЕВЫШКИ (IPipeFeederModule) ====================

    @Override
    public BlockPos getRigPos() {
        return worldPosition;
    }

    @Override
    public Direction getFeederFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(PipeDrumBlock.FACING) ? state.getValue(PipeDrumBlock.FACING) : Direction.NORTH;
    }

    @Override
    public WellboreColumn getWellboreColumn() {
        // Податчик не является осевым; возвращает координаты оси вала внутри своей зоны
        BlockPos wellApprox = worldPosition.offset(MultiblockStructureHelper.rotate(new BlockPos(0, 0, 3), getFeederFacing()));
        return new WellboreColumn(wellApprox.getX(), wellApprox.getZ());
    }

    @Override
    public RigModuleRole getModuleRole() {
        return RigModuleRole.PIPE_FEEDER;
    }

    @Override
    public boolean isAxialModule() {
        return false;
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
        return !this.isRemoved() && level != null;
    }

    /** Индекс кости (0..5), которая при текущем шаге карусели стоит в позиции выдачи (угол 0°, у стола). */
    public int getReadyBoneIndex() {
        return (SLOT_COUNT - (carouselStep % SLOT_COUNT)) % SLOT_COUNT;
    }

    @Override
    public boolean hasReadyPipe() {
        return !indexingAnimActive && !pendingIndex && boneHasPipe[getReadyBoneIndex()];
    }

    @Override
    public boolean consumeReadyPipe() {
        if (!hasReadyPipe()) return false;
        boneHasPipe[getReadyBoneIndex()] = false;
        setChanged();
        syncToClient();
        return true;
    }

    @Override
    public boolean isIndexingOrPending() {
        return indexingAnimActive || pendingIndex;
    }

    @Override
    public void requestIndexNextSlot() {
        if (indexingAnimActive || pendingIndex) return;
        pendingIndex = true;
        setChanged();
        syncToClient();
    }

    // ==================== СЛОТЫ И КАРУСЕЛЬ ====================

    public boolean hasPipeInBone(int boneIndex) {
        return boneIndex >= 0 && boneIndex < SLOT_COUNT && boneHasPipe[boneIndex];
    }

    public int getLoadedPipeCount() {
        int c = 0;
        for (boolean b : boneHasPipe) {
            if (b) c++;
        }
        return c;
    }

    public boolean insertPipeIntoSlot(int boneIndex) {
        if (boneIndex < 0 || boneIndex >= SLOT_COUNT || boneHasPipe[boneIndex]) {
            return false;
        }
        boneHasPipe[boneIndex] = true;
        setChanged();
        syncToClient();
        return true;
    }

    public float getCarouselAngleDeg(float partialTick) {
        float baseDeg = carouselStep * 60.0f;
        if (!indexingAnimActive) {
            return baseDeg;
        }
        float progress = Mth.clamp((clientIndexTicks + partialTick) / (float) INDEX_ANIM_TICKS, 0.0f, 1.0f);
        return baseDeg + progress * 60.0f;
    }

    // ==================== ДИНАМИЧЕСКАЯ ГЕОМЕТРИЯ 6 КОСТЕЙ И РЕЙКАСТ ====================

    /**
     * Вычисляет мировой центр трубы для кости {@code boneIndex} (0..5)
     * с учётом текущего угла карусели и ориентации блока.
     */
    public Vec3 computeSlotWorldCenter(int boneIndex, float partialTick) {
        double angleDeg = boneIndex * 60.0 + getCarouselAngleDeg(partialTick);
        double theta = Math.toRadians(angleDeg);
        double gx = CAROUSEL_RADIUS * Math.sin(theta);
        double gz = CAROUSEL_PIVOT_Z + CAROUSEL_RADIUS * Math.cos(theta);

        double yawRad = Math.toRadians(180.0f - getFeederFacing().toYRot());
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);

        double wx = worldPosition.getX() + 0.5 + gx * cos + gz * sin;
        double wz = worldPosition.getZ() + 0.5 - gx * sin + gz * cos;
        double wy = worldPosition.getY() + (PIPE_Y_MIN + PIPE_Y_MAX) * 0.5;
        return new Vec3(wx, wy, wz);
    }

    public AABB computeSlotWorldAabb(int boneIndex, float partialTick) {
        Vec3 c = computeSlotWorldCenter(boneIndex, partialTick);
        return new AABB(
                c.x - PIPE_HALF_WIDTH, worldPosition.getY() + PIPE_Y_MIN, c.z - PIPE_HALF_WIDTH,
                c.x + PIPE_HALF_WIDTH, worldPosition.getY() + PIPE_Y_MAX, c.z + PIPE_HALF_WIDTH
        );
    }

    /** Возвращает индекс ближайшего пустого слота на луче взгляда или -1. */
    public int raycastEmptySlot(Vec3 eyePos, Vec3 lookEnd) {
        return raycastEmptySlot(eyePos, lookEnd, 0.0f);
    }

    public int raycastEmptySlot(Vec3 eyePos, Vec3 lookEnd, float partialTick) {
        int bestSlot = -1;
        double bestDistSq = Double.MAX_VALUE;
        for (int b = 0; b < SLOT_COUNT; b++) {
            if (boneHasPipe[b]) continue;
            AABB box = computeSlotWorldAabb(b, partialTick);
            Optional<Vec3> clip = box.clip(eyePos, lookEnd);
            if (clip.isPresent()) {
                double dSq = clip.get().distanceToSqr(eyePos);
                if (dSq < bestDistSq) {
                    bestDistSq = dSq;
                    bestSlot = b;
                }
            }
        }
        return bestSlot;
    }

    public int findNearestEmptySlot(Vec3 worldHitPos) {
        int bestSlot = -1;
        double bestDistSq = 2.25 * 2.25;
        for (int b = 0; b < SLOT_COUNT; b++) {
            if (boneHasPipe[b]) continue;
            Vec3 center = computeSlotWorldCenter(b, 0.0f);
            double dSq = center.distanceToSqr(worldHitPos);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                bestSlot = b;
            }
        }
        return bestSlot;
    }

    // ==================== ЭНЕРГИЯ (JE — ТОЛЬКО ВНЕШНЯЯ/СЕВЕРНАЯ СТОРОНА) ====================

    @Override
    public boolean canConnectEnergy(@Nullable Direction side) {
        return side == getFeederFacing();
    }

    @Override public long getEnergyStored() { return energyStored; }
    @Override public long getMaxEnergyStored() { return MAX_ENERGY; }

    @Override
    public void setEnergyStored(long energy) {
        long clamped = Mth.clamp(energy, 0L, MAX_ENERGY);
        if (this.energyStored != clamped) {
            this.energyStored = clamped;
            setChanged();
        }
    }

    @Override public long getReceiveSpeed() { return RECEIVE_SPEED; }
    @Override public Priority getPriority() { return Priority.NORMAL; }
    @Override public boolean canReceive() { return energyStored < MAX_ENERGY; }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        if (maxReceive <= 0L) return 0L;
        long space = MAX_ENERGY - energyStored;
        long accepted = Math.min(space, Math.min(RECEIVE_SPEED, maxReceive));
        if (!simulate && accepted > 0L) {
            energyStored += accepted;
            setChanged();
            syncToClient();
        }
        return accepted;
    }

    // ==================== ЖИЗНЕННЫЙ ЦИКЛ И ТИКИ ====================

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            SERVER_DRUMS.add(this);
            OilRigManager.get(serverLevel).onModuleLoaded(serverLevel, this);
            EnergyNetworkManager.get(serverLevel).ensureNodeConnected(worldPosition);
        } else if (level != null && level.isClientSide) {
            CLIENT_DRUMS.add(this);
        }
    }

    @Override
    public void setRemoved() {
        CLIENT_DRUMS.remove(this);
        SERVER_DRUMS.remove(this);
        if (level instanceof ServerLevel serverLevel) {
            OilRigManager.get(serverLevel).onModuleRemoved(serverLevel, worldPosition);
            EnergyNetworkManager.get(serverLevel).removeNode(worldPosition);
        }
        super.setRemoved();
    }

    public void clientTick() {
        if (indexingAnimActive) {
            if (clientIndexTicks < INDEX_ANIM_TICKS) {
                clientIndexTicks++;
            }
        } else {
            clientIndexTicks = 0;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PipeDrumBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        if (be.pendingIndex && !be.indexingAnimActive && be.energyStored >= INDEX_COST_JE) {
            be.energyStored -= INDEX_COST_JE;
            be.pendingIndex = false;
            be.indexingAnimActive = true;
            be.indexAnimTicks = 0;
            be.setChanged();
            be.syncToClient();
        }

        if (be.indexingAnimActive) {
            be.indexAnimTicks++;
            if (be.indexAnimTicks >= INDEX_ANIM_TICKS) {
                be.indexingAnimActive = false;
                be.indexAnimTicks = 0;
                be.carouselStep = (be.carouselStep + 1) % SLOT_COUNT;
                be.setChanged();
                be.syncToClient();
            }
        }
    }

    public void markBrokenInCreative() {
        this.brokenInCreative = true;
    }

    public boolean isBrokenInCreative() {
        return brokenInCreative;
    }

    // ==================== NBT И СИНХРОНИЗАЦИЯ ====================

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        byte mask = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (boneHasPipe[i]) {
                mask |= (1 << i);
            }
        }
        tag.putByte("PipeSlots", mask);
        tag.putLong("EnergyJE", energyStored);
        tag.putInt("CarouselStep", carouselStep);
        tag.putBoolean("PendingIndex", pendingIndex);
        tag.putBoolean("Indexing", indexingAnimActive);
        tag.putInt("IndexTicks", indexAnimTicks);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        byte mask = tag.getByte("PipeSlots");
        for (int i = 0; i < SLOT_COUNT; i++) {
            boneHasPipe[i] = (mask & (1 << i)) != 0;
        }
        energyStored = Mth.clamp(tag.getLong("EnergyJE"), 0L, MAX_ENERGY);
        carouselStep = Math.floorMod(tag.getInt("CarouselStep"), SLOT_COUNT);
        pendingIndex = tag.getBoolean("PendingIndex");
        indexingAnimActive = tag.getBoolean("Indexing");
        indexAnimTicks = tag.getInt("IndexTicks");
        clientIndexTicks = indexAnimTicks;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
