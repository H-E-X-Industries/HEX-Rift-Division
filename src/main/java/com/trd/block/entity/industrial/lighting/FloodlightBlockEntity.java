package com.trd.block.entity.industrial.lighting;

import com.trd.api.energy.EnergyNetworkManager;
import com.trd.api.energy.IEnergyConnector;
import com.trd.api.energy.IEnergyReceiver;
import com.trd.block.basic.industrial.lighting.FloodlightBlock;
import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Промисловий поворотний прожектор (Floodlight).
 * Споживання: 40 од./такт.
 * Буфер: 40 од. (миттєва реакція на розрив мережі).
 * Двоосьовий шарнір: плавний поворот 360° Yaw та нахил Pitch від -75° до +90°.
 * Підтримує 6-стороннє кріплення (підлога, стеля, 4 стіни).
 */
public class FloodlightBlockEntity extends BlockEntity implements IEnergyReceiver, IEnergyConnector {

    public static final long CONSUMPTION = 40L;
    public static final long MAX_ENERGY = 40L;
    public static final long RECEIVE_SPEED = 40L;
    public static final int MAX_BEAM_DISTANCE = 50;
    public static final int MAX_DISTANCE = MAX_BEAM_DISTANCE;

    public static final float MAX_YAW_SPEED = 6.0f;   // градусів за тік
    public static final float MAX_PITCH_SPEED = 5.0f; // градусів за тік

    private long energyStored = 0L;

    private final Set<BlockPos> activeLightPositions = new HashSet<>();
    private int currentLightLevel = 0;
    private float beamDistance = 50.0f;
    private int warmupTicks = 0;
    private int cooldownTicks = 0;
    private boolean raycastDirty = true;
    private int raycastCheckCounter = 0;

    // Плавна орієнтація прожектора (градуси)
    public float yaw = 0.0f;
    public float prevYaw = 0.0f;
    public float targetYaw = 0.0f;

    public float pitch = 0.0f;
    public float prevPitch = 0.0f;
    public float targetPitch = 0.0f;

    private float lastRaycastYaw = -999.0f;
    private float lastRaycastPitch = -999.0f;

    public FloodlightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLOODLIGHT_BE.get(), pos, state);
    }

    public void markRaycastDirty() {
        this.raycastDirty = true;
    }

    public float getBeamDistance() {
        return beamDistance;
    }

    public int getCurrentLightLevel() {
        return currentLightLevel;
    }

    public int getEffectiveLightLevel() {
        if (currentLightLevel > 0) return currentLightLevel;
        BlockState state = getBlockState();
        return (state.hasProperty(FloodlightBlock.LIT) && state.getValue(FloodlightBlock.LIT)) ? 15 : 0;
    }

    // ==================== ORIENTATION CONTROL ====================

    public void setTargetOrientation(float targetYaw, float targetPitch) {
        this.targetYaw = Mth.wrapDegrees(targetYaw);
        this.targetPitch = Mth.clamp(targetPitch, -75.0f, 90.0f);
        this.raycastDirty = true;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void tickRotation() {
        this.prevYaw = this.yaw;
        this.prevPitch = this.pitch;

        // Плавне доведення yaw по найкоротшій дузі
        float yawDiff = Mth.wrapDegrees(this.targetYaw - this.yaw);
        if (Math.abs(yawDiff) > 0.01f) {
            float step = Mth.clamp(yawDiff, -MAX_YAW_SPEED, MAX_YAW_SPEED);
            this.yaw = Mth.wrapDegrees(this.yaw + step);
        } else {
            this.yaw = this.targetYaw;
        }

        // Плавне доведення pitch
        float pitchDiff = this.targetPitch - this.pitch;
        if (Math.abs(pitchDiff) > 0.01f) {
            float step = Mth.clamp(pitchDiff, -MAX_PITCH_SPEED, MAX_PITCH_SPEED);
            this.pitch = Mth.clamp(this.pitch + step, -75.0f, 90.0f);
        } else {
            this.pitch = this.targetPitch;
        }
    }

    public void clientTick(Level level, BlockPos pos, BlockState state) {
        tickRotation();
    }

    // ==================== BASE PORT CONNECTION ====================

    /**
     * Повертає сторону підключення проводу — СУВОРО з боку монтажної підошви (основи) прожектора.
     */
    public Direction getConnectionSide() {
        BlockState state = getBlockState();
        if (state.hasProperty(FloodlightBlock.MOUNT_FACE)) {
            return state.getValue(FloodlightBlock.MOUNT_FACE).getOpposite();
        }
        return Direction.DOWN;
    }

    public Direction getActiveConnectionSide() {
        return getConnectionSide();
    }

    // ==================== TICKER & THERMAL INERTIA ====================

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        tickRotation();

        boolean hasPower = this.energyStored >= CONSUMPTION;
        this.energyStored = 0L;
        setChanged();

        int prevLightLevel = this.currentLightLevel;
        if (hasPower) {
            this.cooldownTicks = 0;
            this.warmupTicks++;
            if (this.warmupTicks == 1) {
                this.currentLightLevel = 5;
            } else if (this.warmupTicks == 2) {
                this.currentLightLevel = 10;
            } else {
                this.currentLightLevel = 15;
            }
        } else {
            this.warmupTicks = 0;
            if (this.currentLightLevel > 0) {
                this.cooldownTicks++;
                if (this.cooldownTicks % 2 == 0) {
                    this.currentLightLevel = Math.max(0, this.currentLightLevel - 5);
                }
                if (this.cooldownTicks >= 6) {
                    this.currentLightLevel = 0;
                }
            } else {
                this.cooldownTicks = 0;
            }
        }

        boolean lightLevelChanged = (prevLightLevel != this.currentLightLevel);
        if (lightLevelChanged) {
            this.raycastDirty = true;
        }

        boolean shouldBeLit = this.currentLightLevel > 0;
        boolean wasLit = state.getValue(FloodlightBlock.LIT);

        if (wasLit != shouldBeLit) {
            level.setBlock(pos, state.setValue(FloodlightBlock.LIT, shouldBeLit), Block.UPDATE_CLIENTS);
            this.raycastDirty = true;
        }

        boolean rotChanged = Math.abs(this.yaw - this.lastRaycastYaw) > 3.0f
                || Math.abs(this.pitch - this.lastRaycastPitch) > 3.0f;

        if (shouldBeLit) {
            this.raycastCheckCounter++;
            if (this.raycastDirty || rotChanged || this.raycastCheckCounter >= 20) {
                this.raycastCheckCounter = 0;
                this.raycastDirty = false;
                this.lastRaycastYaw = this.yaw;
                this.lastRaycastPitch = this.pitch;
                updateBeam(level, pos, state);
            }
        } else {
            cleanupAllLightBlocks(level);
        }

        if (lightLevelChanged || Math.abs(this.prevYaw - this.yaw) > 0.05f || Math.abs(this.prevPitch - this.pitch) > 0.05f) {
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ==================== 3D SPHERICAL RAYCAST SYSTEM ====================

    private void updateBeam(ServerLevel level, BlockPos pos, BlockState state) {
        Direction mountFace = state.hasProperty(FloodlightBlock.MOUNT_FACE)
                ? state.getValue(FloodlightBlock.MOUNT_FACE)
                : Direction.UP;

        // 3D-вектор променя в локальній системі координат прожектора
        float yawRad = (float) Math.toRadians(this.yaw);
        float pitchRad = (float) Math.toRadians(this.pitch);
        float cosP = Mth.cos(pitchRad);
        Vec3 localBeamDir = new Vec3(-Mth.sin(yawRad) * cosP, Mth.sin(pitchRad), Mth.cos(yawRad) * cosP).normalize();

        // Поворот вектора під орієнтацію монтажної бази MOUNT_FACE
        Vector3f dirVec = localBeamDir.toVector3f();
        switch (mountFace) {
            case DOWN -> dirVec.rotateX((float) Math.toRadians(180));
            case NORTH -> dirVec.rotateX((float) Math.toRadians(90));
            case SOUTH -> {
                dirVec.rotateX((float) Math.toRadians(90));
                dirVec.rotateY((float) Math.toRadians(180));
            }
            case WEST -> {
                dirVec.rotateX((float) Math.toRadians(90));
                dirVec.rotateY((float) Math.toRadians(270));
            }
            case EAST -> {
                dirVec.rotateX((float) Math.toRadians(90));
                dirVec.rotateY((float) Math.toRadians(90));
            }
            case UP -> {}
        }
        Vec3 beamDir = new Vec3(dirVec.x(), dirVec.y(), dirVec.z()).normalize();

        // Зміщення центру обертання шарнірів барабана відносно центру блоку
        Vector3f pivotOffset = new Vector3f(0.0f, 0.0625f, 0.0f);
        switch (mountFace) {
            case DOWN -> pivotOffset.rotateX((float) Math.toRadians(180));
            case NORTH -> pivotOffset.rotateX((float) Math.toRadians(90));
            case SOUTH -> {
                pivotOffset.rotateX((float) Math.toRadians(90));
                pivotOffset.rotateY((float) Math.toRadians(180));
            }
            case WEST -> {
                pivotOffset.rotateX((float) Math.toRadians(90));
                pivotOffset.rotateY((float) Math.toRadians(270));
            }
            case EAST -> {
                pivotOffset.rotateX((float) Math.toRadians(90));
                pivotOffset.rotateY((float) Math.toRadians(90));
            }
            case UP -> {}
        }
        Vec3 pivot = Vec3.atCenterOf(worldPosition).add(pivotOffset.x(), pivotOffset.y(), pivotOffset.z());

        java.util.Map<BlockPos, Integer> desiredLights = new java.util.LinkedHashMap<>();
        float detectedDistance = MAX_DISTANCE;

        for (int dist = 1; dist <= MAX_DISTANCE; dist++) {
            Vec3 currentPoint = pivot.add(beamDir.scale(dist));
            BlockPos checkPos = BlockPos.containing(currentPoint);

            if (!level.isLoaded(checkPos)) {
                detectedDistance = dist;
                break;
            }

            if (checkPos.equals(pos)) {
                continue;
            }

            BlockState bs = level.getBlockState(checkPos);
            boolean isAirOrPassable = bs.isAir() || bs.is(Blocks.LIGHT) || bs.canBeReplaced()
                    || bs.getCollisionShape(level, checkPos).isEmpty();

            if (isAirOrPassable) {
                int baseLevel = calculateBaseLightLevel(dist);
                float fadeRatio = this.currentLightLevel / 15.0f;
                int finalLevel = Math.max(1, (int) Math.round(baseLevel * fadeRatio));

                desiredLights.put(checkPos.immutable(), Math.max(desiredLights.getOrDefault(checkPos, 0), finalLevel));
            } else {
                detectedDistance = (float) dist;
                break;
            }
        }

        boolean distChanged = Math.abs(this.beamDistance - detectedDistance) > 0.05f;
        this.beamDistance = detectedDistance;

        Iterator<BlockPos> it = activeLightPositions.iterator();
        while (it.hasNext()) {
            BlockPos oldPos = it.next();
            if (!desiredLights.containsKey(oldPos)) {
                removeLightAt(level, oldPos);
                it.remove();
            }
        }

        for (java.util.Map.Entry<BlockPos, Integer> entry : desiredLights.entrySet()) {
            placeOrUpdateLight(level, entry.getKey(), entry.getValue());
            activeLightPositions.add(entry.getKey());
        }
        setChanged();

        if (distChanged) {
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private int calculateBaseLightLevel(int dist) {
        if (dist <= 6) {
            return dist * 2 + 2; // 4, 6, 8, 10, 12, 14
        } else if (dist <= 42) {
            return 15;
        } else if (dist == 43) {
            return 14;
        } else if (dist == 44) {
            return 12;
        } else if (dist == 45) {
            return 10;
        } else if (dist == 46) {
            return 8;
        } else if (dist == 47) {
            return 6;
        } else if (dist == 48) {
            return 4;
        } else if (dist == 49) {
            return 3;
        } else {
            return 2; // d >= 50
        }
    }

    private void placeOrUpdateLight(ServerLevel level, BlockPos p, int lightLevel) {
        if (level.isLoaded(p)) {
            BlockState bs = level.getBlockState(p);
            if (bs.isAir() || bs.is(Blocks.LIGHT) || (bs.canBeReplaced() && !bs.is(Blocks.WATER))) {
                boolean isWater = level.getFluidState(p).getType() == Fluids.WATER;
                BlockState desiredLight = Blocks.LIGHT.defaultBlockState()
                        .setValue(LightBlock.LEVEL, lightLevel)
                        .setValue(BlockStateProperties.WATERLOGGED, isWater);
                if (!bs.equals(desiredLight)) {
                    level.setBlock(p, desiredLight, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private void removeLightAt(ServerLevel level, BlockPos p) {
        if (level.isLoaded(p)) {
            BlockState bs = level.getBlockState(p);
            if (bs.is(Blocks.LIGHT)) {
                if (bs.hasProperty(BlockStateProperties.WATERLOGGED) && bs.getValue(BlockStateProperties.WATERLOGGED)) {
                    level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                } else {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    public void cleanupAllLightBlocks(ServerLevel level) {
        if (!activeLightPositions.isEmpty()) {
            for (BlockPos p : activeLightPositions) {
                removeLightAt(level, p);
            }
            activeLightPositions.clear();
            setChanged();
        }
        if (this.beamDistance != 0.0f) {
            this.beamDistance = 0.0f;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void cleanupLightBlocks() {
        if (level instanceof ServerLevel sl) {
            cleanupAllLightBlocks(sl);
        }
    }

    // ==================== IEnergyReceiver ====================

    @Override
    public long getEnergyStored() {
        return energyStored;
    }

    @Override
    public long getMaxEnergyStored() {
        return MAX_ENERGY;
    }

    @Override
    public void setEnergyStored(long energy) {
        this.energyStored = Math.max(0L, Math.min(energy, MAX_ENERGY));
        setChanged();
    }

    @Override
    public long getReceiveSpeed() {
        return RECEIVE_SPEED;
    }

    @Override
    public Priority getPriority() {
        return Priority.NORMAL;
    }

    @Override
    public boolean canReceive() {
        return energyStored < MAX_ENERGY;
    }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        long canAccept = Math.min(MAX_ENERGY - energyStored, Math.min(maxReceive, RECEIVE_SPEED));
        if (!simulate && canAccept > 0L) {
            energyStored += canAccept;
            setChanged();
        }
        return canAccept;
    }

    // ==================== IEnergyConnector ====================

    @Override
    public boolean canConnectEnergy(@Nullable Direction side) {
        if (side == null) {
            return true;
        }
        return side == getActiveConnectionSide();
    }

    @Nullable
    public IEnergyReceiver getEnergyReceiver(@Nullable Direction side) {
        return canConnectEnergy(side) ? this : null;
    }

    @Nullable
    public IEnergyConnector getEnergyConnector(@Nullable Direction side) {
        return canConnectEnergy(side) ? this : null;
    }

    @Nullable
    public net.neoforged.neoforge.energy.IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return canConnectEnergy(side) ? new com.trd.api.energy.LightEnergyStorage(this) : null;
    }

    // ==================== LIFECYCLE & NETWORK ====================

    @Override
    public void onLoad() {
        super.onLoad();
        this.raycastDirty = true;
        if (level instanceof ServerLevel serverLevel) {
            EnergyNetworkManager.get(serverLevel).addNode(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        cleanupLightBlocks();
        if (level instanceof ServerLevel serverLevel) {
            EnergyNetworkManager.get(serverLevel).removeNode(worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        cleanupLightBlocks();
        if (level instanceof ServerLevel serverLevel) {
            EnergyNetworkManager.get(serverLevel).removeNode(worldPosition);
        }
    }

    // ==================== NBT PERSISTENCE ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putLong("Energy", energyStored);
        tag.putInt("LightLevel", currentLightLevel);
        tag.putFloat("BeamDistance", beamDistance);
        tag.putInt("WarmupTicks", warmupTicks);
        tag.putInt("CooldownTicks", cooldownTicks);

        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putFloat("TargetYaw", targetYaw);
        tag.putFloat("TargetPitch", targetPitch);

        long[] positions = activeLightPositions.stream().mapToLong(BlockPos::asLong).toArray();
        tag.putLongArray("LightPositions", positions);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        energyStored = tag.getLong("Energy");
        currentLightLevel = tag.getInt("LightLevel");
        if (tag.contains("BeamDistance")) {
            beamDistance = tag.getFloat("BeamDistance");
        } else {
            beamDistance = 50.0f;
        }
        warmupTicks = tag.getInt("WarmupTicks");
        cooldownTicks = tag.getInt("CooldownTicks");

        if (tag.contains("Yaw")) yaw = tag.getFloat("Yaw");
        if (tag.contains("Pitch")) pitch = tag.getFloat("Pitch");
        if (tag.contains("TargetYaw")) targetYaw = tag.getFloat("TargetYaw");
        if (tag.contains("TargetPitch")) targetPitch = tag.getFloat("TargetPitch");
        prevYaw = yaw;
        prevPitch = pitch;

        activeLightPositions.clear();
        if (tag.contains("LightPositions")) {
            for (long posLong : tag.getLongArray("LightPositions")) {
                activeLightPositions.add(BlockPos.of(posLong));
            }
        }
        raycastDirty = true;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, provider);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            loadAdditional(tag, lookupProvider);
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        super.handleUpdateTag(tag, lookupProvider);
        loadAdditional(tag, lookupProvider);
    }

    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(52.0);
    }
}
