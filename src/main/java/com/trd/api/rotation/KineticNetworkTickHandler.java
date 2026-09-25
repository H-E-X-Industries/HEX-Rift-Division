package com.trd.api.rotation;

import com.trd.main.MainRegistry;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public class KineticNetworkTickHandler {

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            KineticNetworkManager.get(serverLevel).tickAllNetworks();
        }
    }
}
