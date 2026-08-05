package com.trd.item.tools;

import net.minecraft.world.item.Item;

public class BladeItem extends Item {
    public BladeItem(Properties properties) {
        super(properties.durability(256)); // stacksTo(1) убрано — оно конфликтует с durability
    }
}