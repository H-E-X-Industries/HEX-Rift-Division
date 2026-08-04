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

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.trd.infinite_barrel.slot"));
        tooltip.add(Component.translatable("tooltip.trd.infinite_barrel.tank"));
        tooltip.add(Component.translatable("tooltip.trd.infinite_barrel.fill"));
        tooltip.add(Component.translatable("tooltip.trd.infinite_barrel.source"));
    }
}
