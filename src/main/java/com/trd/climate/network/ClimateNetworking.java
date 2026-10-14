package com.trd.climate.network;

import com.trd.main.MainRegistry;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Регистрация сетевых пакетов для климатической системы.
 */
public class ClimateNetworking {
    
    public static void register() {
        // Регистрация выполняется через событие в ModEventBus
        MainRegistry.LOGGER.info("Climate networking initialized");
    }

    /**
     * Регистрирует сетевые пакеты в событии RegisterPayloadHandlersEvent.
     * Этот метод должен быть вызван из конструктора мода через modEventBus.addListener().
     */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        
        // Регистрация пакета синхронизации температуры (Сервер -> Клиент)
        registrar.playToClient(
            SyncTemperaturePayload.TYPE,
            SyncTemperaturePayload.STREAM_CODEC,
            SyncTemperaturePayload::handle
        );
    }
}
