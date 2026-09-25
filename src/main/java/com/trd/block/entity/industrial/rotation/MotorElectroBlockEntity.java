package com.trd.block.entity.industrial.rotation;

import com.trd.api.energy.IEnergyConnector;
import com.trd.api.energy.IEnergyReceiver;
import com.trd.api.rotation.KineticNetwork;
import com.trd.api.rotation.KineticNetworkManager;
import com.trd.api.rotation.Rotational;
import com.trd.api.rotation.ShaftDiameter;
import com.trd.block.basic.industrial.rotation.MotorElectroBlock;
import com.trd.block.basic.industrial.rotation.ShaftBlock;
import com.trd.block.entity.ModBlockEntities;
import com.trd.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MotorElectroBlockEntity extends KineticNodeBlockEntity implements IEnergyReceiver, IEnergyConnector {

    // ===================== КОНСТАНТЫ =====================
    public static final long MAX_ENERGY = 10_000L;
    public static final long RECEIVE_SPEED = 1_000L;
    public static final int MIN_RPM = 100;
    public static final int MAX_RPM = 1_000;

    // ===================== ПОЛЯ =====================
    private long energyStored = 0L;
    private int targetRpm = MAX_RPM;
    private boolean reversed = false;
    private boolean hasEnergy = false;
    private int powerDeficitTicks = 0;
    private static final int MAX_DEFICIT_TICKS = 10;
    private int startSoundCooldown = 0;

    public boolean isTriggered = false;

    // ContainerData для GUI: [0]=targetRpm [1]=energy/10 [2]=JE/s [3]=torque
    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> targetRpm;
                case 1 -> (int) (energyStored / 10);
                case 2 -> getConsumptionPerSecond();
                case 3 -> (int) getTorqueNm();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) targetRpm = clampRpm(value);
        }

        @Override
        public int getCount() { return 4; }
    };

    public MotorElectroBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOTOR_ELECTRO_BE.get(), pos, state);
    }

    private Direction getBackSide() {
        BlockState state = getBlockState();
        if (!state.hasProperty(MotorElectroBlock.FACING)) return Direction.NORTH;
        return state.getValue(MotorElectroBlock.FACING).getOpposite();
    }

    // ===================== CAPABILITY ACCESSORS =====================
    @Nullable
    public IEnergyReceiver getEnergyReceiver(@Nullable Direction side) {
        if (side == null || side == getBackSide()) {
            return this;
        }
        return null;
    }

    @Nullable
    public IEnergyConnector getEnergyConnector(@Nullable Direction side) {
        if (side == null || side == getBackSide()) {
            return this;
        }
        return null;
    }

    // ===================== IEnergyConnector =====================
    @Override
    public boolean canConnectEnergy(Direction side) {
        return side == getBackSide();
    }

    // ===================== IEnergyReceiver =====================
    @Override
    public long getEnergyStored() { return energyStored; }

    @Override
    public long getMaxEnergyStored() { return MAX_ENERGY; }

    @Override
    public void setEnergyStored(long energy) {
        this.energyStored = Math.max(0, Math.min(energy, MAX_ENERGY));
    }

    @Override
    public long getReceiveSpeed() { return RECEIVE_SPEED; }

    @Override
    public Priority getPriority() { return Priority.NORMAL; }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        long canReceive = Math.min(MAX_ENERGY - energyStored, Math.min(maxReceive, RECEIVE_SPEED));
        if (!simulate && canReceive > 0) {
            energyStored += canReceive;
            setChanged();
        }
        return canReceive;
    }

    @Override
    public boolean canReceive() { return energyStored < MAX_ENERGY; }

    // ===================== ПАРАМЕТРЫ МОТОРА =====================

    public long getTorqueNm() { return targetRpm / 5L; }

    public int getConsumptionPerTick() {
        return Math.max(1, targetRpm * 25 / MAX_RPM);
    }

    public int getConsumptionPerSecond() { return getConsumptionPerTick() * 20; }

    public int getTargetRpm() { return targetRpm; }

    public void setTargetRpm(int rpm) {
        int newRpm = clampRpm(rpm);
        if (this.targetRpm != newRpm) {
            this.targetRpm = newRpm;
            setChanged();
            requestKineticRecalculation();
        }
    }

    public boolean isReversed() { return reversed; }

    public boolean isRunning() { return hasEnergy; }

    public void toggleDirection() {
        this.reversed = !this.reversed;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            requestKineticRecalculation();
        }
    }

    private static int clampRpm(int rpm) {
        rpm = Math.round(rpm / 10.0f) * 10;
        return Math.max(MIN_RPM, Math.min(MAX_RPM, rpm));
    }

    // ===================== TICK =====================

    public static void clientTick(Level level, BlockPos pos, BlockState state, MotorElectroBlockEntity be) {
        if (level.isClientSide) {
            com.trd.client.sound.MotorElectroSoundHandler.tick(be);
        }
    }

    public static <T extends BlockEntity> BlockEntityTicker<T> createTicker() {
        return (level, pos, state, be) -> {
            if (!level.isClientSide && be instanceof MotorElectroBlockEntity motor) {
                motor.serverTick((ServerLevel) level);
            }
        };
    }

    private void serverTick(ServerLevel serverLevel) {
        int consumption = getConsumptionPerTick();

        if (startSoundCooldown > 0) {
            startSoundCooldown--;
        }

        if (energyStored >= consumption) {
            energyStored -= consumption;
            powerDeficitTicks = 0;
            if (!hasEnergy) {
                hasEnergy = true;
                this.speed = 0;
                this.lastSyncedSpeed = 0;
                requestKineticRecalculation();
                if (startSoundCooldown == 0) {
                    serverLevel.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                            ModSounds.MOTOR_ELECTRO_START.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                    startSoundCooldown = 60;
                }
            }
        } else {
            powerDeficitTicks++;
            if (powerDeficitTicks >= MAX_DEFICIT_TICKS) {
                if (hasEnergy) {
                    hasEnergy = false;
                    this.speed = 0;
                    this.lastSyncedSpeed = 0;
                    requestKineticRecalculation();
                }
            }
        }

        serverLevel.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        setChanged();
    }

    private void requestKineticRecalculation() {
        if (level instanceof ServerLevel serverLevel) {
            KineticNetwork net = KineticNetworkManager.get(serverLevel).getNetworkFor(worldPosition);
            if (net != null) net.requestRecalculation();
        }
    }

    // ===================== Rotational =====================

    @Override
    public long getGeneratedSpeed() {
        if (!hasEnergy) return 0;
        return reversed ? -targetRpm : targetRpm;
    }

    @Override
    public long getVisualSpeed() {
        if (!hasEnergy) return 0;
        BlockState state = getBlockState();
        if (!state.hasProperty(MotorElectroBlock.FACING)) return 0;
        Direction facing = state.getValue(MotorElectroBlock.FACING);
        if (facing == Direction.SOUTH || facing == Direction.EAST || facing == Direction.UP) {
            return -this.speed;
        }
        return this.speed;
    }

    @Override
    public long getTorque() {
        return hasEnergy ? getTorqueNm() : 0L;
    }

    @Override
    public boolean isSource() { return true; }

    @Override
    public double getInertiaContribution() { return 5.0; }

    @Override
    public long getMaxTorqueTolerance() { return getMaxTorque(); }

    @Override
    public long getMaxSpeed() { return MAX_RPM; }

    @Override
    public long getMaxTorque() { return 1024; }

    @Override
    public boolean canConnectMechanically(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        BlockState state = getBlockState();
        if (!state.hasProperty(MotorElectroBlock.FACING)) return false;
        Direction facing = state.getValue(MotorElectroBlock.FACING);
        if (!neighborPos.equals(myPos.relative(facing))) return false;

        if (neighbor instanceof ShaftBlockEntity shaftBE) {
            if (shaftBE.getBlockState().getBlock() instanceof ShaftBlock shaftBlock) {
                return shaftBlock.getDiameter() == ShaftDiameter.LIGHT;
            }
        } else if (neighbor instanceof ClutchBlockEntity clutch) {
            return clutch.hasShaft() && clutch.getShaftDiameter() == ShaftDiameter.LIGHT;
        } else if (neighbor instanceof BearingBlockEntity bearing) {
            return bearing.hasShaft() && bearing.getShaftDiameter() == ShaftDiameter.LIGHT;
        }
        return true;
    }

    @Override
    public Direction[] getPropagationDirections() {
        BlockState state = getBlockState();
        if (!state.hasProperty(MotorElectroBlock.FACING)) return new Direction[0];
        return new Direction[]{state.getValue(MotorElectroBlock.FACING)};
    }

    @Override
    public List<BlockPos> getPotentialConnections(Level lvl, BlockPos myPos) {
        BlockState state = getBlockState();
        if (!state.hasProperty(MotorElectroBlock.FACING)) return List.of();
        Direction facing = state.getValue(MotorElectroBlock.FACING);
        return List.of(myPos.relative(facing));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            KineticNetwork net = KineticNetworkManager
                    .get((ServerLevel) level)
                    .getNetworkFor(worldPosition);
            if (net != null) {
                this.speed = net.getSpeed();
                this.lastSyncedSpeed = this.speed;
                net.requestRecalculation();
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            com.trd.client.sound.MotorElectroSoundHandler.stop(worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) {
            com.trd.client.sound.MotorElectroSoundHandler.stop(worldPosition);
        }
    }

    // ===================== NBT =====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putLong("EnergyStored", energyStored);
        tag.putInt("TargetRpm", targetRpm);
        tag.putBoolean("Reversed", reversed);
        tag.putBoolean("HasEnergy", hasEnergy);
        tag.putBoolean("IsTriggered", isTriggered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        energyStored = tag.getLong("EnergyStored");
        targetRpm = clampRpm(tag.getInt("TargetRpm") == 0 ? MAX_RPM : tag.getInt("TargetRpm"));
        reversed = tag.getBoolean("Reversed");
        hasEnergy = tag.getBoolean("HasEnergy");
        isTriggered = tag.getBoolean("IsTriggered");
    }

    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.5D);
    }

    public long getCurrentVisualSpeed() {
        return this.speed;
    }
}
