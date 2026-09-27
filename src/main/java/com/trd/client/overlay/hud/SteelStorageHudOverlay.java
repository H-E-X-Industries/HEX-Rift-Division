package com.trd.client.overlay.hud;

import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.steel_storage.SteelStorageBlockEntity;
import com.trd.multiblock.system.roles.IMultiblockPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
        int baseX = screenWidth / 2 + 12;
        int baseY = screenHeight / 2 + 4;

        float ratio = totalSlots > 0 ? (float) filledSlots / totalSlots : 0;
        int headerColor = ratio < 0.33f ? 0x55FF55
                : (ratio < 0.66f ? 0xFFFF55 : 0xFF5555);

        String header = Component.translatable("hud.trd.storage.header", filledSlots, totalSlots).getString();
        int headerWidth = font.width(header);
        int maxTextWidth = headerWidth;

        List<String> lines = new ArrayList<>();
        if (filledSlots == 0) {
            lines.add(Component.translatable("hud.trd.storage.empty").getString());
        } else {
            int shown = 0;
            for (Map.Entry<String, Integer> entry : items.entrySet()) {
                if (shown >= 8) {
                    lines.add(Component.translatable("hud.trd.storage.more", (items.size() - 8)).getString());
                    break;
                }
                String line = Component.translatable("hud.trd.storage.item", entry.getKey(), entry.getValue()).getString();
                lines.add(line);
                maxTextWidth = Math.max(maxTextWidth, font.width(line));
                shown++;
            }
        }

        int lineHeight = font.lineHeight;
        int paddingX = 4;
        int paddingY = 3;
        int lineSpacing = 1;

        int contentHeight = lines.size() * (lineHeight + lineSpacing) - lineSpacing;
        int totalHeight = lineHeight + 2 + contentHeight;
        int bgWidth = maxTextWidth + paddingX * 2;
        int bgHeight = totalHeight + paddingY * 2;

        if (baseX + bgWidth > screenWidth) {
            baseX = screenWidth / 2 - bgWidth - 12;
        }

        int bgX = baseX - paddingX;
        int bgY = baseY - paddingY;

        graphics.fill(bgX, bgY, bgX + bgWidth, bgY + bgHeight, 0x90000000);

        int currentY = baseY;
        graphics.drawString(font, header, baseX, currentY, headerColor, true);
        currentY += lineHeight + 2;

        if (filledSlots > 0) {
            graphics.fill(bgX + 2, currentY - 1, bgX + bgWidth - 2, currentY, 0x60FFFFFF);
        }

        for (String line : lines) {
            int color = line.startsWith("•") ? 0xFFCCCCCC : 0xFF888888;
            if (line.equals("Пусто") || line.equals("Empty")) color = 0xFF888888;
            graphics.drawString(font, line, baseX, currentY, color, true);
            currentY += lineHeight + lineSpacing;
        }
    }
}
