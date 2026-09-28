package com.trd.client;

import com.trd.api.tooltip.ExplosionTooltipRegistry;
import com.trd.main.MainRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class ExplosionTooltipHandler {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem blockItem)) return;

        Block block = blockItem.getBlock();
        if (!ExplosionTooltipRegistry.contains(block)) return;

        float resistance = block.getExplosionResistance();
        // Красиво форматируем: без .0 если целое
        String formatted = (resistance == (int) resistance)
                ? String.valueOf((int) resistance)
                : String.format("%.1f", resistance);

        event.getToolTip().add(
                Component.translatable("tooltip.trd.explosion_resistance", formatted)
                        .withStyle(ChatFormatting.GOLD)
        );
    }
}
