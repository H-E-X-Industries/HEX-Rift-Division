package com.trd.api.hive;

import com.trd.main.MainRegistry;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class HiveNetworkTickHandler {

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getLevel() instanceof ServerLevel serverLevel) {
            HiveNetworkManager manager = HiveNetworkManager.get(serverLevel);
            if (manager != null) {
                manager.tick(serverLevel);
            }
        }
    }
}
