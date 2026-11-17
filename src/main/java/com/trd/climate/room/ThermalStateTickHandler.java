package com.trd.climate.room;

import com.trd.main.MainRegistry;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Один секундный шаг термического движка пассивных источников.
 * <p>
 * Шаг привязан к тику уровня (а не к контроллеру комнаты), поэтому корректно
 * обрабатываются и перенос блока из комнаты, и снятие контроллера, и замена блока.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public final class ThermalStateTickHandler {

    private ThermalStateTickHandler() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % 20L != 0L) {
            return;
        }
        ThermalStateStore.get(level).tick(level);
    }
}
