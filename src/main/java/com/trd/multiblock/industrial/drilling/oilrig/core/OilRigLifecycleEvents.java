package com.trd.multiblock.industrial.drilling.oilrig.core;

import com.trd.main.MainRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Очистка кэша топологии OilRigManager при выгрузке измерения или остановке сервера.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public final class OilRigLifecycleEvents {

    private OilRigLifecycleEvents() {}

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide() && event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            OilRigManager.unloadLevel(serverLevel.dimension());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        OilRigManager.clearAll();
    }
}
