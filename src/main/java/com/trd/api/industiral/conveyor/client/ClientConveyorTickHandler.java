package com.trd.api.industiral.conveyor.client;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

@net.neoforged.fml.common.EventBusSubscriber(modid = com.trd.main.MainRegistry.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class ClientConveyorTickHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientConveyorManager.tickClient();
    }
}
