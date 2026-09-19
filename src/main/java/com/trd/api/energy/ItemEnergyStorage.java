package com.trd.api.energy;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public class ItemEnergyStorage implements IEnergyProvider, IEnergyReceiver {
    private final ItemStack stack;
    private final long capacity;
    private final long maxReceive;
    private final long maxExtract;

    public ItemEnergyStorage(ItemStack stack, long capacity, long maxReceive, long maxExtract) {
        this.stack = stack;
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
    }

    @Override
    public long getEnergyStored() {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getLong("energy");
    }

    @Override
    public void setEnergyStored(long energy) {
        long clamped = Math.max(0, Math.min(energy, capacity));
        net.minecraft.world.item.component.CustomData data = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
        net.minecraft.nbt.CompoundTag tag = data.copyTag();
        tag.putLong("energy", clamped);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
    }

    @Override
    public long getMaxEnergyStored() {
        return this.capacity;
    }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        if (!canReceive()) return 0;
        long energyStored = getEnergyStored();
        long energyReceived = Math.min(capacity - energyStored, Math.min(this.maxReceive, maxReceive));
        if (!simulate && energyReceived > 0) {
            setEnergyStored(energyStored + energyReceived);
        }
        return energyReceived;
    }

    @Override
    public long getReceiveSpeed() { return this.maxReceive; }
    @Override
    public Priority getPriority() { return Priority.NORMAL; }
    @Override
    public boolean canReceive() { return this.maxReceive > 0 && getEnergyStored() < capacity; }

    @Override
    public long extractEnergy(long maxExtract, boolean simulate) {
        if (!canExtract()) return 0;
        long energyStored = getEnergyStored();
        long energyExtracted = Math.min(energyStored, Math.min(this.maxExtract, maxExtract));
        if (!simulate && energyExtracted > 0) {
            setEnergyStored(energyStored - energyExtracted);
        }
        return energyExtracted;
    }

    @Override
    public long getProvideSpeed() { return this.maxExtract; }
    @Override
    public boolean canExtract() { return this.maxExtract > 0 && getEnergyStored() > 0; }
    @Override
    public boolean canConnectEnergy(Direction side) { return true; }
}
