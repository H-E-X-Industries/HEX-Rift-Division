package com.trd.climate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * HUD overlay для отображения температуры игрока на экране.
 */
@EventBusSubscriber(modid = "trd", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class TemperatureHudOverlay {

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.PLAYER_HEALTH,
            ResourceLocation.fromNamespaceAndPath("trd", "temperature_hud"),
            new LayeredDraw.Layer() {
                @Override
                public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
                    renderTemperatureHud(guiGraphics);
                }
            }
        );
    }

    private static void renderTemperatureHud(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        
        // Не отображать если GUI скрыт или игрок в режиме наблюдателя
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        // Получаем текущую температуру из клиентского кэша
        float currentTemp = ClientTemperatureHandler.getCurrentTemp();
        int color = ClientTemperatureHandler.getTemperatureColor();

        // Форматируем температуру
        String tempText = String.format("%.1f°C", currentTemp);

        // Позиция: по центру между едой и здоровьем
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        // Рассчитываем ширину текста для центрирования
        int textWidth = mc.font.width(tempText);
        int x = (screenWidth / 2) - (textWidth / 2); // Центрируем текст
        int y = screenHeight - 49;                    // Над полоской здоровья

        // Отрисовываем текст с тенью
        guiGraphics.drawString(mc.font, tempText, x, y, color, true);
    }
}
