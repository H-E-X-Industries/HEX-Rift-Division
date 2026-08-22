package com.trd.multiblock.industrial.centrifuge;

import com.trd.api.energy.EnergyNetworkManager;
import com.trd.api.energy.IEnergyConnector;
import com.trd.api.energy.IEnergyReceiver;
import com.trd.block.entity.ModBlockEntities;
import com.trd.capability.ModCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CentrifugeMotorBlockEntity extends BlockEntity implements IEnergyReceiver, IEnergyConnector {

    private final LazyOptional<IEnergyReceiver> receiverCap = LazyOptional.of(() -> this);
    private final LazyOptional<IEnergyConnector> connectorCap = LazyOptional.of(() -> this);
    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(AttachedItemHandler::new);

    public CentrifugeMotorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CENTRIFUGE_MOTOR_BE.get(), pos, state);
    }

    @Nullable
    public CentrifugeConusBlockEntity getAttachedConus() {
        if (level == null) return null;
        BlockEntity be = level.getBlockEntity(worldPosition.above());
        return be instanceof CentrifugeConusBlockEntity conus ? conus : null;
    }

    // ===================== ENERGy =====================

    @Override
    public long getEnergyStored() {
        CentrifugeConusBlockEntity conus = getAttachedConus();
        return conus != null ? conus.getEnergyStored() : 0L;
    }

    @Override
    public long getMaxEnergyStored() {
        CentrifugeConusBlockEntity conus = getAttachedConus();
        return conus != null ? conus.getMaxEnergy() : 0L;
    }

    @Override
    public void setEnergyStored(long energy) {
        CentrifugeConusBlockEntity conus = getAttachedConus();
        if (conus != null) {
            conus.addEnergy(energy - conus.getEnergyStored());
        }
    }

    @Override
    public long getReceiveSpeed() { return CentrifugeConusBlockEntity.RECEIVE_SPEED; }

    @Override
    public Priority getPriority() { return Priority.NORMAL; }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        CentrifugeConusBlockEntity conus = getAttachedConus();
        if (conus == null) return 0L;
        long space = CentrifugeConusBlockEntity.MAX_ENERGY - conus.getEnergyStored();
        long canReceive = Math.min(space, Math.min(maxReceive, CentrifugeConusBlockEntity.RECEIVE_SPEED));
        if (!simulate && canReceive > 0) {
            // Буфер лежит в BE насадки; пополнение идёт через её инвентарь-независимое поле.
            conus.addEnergy(canReceive);
        }
        return canReceive;
    }

    @Override
    public boolean canReceive() {
        CentrifugeConusBlockEntity conus = getAttachedConus();
        return conus != null && conus.getEnergyStored() < CentrifugeConusBlockEntity.MAX_ENERGY;
    }

    @Override
    public boolean canConnectEnergy(Direction side) {
        return side == null || side == Direction.DOWN;
    }

    // ===================== CAPABILITIES =====================

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

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        receiverCap.invalidate();
        connectorCap.invalidate();
        itemCap.invalidate();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && side != Direction.DOWN) {
            return itemCap.cast();
        }
        if (side == null || side == Direction.DOWN) {
            if (cap == ModCapabilities.ENERGY_RECEIVER) return receiverCap.cast();
            if (cap == ModCapabilities.ENERGY_CONNECTOR) return connectorCap.cast();
        }
        return super.getCapability(cap, side);
    }

    /**
     * Предметный доступ к насадке через мотор: вставка только во вход и слот
     * аккумуляторов, извлечение только из выходных слотов.
     */
    private class AttachedItemHandler implements IItemHandler {

        @Nullable
        private IItemHandler backing() {
            CentrifugeConusBlockEntity conus = getAttachedConus();
            return conus != null ? conus.getInventory() : null;
        }

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
            if (slot != CentrifugeConusBlockEntity.INPUT_SLOT
                    && slot != CentrifugeConusBlockEntity.BATTERY_SLOT) return stack;
            return h.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler h = backing();
            if (h == null) return ItemStack.EMPTY;
            if (slot < CentrifugeConusBlockEntity.FIRST_OUTPUT_SLOT
                    || slot >= CentrifugeConusBlockEntity.FIRST_OUTPUT_SLOT + CentrifugeConusBlockEntity.OUTPUT_SLOTS) {
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
            return slot == CentrifugeConusBlockEntity.INPUT_SLOT
                    || slot == CentrifugeConusBlockEntity.BATTERY_SLOT;
        }
    }
}
