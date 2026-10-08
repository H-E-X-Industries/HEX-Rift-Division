package com.trd.api.fuel;

import com.trd.main.MainRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class FuelTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        int burnTime = ModFuels.getBurnTime(stack);

        if (burnTime > 0) {
            String efficiency = ModFuels.getEfficiencyComparedToCoal(burnTime);
            event.getToolTip().add(
                    Component.literal("⛽ " + efficiency)
                            .withStyle(ChatFormatting.GOLD)
            );
        }
    }
}
