package com.trd.climate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Визуальные эффекты температуры:
 * - Оранжевая виньетка при перегреве
 * - Тряска камеры при замерзании (уже есть в ванильном freeze эффекте)
 */
@EventBusSubscriber(modid = "trd", value = Dist.CLIENT)
public class TemperatureVisualEffects {

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        float temp = ClientTemperatureHandler.getCurrentTemp();
        
        // Оранжевая виньетка при сильном перегреве (> 32°C)
        if (temp > 32.0f) {
            renderHeatVignette(event.getGuiGraphics(), temp);
        }
    }

    /**
     * Рендерит оранжевую виньетку перегрева (только по краям экрана).
     * Интенсивность зависит от температуры.
     */
    private static void renderHeatVignette(GuiGraphics guiGraphics, float temp) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        // Рассчитываем интенсивность
        // 32°C → 0.0 (начало эффекта)
        // 40°C → 0.2 (средняя интенсивность)
        // 50°C+ → 0.25 (максимум 25% прозрачности)
        float intensity = Math.min((temp - 32.0f) / 72.0f, 0.25f); // Делитель увеличен до 72
        
        if (intensity <= 0.0f) return;
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // Оранжево-красный цвет (RGB: 255, 80, 0)
        int baseAlpha = (int) (intensity * 255); // Максимум 25% от 255 = ~64
        
        // Размер виньетки (отступ от краев)
        int vignetteSize = Math.min(screenWidth, screenHeight) / 4; // Меньше размер
        
        // Рисуем виньетку с градиентом от краев к центру (4 стороны)
        for (int i = 0; i < vignetteSize; i++) {
            float fade = (float) i / vignetteSize;
            int alpha = (int) (baseAlpha * (1.0f - fade));
            int color = (alpha << 24) | (255 << 16) | (80 << 8) | 0;
            
            // Верхняя полоса
            guiGraphics.fill(0, i, screenWidth, i + 1, color);
            // Нижняя полоса
            guiGraphics.fill(0, screenHeight - i - 1, screenWidth, screenHeight - i, color);
            // Левая полоса
            guiGraphics.fill(i, 0, i + 1, screenHeight, color);
            // Правая полоса
            guiGraphics.fill(screenWidth - i - 1, 0, screenWidth - i, screenHeight, color);
        }
        
        RenderSystem.disableBlend();
    }
}
