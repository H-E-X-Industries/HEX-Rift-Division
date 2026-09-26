package com.trd.multiblock.industrial.centrifuge;

import com.trd.api.energy.EnergyNetworkManager;
import com.trd.api.energy.IEnergyConnector;
import com.trd.api.energy.IEnergyReceiver;
import com.trd.block.entity.ModBlockEntities;
import com.trd.multiblock.industrial.centrifuge.conus.CentrifugeConusBlockEntity;
import com.trd.multiblock.industrial.centrifuge.cylinder.CentrifugeCylinderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CentrifugeMotorBlockEntity extends BlockEntity implements IEnergyReceiver, IEnergyConnector {

    private final IItemHandler itemHandler = new AttachedItemHandler();

    public CentrifugeMotorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CENTRIFUGE_MOTOR_BE.get(), pos, state);
    }

    @Nullable
    public CentrifugeConusBlockEntity getAttachedConus() {
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(worldPosition.above());
        return be instanceof CentrifugeConusBlockEntity conus ? conus : null;
    }

    @Nullable
    public CentrifugeCylinderBlockEntity getAttachedCylinder() {
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(worldPosition.above());
        return be instanceof CentrifugeCylinderBlockEntity cylinder ? cylinder : null;
    }

    // ===================== ENERGY =====================

    @Override
    public long getEnergyStored() {
        CentrifugeConusBlockEntity conus = getAttachedConus();
        if (conus != null) return conus.getEnergyStored();
        CentrifugeCylinderBlockEntity cylinder = getAttachedCylinder();
        return cylinder != null ? cylinder.getEnergyStored() : 0L;
    }

    @Override
    public long getMaxEnergyStored() {
        CentrifugeConusBlockEntity conus = getAttachedConus();
        if (conus != null) return conus.getMaxEnergy();
        CentrifugeCylinderBlockEntity cylinder = getAttachedCylinder();
        return cylinder != null ? cylinder.getMaxEnergy() : 0L;
    }

    @Override
    public void setEnergyStored(long energy) {
        CentrifugeConusBlockEntity conus = getAttachedConus();
        if (conus != null) {
            conus.addEnergy(energy - conus.getEnergyStored());
            return;
        }
        CentrifugeCylinderBlockEntity cylinder = getAttachedCylinder();
        if (cylinder != null) {
            cylinder.addEnergy(energy - cylinder.getEnergyStored());
        }
    }

    @Override
    public long getReceiveSpeed() { return CentrifugeConusBlockEntity.RECEIVE_SPEED; }

    @Override
    public Priority getPriority() { return Priority.NORMAL; }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        CentrifugeConusBlockEntity conus = getAttachedConus();
        if (conus != null) {
            long space = CentrifugeConusBlockEntity.MAX_ENERGY - conus.getEnergyStored();
            long canReceive = Math.min(space, Math.min(maxReceive, CentrifugeConusBlockEntity.RECEIVE_SPEED));
            if (!simulate && canReceive > 0) {
                conus.addEnergy(canReceive);
            }
            return canReceive;
        }
        CentrifugeCylinderBlockEntity cylinder = getAttachedCylinder();
        if (cylinder != null) {
            long space = CentrifugeCylinderBlockEntity.MAX_ENERGY - cylinder.getEnergyStored();
            long canReceive = Math.min(space, Math.min(maxReceive, CentrifugeCylinderBlockEntity.RECEIVE_SPEED));
            if (!simulate && canReceive > 0) {
                cylinder.addEnergy(canReceive);
            }
            return canReceive;
        }
        return 0L;
    }

    @Override
    public boolean canReceive() {
        CentrifugeConusBlockEntity conus = getAttachedConus();
        if (conus != null) return conus.getEnergyStored() < CentrifugeConusBlockEntity.MAX_ENERGY;
        CentrifugeCylinderBlockEntity cylinder = getAttachedCylinder();
        return cylinder != null && cylinder.getEnergyStored() < CentrifugeCylinderBlockEntity.MAX_ENERGY;
    }

    @Override
    public boolean canConnectEnergy(Direction side) {
        return side != Direction.UP;
    }

    // ===================== LIFECYCLE =====================

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            EnergyNetworkManager.get((ServerLevel) level).addNode(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            EnergyNetworkManager.get((ServerLevel) level).removeNode(worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            EnergyNetworkManager.get((ServerLevel) level).removeNode(getBlockPos());
        }
    }

    public IEnergyReceiver getEnergyReceiver(Direction side) {
        return canConnectEnergy(side) ? this : null;
    }

    public IEnergyConnector getEnergyConnector(Direction side) {
        return canConnectEnergy(side) ? this : null;
    }

    public IItemHandler getItemHandler(Direction side) {
        return side != Direction.DOWN ? itemHandler : null;
    }

    @Nullable
    public IFluidHandler getFluidHandler(Direction side) {
        CentrifugeCylinderBlockEntity cylinder = getAttachedCylinder();
        return cylinder != null ? cylinder.getFluidHandlerCapability() : null;
    }

    private class AttachedItemHandler implements IItemHandler {

        @Nullable
        private IItemHandler backing() {
            CentrifugeConusBlockEntity conus = getAttachedConus();
            if (conus != null) return conus.getInventory();
            CentrifugeCylinderBlockEntity cylinder = getAttachedCylinder();
            return cylinder != null ? cylinder.getInventory() : null;
        }

        private boolean isCylinder() { return getAttachedCylinder() != null; }

        @Override
        public int getSlots() {
            IItemHandler h = backing();
            return h != null ? h.getSlots() : 0;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            IItemHandler h = backing();
            return h != null ? h.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            IItemHandler h = backing();
            if (h == null || stack.isEmpty()) return stack;
            if (isCylinder()) {
                if (slot != CentrifugeCylinderBlockEntity.BATTERY_SLOT) return stack;
            } else if (slot != CentrifugeConusBlockEntity.INPUT_SLOT
                    && slot != CentrifugeConusBlockEntity.BATTERY_SLOT) {
                return stack;
            }
            return h.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler h = backing();
            if (h == null) return ItemStack.EMPTY;
            int firstOutput = isCylinder()
                    ? CentrifugeCylinderBlockEntity.FIRST_OUTPUT_SLOT
                    : CentrifugeConusBlockEntity.FIRST_OUTPUT_SLOT;
            int outputCount = isCylinder()
                    ? CentrifugeCylinderBlockEntity.OUTPUT_SLOTS
                    : CentrifugeConusBlockEntity.OUTPUT_SLOTS;
            if (slot < firstOutput || slot >= firstOutput + outputCount) {
                return ItemStack.EMPTY;
            }
            return h.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            IItemHandler h = backing();
            return h != null ? h.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            IItemHandler h = backing();
            if (h == null) return false;
            if (isCylinder()) {
                return slot == CentrifugeCylinderBlockEntity.BATTERY_SLOT;
            }
            return slot == CentrifugeConusBlockEntity.INPUT_SLOT
                    || slot == CentrifugeConusBlockEntity.BATTERY_SLOT;
        }
    }
}
