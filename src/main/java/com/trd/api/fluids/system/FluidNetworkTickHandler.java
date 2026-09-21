package com.trd.api.fluids.system;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "trd")
public class FluidNetworkTickHandler {

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getLevel() instanceof ServerLevel serverLevel) {

            FluidNetworkManager manager = FluidNetworkManager.get(serverLevel);

            // 1. Даем сетям тик (для перекачки жидкостей)
            manager.tick();

            // 2. Отладка: выводим в консоль статус раз в 3 секунды (60 тиков)
            if (serverLevel.getGameTime() % 60 == 0) {
                manager.debugLog();
            }
        }
    }
}