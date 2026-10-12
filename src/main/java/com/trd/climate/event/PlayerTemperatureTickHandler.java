package com.trd.climate.event;

import com.trd.climate.attachment.ModAttachments;
import com.trd.climate.calculation.TemperatureCalculator;
import com.trd.climate.data.PlayerTemperatureData;
import com.trd.climate.network.SyncTemperaturePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Серверный обработчик события тика игрока.
 * Обновляет температуру игрока раз в секунду (20 тиков) и отправляет данные клиенту.
 */
@EventBusSubscriber(modid = "trd")
public class PlayerTemperatureTickHandler {

    private static final float ADAPTATION_SPEED = 0.025f; // Скорость адаптации температуры тела

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        
        // Только на сервере
        if (player.level().isClientSide() || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        // Обновление раз в секунду (20 тиков), с уникальным сдвигом для каждого игрока
        if ((player.tickCount + player.getId()) % 20 != 0) {
            return;
        }

        // Получаем данные температуры игрока
        PlayerTemperatureData data = player.getData(ModAttachments.PLAYER_TEMPERATURE);
        
        // Рассчитываем целевую температуру окружающей среды
        float targetEnv = TemperatureCalculator.calculateTargetEnvTemp(player, level);
        data.setTargetTemp(targetEnv);

        // Применяем тепловую инерцию (плавное изменение температуры тела)
        float current = data.getCurrentTemp();
        float newCurrent = TemperatureCalculator.applyThermalInertia(current, targetEnv, ADAPTATION_SPEED);
        data.setCurrentTemp(newCurrent);

        // Отправляем синхронизационный пакет клиенту
        PacketDistributor.sendToPlayer(
            (ServerPlayer) player, 
            new SyncTemperaturePayload(newCurrent, targetEnv)
        );
    }
}
