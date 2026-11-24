package com.trd.climate.room;

import com.trd.main.MainRegistry;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Тики термического движка пассивных источников.
 * <p>
 * Два шага: покадровое вероятностное таяние льда и секундный пересмотр
 * всех записей (расход/восстановление запаса, превращение в depletedBlock).
 * Шаги привязаны к тику уровня (а не к контроллеру комнаты), поэтому корректно
 * обрабатываются перенос блока из комнаты, снятие контроллера и замена блока.
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
        ThermalStateStore store = ThermalStateStore.get(level);
        // Покадровое таяние льда (шанс и кулдаун зависят от температуры комнаты)
        store.tickMelting(level);
        // Процессы воды: заморозка ниже 0°C, кипение выше 100°C
        store.tickWaterPhysics(level);
        // Секундный шаг расхода/восстановления и превращения блоков
        if (level.getGameTime() % 20L == 0L) {
            store.tick(level);
        }
    }
}
