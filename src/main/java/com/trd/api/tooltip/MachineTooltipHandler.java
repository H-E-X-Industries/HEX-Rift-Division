package com.trd.api.tooltip;

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

import java.util.ArrayList;
import java.util.List;

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
            String[] sentences = raw.split("(?<=\\. )");

            for (String sentence : sentences) {
                String trimmed = sentence.trim();
                if (trimmed.isEmpty()) continue;
                event.getToolTip().add(parseColoredSentence(trimmed));
            }
        }
    }

    /**
     * Парсит текст: всё белое, а всё что между | | - золотое. Сам | не отображается.
     * <p>
     * Если подсвеченный сегмент начинается с §-кодов, они применяются вместо золотого:
     * {@code |§cЗажигательная|}. Ведущие коды снимаются из текста, остальные § печатаются как есть.
     * Без кодов поведение прежнее - золото, так что старые описания не меняются.
     */
    private static MutableComponent parseColoredSentence(String text) {
        MutableComponent result = Component.empty();
        StringBuilder buffer = new StringBuilder();
        boolean inHighlight = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '|') {
                if (buffer.length() > 0) {
                    result.append(buildSegment(buffer.toString(), inHighlight));
                    buffer.setLength(0);
                }
                inHighlight = !inHighlight;
            } else {
                buffer.append(c);
            }
        }

        if (buffer.length() > 0) {
            result.append(buildSegment(buffer.toString(), inHighlight));
        }

        return result;
    }

    private static MutableComponent buildSegment(String segment, boolean highlighted) {
        int i = 0;
        List<ChatFormatting> codes = new ArrayList<>(2);

        while (i + 1 < segment.length() && segment.charAt(i) == ChatFormatting.PREFIX_CODE) {
            ChatFormatting code = ChatFormatting.getByCode(segment.charAt(i + 1));
            if (code == null) break;
            codes.add(code);
            i += 2;
        }

        MutableComponent result = Component.literal(segment.substring(i));
        result.withStyle(highlighted ? ChatFormatting.GOLD : ChatFormatting.WHITE);
        if (!codes.isEmpty()) {
            result.withStyle(codes.toArray(new ChatFormatting[0]));
        }
        return result;
    }
}