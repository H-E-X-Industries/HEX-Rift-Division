package com.trd.climate.client;

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

import java.util.Locale;

/**
 * HUD overlay для отображения температуры игрока на экране.
 * Расположен строго по центру между шкалой еды и здоровья.
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
        
        // Не отображать если GUI скрыт, мир не загружен или игрок в режиме наблюдателя
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) {
            return;
        }

        // Получаем текущую температуру из клиентского кэша
        float currentTemp = ClientTemperatureHandler.getCurrentTemp();
        int color = ClientTemperatureHandler.getTemperatureColor();

        // Форматируем температуру с точкой независимо от локали ОС
        String tempText = String.format(Locale.ROOT, "%.1f°C", currentTemp);

        // Расчет позиции по центру экрана между шкалой здоровья (слева) и еды (справа)
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int centerX = screenWidth / 2;

        int textWidth = mc.font.width(tempText);
        int x = centerX - (textWidth / 2);

        // По вертикали: шкала здоровья и еды на высоте screenHeight - 39.
        // Если надета броня (слева) или игрок под водой (справа, пузыри),
        // размещаем на screenHeight - 52, чтобы не налезать на значки.
        // Без брони - компактно на screenHeight - 49 прямо над сердечками и едой.
        int y;
        if (mc.player.getArmorValue() > 0 || mc.player.getAirSupply() < mc.player.getMaxAirSupply()) {
            y = screenHeight - 52;
        } else {
            y = screenHeight - 49;
        }

        // Отрисовываем текст с тенью
        guiGraphics.drawString(mc.font, tempText, x, y, color, true);
    }
}
