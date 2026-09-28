package com.trd.item.tools;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class InfiniteFluidBarrelItem extends Item {

    public InfiniteFluidBarrelItem(Properties properties) {
        super(properties.stacksTo(1)); // Не стакается
    }


}
