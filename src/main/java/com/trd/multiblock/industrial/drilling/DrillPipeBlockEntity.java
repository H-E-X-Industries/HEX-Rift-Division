package com.trd.multiblock.industrial.drilling;

import com.trd.api.industiral.rotation.ClientKineticAngleTracker;
import com.trd.api.industiral.rotation.Rotational;
import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.rotation.KineticNodeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * BlockEntity буровой трубы (DrillPipe 1×1×4).
 * Вращается на базе кинетической сети (api/rotation/) либо синхронно с роторным столом,
 * плавно останавливаясь с инерцией после завершения 4-блочного спуска до следующей стыковки.
 */
public class DrillPipeBlockEntity extends KineticNodeBlockEntity {

    private boolean inWellbore = false;
    private boolean hiddenForColumnDescent = false;

    private boolean coastActive = false;
    private float coastAngleRad = 0.0f;
    private float prevCoastAngleRad = 0.0f;
    private float coastOmegaRadPerTick = 0.0f;
    private BlockPos coastDrivePos = null;

    public DrillPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRILL_PIPE_BE.get(), pos, state);
    }

    @Override public long getMaxTorqueTolerance() { return Long.MAX_VALUE; }
    @Override public long getMaxTorque() { return Long.MAX_VALUE; }
    @Override public double getInertiaContribution() { return 6.0; }
    @Override public long getMaxSpeed() { return 2000L; }
    @Override public long getTorque() { return 0L; }
    @Override public boolean isSource() { return false; }

    public Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(DrillPipeBlock.FACING) ? state.getValue(DrillPipeBlock.FACING) : Direction.NORTH;
    }

    public boolean isHorizontal() {
        BlockState state = getBlockState();
        return state.hasProperty(DrillPipeBlock.HORIZONTAL) && state.getValue(DrillPipeBlock.HORIZONTAL);
    }

    @Override
    public Direction[] getPropagationDirections() {
        return isHorizontal() ? new Direction[0] : new Direction[]{Direction.UP, Direction.DOWN};
    }

    @Override
    public List<BlockPos> getPotentialConnections(Level level, BlockPos myPos) {
        return isHorizontal() ? List.of() : List.of(myPos.below(), myPos.above(4));
    }

    @Override
    public boolean canConnectMechanically(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        if (isHorizontal()) {
            return false;
        }
        return neighborPos.equals(myPos.below()) || neighborPos.equals(myPos.above(4));
    }

    public void setInWellbore(boolean inWellbore) {
        if (this.inWellbore != inWellbore) {
            this.inWellbore = inWellbore;
            setChanged();
            syncToClient();
        }
    }

    public boolean isInWellbore() {
        return inWellbore;
    }

    public void setHiddenForColumnDescent(boolean hidden) {
        if (this.hiddenForColumnDescent != hidden) {
            this.hiddenForColumnDescent = hidden;
            setChanged();
            syncToClient();
        }
    }

    public boolean isHiddenForColumnDescent() {
        return hiddenForColumnDescent;
    }

    /** Запускает инерционный выбег трубы после стыковки/опускания на 4 блока, наследуя фазу стола. */
    public void startInertialCoastFromDrive(BlockPos drivePos, long rpm) {
        this.coastDrivePos = drivePos;
        this.coastOmegaRadPerTick = (float) (rpm * Math.PI / 600.0);
        this.coastActive = Math.abs(this.coastOmegaRadPerTick) > 0.0005f;
        setChanged();
        syncToClient();
    }

    public void clientTick() {
        this.prevCoastAngleRad = this.coastAngleRad;
        if (coastActive) {
            this.coastAngleRad += this.coastOmegaRadPerTick;
            this.coastOmegaRadPerTick *= 0.92f;
            if (Math.abs(this.coastOmegaRadPerTick) < 0.001f) {
                this.coastOmegaRadPerTick = 0.0f;
                this.coastActive = false;
            }
        }
    }

    public float getRenderSpinAngleRad(float partialTick) {
        if (isHorizontal()) {
            return 0.0f;
        }
        if (inWellbore) {
            if (speed != 0L) {
                return ClientKineticAngleTracker.getAngle(this, null, partialTick);
            }
            return Mth.lerp(partialTick, prevCoastAngleRad, coastAngleRad);
        }
        return ClientKineticAngleTracker.getAngle(this, null, partialTick);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putBoolean("InWellbore", inWellbore);
        tag.putBoolean("HiddenDescent", hiddenForColumnDescent);
        tag.putBoolean("CoastActive", coastActive);
        tag.putFloat("CoastAngle", coastAngleRad);
        tag.putFloat("CoastOmega", coastOmegaRadPerTick);
        if (coastDrivePos != null) {
            tag.putLong("CoastDrivePos", coastDrivePos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        inWellbore = tag.getBoolean("InWellbore");
        hiddenForColumnDescent = tag.getBoolean("HiddenDescent");
        boolean incomingCoast = tag.getBoolean("CoastActive");
        float incomingAngle = tag.getFloat("CoastAngle");
        float incomingOmega = tag.getFloat("CoastOmega");
        coastDrivePos = tag.contains("CoastDrivePos") ? BlockPos.of(tag.getLong("CoastDrivePos")) : null;

        if (level != null && level.isClientSide && incomingCoast && coastDrivePos != null) {
            if (level.getBlockEntity(coastDrivePos) instanceof RotaryTableBlockEntity drive) {
                incomingAngle = drive.getFrozenDiskAngle();
            }
        }
        if (incomingCoast || !coastActive) {
            coastActive = incomingCoast;
            coastAngleRad = incomingAngle;
            prevCoastAngleRad = incomingAngle;
            coastOmegaRadPerTick = incomingOmega;
        }
    }
}
