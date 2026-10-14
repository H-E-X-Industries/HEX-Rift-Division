package com.trd.climate.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Визуальные эффекты температуры на клиенте:
 * - Управление замерзанием игрока (ticksFrozen) на стороне клиента:
 *   1. При температуре ниже 0°C сердечки здоровья становятся морозными (Gui.HeartType.FROZEN)
 *      со снежинками и текстурой льда, как при полном замерзании.
 *   2. По бокам экрана отображается ванильная морозная виньетка рыхлого снега (powder_snow_outline).
 *   3. Включается характерное покачивание и дрожание персонажа/камеры от холода.
 * - При переохлаждении (0°C .. 10°C) виньетка плавно нарастает по мере приближения к 0°C.
 * - При комфортной температуре (> 10°C) игрок естественным образом оттаивает.
 */
@EventBusSubscriber(modid = "trd", value = Dist.CLIENT)
public class TemperatureVisualEffects {

    @SubscribeEvent
    public static void onClientPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Minecraft mc = Minecraft.getInstance();

        // Проверяем, что событие выполняется на клиенте для локального игрока
        if (!player.level().isClientSide() || player != mc.player) {
            return;
        }

        // В креативе или режиме наблюдателя эффекты холода отключены
        if (player.isCreative() || player.isSpectator()) {
            if (player.getTicksFrozen() > 0) {
                player.setTicksFrozen(0);
            }
            return;
        }

        float temp = ClientTemperatureHandler.getCurrentTemp();
        int maxFreeze = player.getTicksRequiredToFreeze(); // Ванильные 140 тиков

        if (temp < 0.0f) {
            // Обледенение (< 0°C):
            // Поддерживаем максимальный уровень заморозки ticksFrozen = 140 каждый клиентский такт.
            // Это предотвращает ванильный сброс в LocalPlayer.baseTick() и заставляет ванильный рендерер:
            // 1. Сделать сердечки морозными (player.isFullyFrozen() == true -> HeartType.FROZEN)
            // 2. Отобразить виньетку рыхлого снега по бокам экрана (player.getPercentFrozen() == 1.0F)
            // 3. Запустить тряску персонажа и прицела от озноба
            player.setTicksFrozen(maxFreeze);
        } else if (temp < 10.0f) {
            // Сильное переохлаждение (0°C .. 10°C):
            // Плавное появление морозной виньетки рыхлого снега по бокам экрана
            float coldProgress = (10.0f - temp) / 10.0f; // 0.0 при 10°C -> 1.0 при 0°C
            int targetFreeze = (int) (coldProgress * (maxFreeze - 1)); // До 139 тиков (сердечки замерзают при < 0°C)
            int currentFreeze = player.getTicksFrozen();

            if (currentFreeze < targetFreeze) {
                player.setTicksFrozen(Math.min(targetFreeze, currentFreeze + 3));
            } else if (currentFreeze > targetFreeze && !player.isInPowderSnow) {
                player.setTicksFrozen(Math.max(targetFreeze, currentFreeze - 1));
            }
        } else {
            // Зона комфорта и тепла (> 10°C):
            // Если игрок не стоит внутри блока рыхлого снега, плавно убираем заморозку
            if (!player.isInPowderSnow && player.getTicksFrozen() > 0) {
                player.setTicksFrozen(Math.max(0, player.getTicksFrozen() - 2));
            }
        }
    }
}
