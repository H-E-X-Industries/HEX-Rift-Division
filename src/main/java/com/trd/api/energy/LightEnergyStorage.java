package com.trd.api.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * NeoForge {@link IEnergyStorage} adapter for lighting appliances implementing {@link IEnergyReceiver}.
 * Enables seamless compatibility between TRD light fixtures/floodlights and external Forge/NeoForge energy sources.
 */
public class LightEnergyStorage implements IEnergyStorage {

    private final IEnergyReceiver receiver;

    public LightEnergyStorage(IEnergyReceiver receiver) {
        this.receiver = receiver;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return (int) receiver.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(receiver.getEnergyStored(), Integer.MAX_VALUE);
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) Math.min(receiver.getMaxEnergyStored(), Integer.MAX_VALUE);
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return receiver.canReceive();
    }
}
