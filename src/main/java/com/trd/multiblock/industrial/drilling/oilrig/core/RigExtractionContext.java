package com.trd.multiblock.industrial.drilling.oilrig.core;

import com.trd.api.nature.ore.oil.OilComposition;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Контекст цикла добычи флюида, передаваемый в хуки IWellEnhancerModule.
 */
public final class RigExtractionContext {
    public int baseAmountMb;
    public float multiplier;
    public float depletionChance;
    public boolean allowExtraction;
    @Nullable
    public OilComposition reservoir;
    @Nullable
    public BlockPos pocketPos;

    public void reset(int baseAmountMb, float depletionChance,
                      @Nullable OilComposition reservoir, @Nullable BlockPos pocketPos) {
        this.baseAmountMb = baseAmountMb;
        this.multiplier = 1.0f;
        this.depletionChance = depletionChance;
        this.allowExtraction = true;
        this.reservoir = reservoir;
        this.pocketPos = pocketPos;
    }

    public int finalAmountMb() {
        return allowExtraction ? Math.max(0, Math.round(baseAmountMb * multiplier)) : 0;
    }
}
