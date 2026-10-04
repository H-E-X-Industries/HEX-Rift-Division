package com.trd.client.overlay.hud;

import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.steel_storage.SteelStorageBlockEntity;
import com.trd.multiblock.system.roles.IMultiblockPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class SteelStorageHudOverlay {

    private static final String ELLIPSIS = "…";
    private static final int MAX_TEXT_WIDTH = 180;
    private static final int MAX_LINES = 8;

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (be == null) return;

        SteelStorageBlockEntity storage = null;
        if (be instanceof IMultiblockPart part) {
            BlockPos controllerPos = part.getControllerPos();
            if (controllerPos != null) {
                BlockEntity controller = mc.level.getBlockEntity(controllerPos);
                if (controller instanceof SteelStorageBlockEntity s) {
                    storage = s;
                }
            }
        } else if (be instanceof SteelStorageBlockEntity s) {
            storage = s;
        }

        if (storage == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;

        ItemStackHandler inventory = storage.getInventory();
        int totalSlots = inventory.getSlots();
        int filledSlots = 0;
        Map<String, Integer> items = new LinkedHashMap<>();

        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                filledSlots++;
                String name = stack.getHoverName().getString();
                items.merge(name, stack.getCount(), Integer::sum);
            }
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        float ratio = totalSlots > 0 ? (float) filledSlots / totalSlots : 0;
        int headerColor = ratio < 0.33f ? 0x55FF55
                : (ratio < 0.66f ? 0xFFFF55 : 0xFF5555);

        int lineHeight = font.lineHeight;
        int paddingX = 4;
        int paddingY = 3;
        int lineSpacing = 1;

        // Панель не должна вылезать за экран, поэтому текст обрезается по ширине
        int maxTextWidth = Math.max(40, Math.min(MAX_TEXT_WIDTH, screenWidth - (paddingX + 8) * 2));

        String header = fit(font, Component.translatable("hud.trd.storage.header", filledSlots, totalSlots).getString(), maxTextWidth);

        List<String> lines = new ArrayList<>();
        if (filledSlots == 0) {
            lines.add(fit(font, Component.translatable("hud.trd.storage.empty").getString(), maxTextWidth));
        } else {
            int shown = 0;
            for (Map.Entry<String, Integer> entry : items.entrySet()) {
                if (shown >= MAX_LINES) {
                    lines.add(fit(font, Component.translatable("hud.trd.storage.more", (items.size() - MAX_LINES)).getString(), maxTextWidth));
                    break;
                }
                lines.add(formatItemLine(font, entry.getKey(), entry.getValue(), maxTextWidth));
                shown++;
            }
        }

        int textWidth = font.width(header);
        for (String line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }

        int contentHeight = lines.size() * (lineHeight + lineSpacing) - lineSpacing;
        int totalHeight = lineHeight + 2 + contentHeight;
        int bgWidth = textWidth + paddingX * 2;
        int bgHeight = totalHeight + paddingY * 2;

        int baseX = screenWidth / 2 + 12;
        int baseY = screenHeight / 2 + 4;

        if (baseX + bgWidth > screenWidth - 2) {
            baseX = screenWidth / 2 - bgWidth - 12;
        }
        baseX = Mth.clamp(baseX, 2, Math.max(2, screenWidth - 2 - bgWidth));

        int bgY = baseY - paddingY;
        if (bgY + bgHeight > screenHeight - 2) {
            bgY = screenHeight - 2 - bgHeight;
        }
        bgY = Math.max(2, bgY);
        baseY = bgY + paddingY;

        graphics.fill(baseX - paddingX, bgY, baseX - paddingX + bgWidth, bgY + bgHeight, 0x90000000);

        int currentY = baseY;
        graphics.drawString(font, header, baseX, currentY, headerColor, true);
        currentY += lineHeight + 2;

        if (filledSlots > 0) {
            graphics.fill(baseX - paddingX + 2, currentY - 1, baseX - paddingX + bgWidth - 2, currentY, 0x60FFFFFF);
        }

        for (String line : lines) {
            int color = line.startsWith("•") ? 0xFFCCCCCC : 0xFF888888;
            if (line.equals("Пусто") || line.equals("Empty")) color = 0xFF888888;
            graphics.drawString(font, line, baseX, currentY, color, true);
            currentY += lineHeight + lineSpacing;
        }
    }

    /** Обрезает строку по ширине, добавляя многоточие. */
    private static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ELLIPSIS))) + ELLIPSIS;
    }

    /** Формирует строку "предмет xколичество", обрезая только слишком длинное название предмета. */
    private static String formatItemLine(Font font, String name, int count, int maxWidth) {
        String line = Component.translatable("hud.trd.storage.item", name, count).getString();
        if (font.width(line) <= maxWidth) {
            return line;
        }
        // Ширина всей строки, кроме названия предмета (считаем по однобуквенному имени)
        int fixedWidth = font.width(Component.translatable("hud.trd.storage.item", "W", count).getString()) - font.width("W");
        int nameWidth = Math.max(0, maxWidth - fixedWidth - font.width(ELLIPSIS));
        line = Component.translatable("hud.trd.storage.item", font.plainSubstrByWidth(name, nameWidth) + ELLIPSIS, count).getString();
        return font.width(line) <= maxWidth ? line : fit(font, line, maxWidth);
    }
}
