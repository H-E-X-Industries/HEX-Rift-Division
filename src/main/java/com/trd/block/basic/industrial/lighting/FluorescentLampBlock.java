package com.trd.block.basic.industrial.lighting;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * Люмінесцентна лампа (Fluorescent Lamp).
 * Рівень світла: 13.
 * Споживання: 3 од./такт.
 * Знос: 0.003f на randomTick (довговічна).
 */
public class FluorescentLampBlock extends ElectricLampBlock {
    public FluorescentLampBlock(Properties properties,
                                Supplier<? extends Item> lampItemSupplier,
                                Supplier<? extends Block> socketBlockSupplier) {
        super(properties.lightLevel(state -> state.getValue(LIT) && !state.getValue(BROKEN) ? 13 : 0),
                13, lampItemSupplier, socketBlockSupplier);
    }

    @Override
    public long getConsumptionPerTick() {
        return 3L;
    }

    @Override
    public float getWearPerRandomTick() {
        return 0.003f;
    }
}
