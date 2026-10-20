package com.trd.block.basic.industrial.lighting;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * Вугільна лампа розжарення (Carbon Filament Lamp).
 * Рівень світла: 8.
 * Споживання: 12 од./такт.
 * Знос: 0.04f на randomTick (швидкий знос).
 */
public class CarbonLampBlock extends ElectricLampBlock {
    public CarbonLampBlock(Properties properties,
                           Supplier<? extends Item> lampItemSupplier,
                           Supplier<? extends Block> socketBlockSupplier) {
        super(properties.lightLevel(state -> state.getValue(LIT) && !state.getValue(BROKEN) ? 8 : 0),
                8, lampItemSupplier, socketBlockSupplier);
    }

    @Override
    public long getConsumptionPerTick() {
        return 12L;
    }

    @Override
    public float getWearPerRandomTick() {
        return 0.04f;
    }
}
