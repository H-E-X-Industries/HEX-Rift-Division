package com.trd.api.energy;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class ForgeItemWrapper implements IEnergyStorage {
    private final ItemEnergyStorage internal;

    public ForgeItemWrapper(ItemEnergyStorage internal) {
        this.internal = internal;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return (int) internal.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return (int) internal.extractEnergy(maxExtract, simulate);
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(internal.getEnergyStored(), Integer.MAX_VALUE);
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) Math.min(internal.getMaxEnergyStored(), Integer.MAX_VALUE);
    }

    @Override
    public boolean canExtract() {
        return internal.canExtract();
    }

    @Override
    public boolean canReceive() {
        return internal.canReceive();
    }
}
