package com.trd.item.tools;

import net.minecraft.world.item.Item;

public class InfiniteFluidBarrelItem extends Item {

    public InfiniteFluidBarrelItem(Properties properties) {
        super(properties.stacksTo(1)); // Не стакается
    }

}
