package com.trd.item.industrial.energy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.Item;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class ItemCreativeBattery extends ModBatteryItem {

    public ItemCreativeBattery(Properties pProperties) {
        super(pProperties.rarity(Rarity.EPIC).stacksTo(1), Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, TooltipContext context, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.trd.creative_battery_desc").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.trd.creative_battery_flavor").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    @Override
    public boolean isBarVisible(@Nonnull ItemStack stack) {
        return false;
    }

    @Override
    public int getBarWidth(@Nonnull ItemStack stack) {
        return 13;
    }

    @Override
    public int getBarColor(@Nonnull ItemStack stack) {
        return 0xFF00FF;
    }
}
