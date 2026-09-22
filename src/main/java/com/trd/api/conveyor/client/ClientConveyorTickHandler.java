package com.trd.api.conveyor.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import com.trd.main.MainRegistry;

@net.neoforged.fml.common.EventBusSubscriber(modid = com.trd.main.MainRegistry.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class ClientConveyorTickHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientConveyorManager.tickClient();
    }
}
