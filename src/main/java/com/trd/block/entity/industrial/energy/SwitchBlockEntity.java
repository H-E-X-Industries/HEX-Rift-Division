package com.trd.block.entity.industrial.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import com.trd.api.energy.EnergyNetworkManager;
import com.trd.api.energy.IEnergyConnector;
import com.trd.block.basic.industrial.energy.SwitchBlock;
import com.trd.block.entity.ModBlockEntities;

import javax.annotation.Nullable;

public class SwitchBlockEntity extends BlockEntity implements IEnergyConnector {

    /** Кешированный уровень редстоун-сигнала (не состояние блока!) */
    public boolean prevSignal = false;

    public SwitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SWITCH_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SwitchBlockEntity entity) {
        if (level.isClientSide) return;

        if (state.getValue(SwitchBlock.POWERED)) {
            EnergyNetworkManager.get((ServerLevel) level).ensureNodeConnected(pos);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            this.prevSignal = this.level.hasNeighborSignal(this.getBlockPos());
        }
    }

    @Nullable
    public IEnergyConnector getEnergyConnector(@Nullable Direction side) {
        if (isValidSide(side) && isPowered()) {
            return this;
        }
        return null;
    }

    private boolean isPowered() {
        BlockState state = this.getBlockState();
        return state.hasProperty(SwitchBlock.POWERED) && state.getValue(SwitchBlock.POWERED);
    }

    private boolean isValidSide(@Nullable Direction side) {
        BlockState state = this.getBlockState();
        if (!(state.getBlock() instanceof SwitchBlock)) return false;
        if (side == null) return true;

        Direction facing = state.getValue(SwitchBlock.FACING);
        if (side.getAxis() == Direction.Axis.Y) return false;
        return side != facing && side != facing.getOpposite();
    }

    @Override
    public boolean canConnectEnergy(Direction side) {
        return isValidSide(side);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide) {
            EnergyNetworkManager.get((ServerLevel) this.level).removeNode(this.getBlockPos());
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putBoolean("isTriggered", prevSignal);
    }

    @Override
    public void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        prevSignal = tag.getBoolean("isTriggered");
    }
}
