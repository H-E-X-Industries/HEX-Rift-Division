package com.trd.block.basic.industrial.lighting;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * Вольфрамова лампа розжарення (Tungsten Lamp).
 * Рівень світла: 12.
 * Споживання: 6 од./такт.
 * Знос: 0.01f на randomTick (середній ресурс).
 */
public class TungstenLampBlock extends ElectricLampBlock {
    public TungstenLampBlock(Properties properties,
                             Supplier<? extends Item> lampItemSupplier,
                             Supplier<? extends Block> socketBlockSupplier) {
        super(properties.lightLevel(state -> state.getValue(LIT) && !state.getValue(BROKEN) ? 12 : 0),
                12, lampItemSupplier, socketBlockSupplier);
    }

    @Override
    public long getConsumptionPerTick() {
        return 6L;
    }

    @Override
    public float getWearPerRandomTick() {
        return 0.01f;
    }
}
