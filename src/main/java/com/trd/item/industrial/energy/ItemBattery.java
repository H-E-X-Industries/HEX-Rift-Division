package com.trd.item.industrial.energy;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

public class ItemBattery extends ModBatteryItem {
    public ItemBattery(Properties properties, int capacity, int maxReceive, int maxExtract) {
        super(properties, capacity, maxReceive, maxExtract);
    }
}
