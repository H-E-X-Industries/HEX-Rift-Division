package com.trd.item.climate;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Ртутный нательный термометр.
 * <p>
 * Пока находится в хотбаре (в т.ч. в главной руке) или в offhand — на левом крае экрана
 * отображается HUD со столбом ртути (см. {@link com.trd.climate.client.MercuryThermometerHud}).
 * <p>
 * Ртуть внутри стекла: при выкидывании на землю термометр разбивается и исчезает
 * (см. {@link com.trd.event.MercuryThermometerBreakHandler}).
 */
public class MercuryThermometerItem extends Item {

    public MercuryThermometerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(@Nullable ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.trd.thermometer.mercury.danger").withStyle(ChatFormatting.RED));
    }
}
