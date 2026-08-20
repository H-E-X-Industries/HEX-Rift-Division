package com.trd.api.tooltip;

import com.trd.api.tooltip.MachineTooltipRegistry;
import com.trd.main.MainRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class MachineTooltipHandler {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();

        if (!MachineTooltipRegistry.has(item)) return;

        if (!Screen.hasShiftDown()) {
            event.getToolTip().add(
                    Component.translatable("tooltip.trd.machine.hold_shift")
                            .withStyle(ChatFormatting.GRAY)
            );
        } else {
            String descKey = MachineTooltipRegistry.getDescKey(item);
            if (descKey == null) return;

            String raw = Component.translatable(descKey).getString();
            // Разбиваем на предложения (после ". "), сохраняя точки в тексте
            String[] sentences = raw.split("(?<=\\. )");

            for (String sentence : sentences) {
                String trimmed = sentence.trim();
                if (trimmed.isEmpty()) continue;
                event.getToolTip().add(parseColoredSentence(trimmed));
            }
        }
    }

    /** Парсит текст: всё белое, а всё что внутри () — золотое */
    private static MutableComponent parseColoredSentence(String text) {
        MutableComponent result = Component.empty();
        StringBuilder buffer = new StringBuilder();
        boolean inBrackets = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '(') {
                if (buffer.length() > 0) {
                    result.append(Component.literal(buffer.toString()).withStyle(ChatFormatting.WHITE));
                    buffer.setLength(0);
                }
                inBrackets = true;
                buffer.append(c);
            } else if (c == ')') {
                buffer.append(c);
                result.append(Component.literal(buffer.toString()).withStyle(ChatFormatting.GOLD));
                buffer.setLength(0);
                inBrackets = false;
            } else {
                buffer.append(c);
            }
        }

        if (buffer.length() > 0) {
            result.append(Component.literal(buffer.toString()).withStyle(
                    inBrackets ? ChatFormatting.GOLD : ChatFormatting.WHITE
            ));
        }

        return result;
    }
}