package com.trd.client.overlay.hud;

import com.trd.block.basic.industrial.rotation.TachometerBlock;
import com.trd.block.entity.industrial.rotation.TachometerBlockEntity;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Locale;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class TachometerOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Проверяем, что игрок смотрит на блок
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        // Проверяем, что это тахометр
        if (!(state.getBlock() instanceof TachometerBlock)) return;

        // Получаем BlockEntity на клиенте
        if (!(mc.level.getBlockEntity(pos) instanceof TachometerBlockEntity tachometer)) return;

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;

        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();

        // Позиция текста — справа снизу от перекрестия
        int centerX = screenWidth / 2 + 12;
        int centerY = screenHeight / 2 + 4;

        int lineHeight = font.lineHeight + 2;
        int bgColor = 0x80000000; // Полупрозрачный черный фон
        int headerColor = 0xFFFFAA00; // Оранжевый заголовок
        int valueColor = 0xFFFFFFFF; // Белый текст
        int noShaftColor = 0xFFFF5555; // Красный для предупреждения

        if (!tachometer.hasShaft()) {
            // Нет вала — показываем предупреждение
            String noShaft = Component.translatable("hud.trd.tachometer.no_shaft").getString();
            int textWidth = font.width(noShaft);
            if (centerX + textWidth + 4 > screenWidth) {
                centerX = screenWidth / 2 - textWidth - 12;
            }
            guiGraphics.fill(centerX - 4, centerY - 4, centerX + textWidth + 4, centerY + lineHeight + 2, bgColor);
            guiGraphics.drawString(font, noShaft, centerX, centerY, noShaftColor, true);
        } else {
            String header = Component.translatable("hud.trd.tachometer.title").getString();
            String speedText = Component.translatable("hud.trd.tachometer.speed", Math.abs(tachometer.getNetworkSpeed())).getString();
            String torqueText = Component.translatable("hud.trd.tachometer.torque", tachometer.getNetworkConsumedTorque(), tachometer.getNetworkTorque()).getString();

            String inertiaFormat = Component.translatable("hud.trd.tachometer.inertia").getString();
            String inertiaText = formatParam(inertiaFormat, tachometer.getNetworkInertia());

            // Расчет стресса (нагрузки)
            double load = tachometer.getNetworkLoad();
            double stressValue = Math.max(0, (load - 1.0) / 0.25);
            String stressFormat = Component.translatable("hud.trd.tachometer.stress").getString();
            String stressText = formatParam(stressFormat, stressValue * 100.0);

            int stressColor = valueColor;
            if (load >= 1.25) {
                stressColor = noShaftColor; // Красный (критично)
            } else if (load >= 1.0) {
                stressColor = 0xFFFFAA00; // Оранжевый (перегруз)
            }

            // Вычисляем максимальную ширину для фона
            int maxWidth = Math.max(font.width(header),
                    Math.max(font.width(speedText),
                            Math.max(font.width(torqueText),
                                    Math.max(font.width(inertiaText), font.width(stressText)))));

            if (centerX + maxWidth + 8 > screenWidth) {
                centerX = screenWidth / 2 - maxWidth - 12;
            }

            // Фон
            int bgX1 = centerX - 4;
            int bgY1 = centerY - 4;
            int bgX2 = centerX + maxWidth + 8;
            int bgY2 = centerY + lineHeight * 5 + 4;
            guiGraphics.fill(bgX1, bgY1, bgX2, bgY2, bgColor);

            // Заголовок
            guiGraphics.drawString(font, header, centerX, centerY, headerColor, true);

            // Данные
            guiGraphics.drawString(font, speedText, centerX, centerY + lineHeight, valueColor, true);

            int torqueColor = (tachometer.getNetworkConsumedTorque() > tachometer.getNetworkTorque() && tachometer.getNetworkTorque() > 0) ? 0xFFFFAA00 : valueColor;
            guiGraphics.drawString(font, torqueText, centerX, centerY + lineHeight * 2, torqueColor, true);

            guiGraphics.drawString(font, inertiaText, centerX, centerY + lineHeight * 3, valueColor, true);
            guiGraphics.drawString(font, stressText, centerX, centerY + lineHeight * 4, stressColor, true);
        }
    }

    private static String formatParam(String pattern, double value) {
        try {
            return String.format(Locale.ROOT, pattern, value);
        } catch (Exception e) {
            try {
                return String.format(Locale.ROOT, pattern, String.format(Locale.ROOT, "%.2f", value));
            } catch (Exception e2) {
                return pattern + " " + String.format(Locale.ROOT, "%.2f", value);
            }
        }
    }
}
