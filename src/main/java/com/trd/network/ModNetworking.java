package com.trd.network;

import com.trd.main.MainRegistry;
import com.trd.network.packet.energy.PacketSyncEnergy;
import com.trd.network.packet.energy.SyncMotorRpmPacket;
import com.trd.network.packet.energy.UpdateBatteryC2SPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModNetworking {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MainRegistry.MOD_ID).versioned("1.0");

        registrar.playToClient(
            PacketSyncEnergy.TYPE,
            PacketSyncEnergy.STREAM_CODEC,
            PacketSyncEnergy::handle
        );

        registrar.playToServer(
            UpdateBatteryC2SPacket.TYPE,
            UpdateBatteryC2SPacket.STREAM_CODEC,
            UpdateBatteryC2SPacket::handle
        );

        registrar.playToServer(
            SyncMotorRpmPacket.TYPE,
            SyncMotorRpmPacket.STREAM_CODEC,
            SyncMotorRpmPacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.fluids.SelectFluidPacket.TYPE,
            com.trd.network.packet.fluids.SelectFluidPacket.STREAM_CODEC,
            com.trd.network.packet.fluids.SelectFluidPacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.fluids.ToggleFavoriteFluidPacket.TYPE,
            com.trd.network.packet.fluids.ToggleFavoriteFluidPacket.STREAM_CODEC,
            com.trd.network.packet.fluids.ToggleFavoriteFluidPacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.fluids.ClearFluidHistoryPacket.TYPE,
            com.trd.network.packet.fluids.ClearFluidHistoryPacket.STREAM_CODEC,
            com.trd.network.packet.fluids.ClearFluidHistoryPacket::handle
        );
    }
}
