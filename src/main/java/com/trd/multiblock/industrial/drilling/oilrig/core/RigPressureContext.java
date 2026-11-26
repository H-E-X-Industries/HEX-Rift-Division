package com.trd.multiblock.industrial.drilling.oilrig.core;

import com.trd.api.nature.ore.oil.OilComposition;
import org.jetbrains.annotations.Nullable;

/**
 * Контекст тика давления скважины, передаваемый в хуки IBlowoutPreventerModule.
 */
public final class RigPressureContext {
    public float currentPressureBar;
    public float deltaPressureBar;
    public int bufferAmountMb;
    public int bufferCapacityMb;
    public boolean bufferFull;
    public boolean ventSuppressed;
    public boolean blowoutPrevented;
    @Nullable
    public OilComposition reservoir;

    public void reset(float currentPressureBar, float deltaPressureBar, int bufferAmountMb,
                      int bufferCapacityMb, boolean bufferFull, @Nullable OilComposition reservoir) {
        this.currentPressureBar = currentPressureBar;
        this.deltaPressureBar = deltaPressureBar;
        this.bufferAmountMb = bufferAmountMb;
        this.bufferCapacityMb = bufferCapacityMb;
        this.bufferFull = bufferFull;
        this.ventSuppressed = false;
        this.blowoutPrevented = false;
        this.reservoir = reservoir;
    }
}
