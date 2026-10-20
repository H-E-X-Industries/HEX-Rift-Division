package com.trd.block.basic.industrial.lighting;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * Світлодіодна лампа (LED Lamp).
 * Рівень світла: 15.
 * Споживання: 1 од./такт (мінімальне споживання).
 * Знос: 0.0f (без фізичного зносу).
 */
public class LedLampBlock extends ElectricLampBlock {
    public LedLampBlock(Properties properties,
                        Supplier<? extends Item> lampItemSupplier,
                        Supplier<? extends Block> socketBlockSupplier) {
        super(properties.lightLevel(state -> state.getValue(LIT) && !state.getValue(BROKEN) ? 15 : 0),
                15, lampItemSupplier, socketBlockSupplier);
    }

    @Override
    public long getConsumptionPerTick() {
        return 1L;
    }

    @Override
    public float getWearPerRandomTick() {
        return 0.0f;
    }
}
