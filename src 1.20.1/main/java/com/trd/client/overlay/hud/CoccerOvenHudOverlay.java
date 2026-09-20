package com.trd.client.overlay.hud;

import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.coccer.CoccerOvenBlockEntity;
import com.trd.multiblock.system.IMultiblockPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CoccerOvenHudOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (be == null) return;

        CoccerOvenBlockEntity oven = null;
        if (be instanceof IMultiblockPart part) {
            BlockPos controllerPos = part.getControllerPos();
            if (controllerPos != null) {
                BlockEntity controller = mc.level.getBlockEntity(controllerPos);
                if (controller instanceof CoccerOvenBlockEntity o) {
                    oven = o;
                }
            }
        } else if (be instanceof CoccerOvenBlockEntity o) {
            oven = o;
        }

        if (oven == null) return;

        float temp = oven.getTemperature();
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        float maxTemp = CoccerOvenBlockEntity.MAX_TEMP;
        float tempPercent = temp / maxTemp;
        int color = getSmoothTemperatureColor(tempPercent);

        String tempText = String.format("%.0f / %.0f °C", temp, maxTemp);
        int textWidth = font.width(tempText);

        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();
        int x = screenWidth / 2 + 12;
        int y = screenHeight / 2 + 4;

        if (x + textWidth + 4 > screenWidth) {
            x = screenWidth / 2 - textWidth - 12;
        }

        graphics.fill(x - 3, y - 2, x + textWidth + 3, y + font.lineHeight + 2, 0x90000000);
        graphics.drawString(font, tempText, x, y, color, true);

        if (oven.isProcessing()) {
            String status = Component.translatable("hud.trd.temperature.smelting").getString();
            int statusWidth = font.width(status);
            int statusY = y + font.lineHeight + 3;

            if (statusWidth > textWidth) {
                graphics.fill(x - 3, y - 2, x + statusWidth + 3, statusY + font.lineHeight + 2, 0x90000000);
            }
            graphics.drawString(font, status, x, statusY, 0xFFAA00, true);
        } else if (temp > 0) {
            String status = Component.translatable("hud.trd.temperature.heating").getString();
            int statusWidth = font.width(status);
            int statusY = y + font.lineHeight + 3;

            if (statusWidth > textWidth) {
                graphics.fill(x - 3, y - 2, x + statusWidth + 3, statusY + font.lineHeight + 2, 0x90000000);
            }
            graphics.drawString(font, status, x, statusY, 0xFFAA00, true);
        }
    }

    private static int getSmoothTemperatureColor(float percent) {
        percent = Math.max(0.0f, Math.min(1.0f, percent));
        int grey = 0xAAAAAA, orange = 0xFFAA00, red = 0xFF2222;
        if (percent <= 0.3f) return lerpColor(grey, orange, percent / 0.3f);
        else if (percent <= 0.7f) return lerpColor(orange, red, (percent - 0.3f) / 0.4f);
        else return red;
    }

    private static int lerpColor(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }
}