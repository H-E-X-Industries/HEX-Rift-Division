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

        registrar.playToServer(
            com.trd.network.packet.fluids.UpdateBarrelModeC2SPacket.TYPE,
            com.trd.network.packet.fluids.UpdateBarrelModeC2SPacket.STREAM_CODEC,
            com.trd.network.packet.fluids.UpdateBarrelModeC2SPacket::handle
        );

        registrar.playToClient(
            com.trd.network.packet.conveyor.SyncConveyorNetworkPacket.TYPE,
            com.trd.network.packet.conveyor.SyncConveyorNetworkPacket.STREAM_CODEC,
            com.trd.network.packet.conveyor.SyncConveyorNetworkPacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.conveyor.UpdateSortirovshikFilterC2SPacket.TYPE,
            com.trd.network.packet.conveyor.UpdateSortirovshikFilterC2SPacket.STREAM_CODEC,
            com.trd.network.packet.conveyor.UpdateSortirovshikFilterC2SPacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.conveyor.UpdateSortirovshikModeC2SPacket.TYPE,
            com.trd.network.packet.conveyor.UpdateSortirovshikModeC2SPacket.STREAM_CODEC,
            com.trd.network.packet.conveyor.UpdateSortirovshikModeC2SPacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.rotation.ScrollHandCrankPacket.TYPE,
            com.trd.network.packet.rotation.ScrollHandCrankPacket.STREAM_CODEC,
            com.trd.network.packet.rotation.ScrollHandCrankPacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.chemistry.ClearChemicalRecipePacket.TYPE,
            com.trd.network.packet.chemistry.ClearChemicalRecipePacket.STREAM_CODEC,
            com.trd.network.packet.chemistry.ClearChemicalRecipePacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.chemistry.SelectChemicalRecipePacket.TYPE,
            com.trd.network.packet.chemistry.SelectChemicalRecipePacket.STREAM_CODEC,
            com.trd.network.packet.chemistry.SelectChemicalRecipePacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.chemistry.UpdatePortModePacket.TYPE,
            com.trd.network.packet.chemistry.UpdatePortModePacket.STREAM_CODEC,
            com.trd.network.packet.chemistry.UpdatePortModePacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.rotation.SelectStanokRecipePacket.TYPE,
            com.trd.network.packet.rotation.SelectStanokRecipePacket.STREAM_CODEC,
            com.trd.network.packet.rotation.SelectStanokRecipePacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.rotation.ClearStanokRecipePacket.TYPE,
            com.trd.network.packet.rotation.ClearStanokRecipePacket.STREAM_CODEC,
            com.trd.network.packet.rotation.ClearStanokRecipePacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.guns.PacketShoot.TYPE,
            com.trd.network.packet.guns.PacketShoot.STREAM_CODEC,
            com.trd.network.packet.guns.PacketShoot::handle
        );

        registrar.playToServer(
            com.trd.network.packet.guns.PacketReloadGun.TYPE,
            com.trd.network.packet.guns.PacketReloadGun.STREAM_CODEC,
            com.trd.network.packet.guns.PacketReloadGun::handle
        );

        registrar.playToServer(
            com.trd.network.packet.guns.PacketUnloadGun.TYPE,
            com.trd.network.packet.guns.PacketUnloadGun.STREAM_CODEC,
            com.trd.network.packet.guns.PacketUnloadGun::handle
        );

        registrar.playToClient(
            com.trd.network.packet.explosion.SyncCraterPacket.TYPE,
            com.trd.network.packet.explosion.SyncCraterPacket.STREAM_CODEC,
            com.trd.network.packet.explosion.SyncCraterPacket::handle
        );

        registrar.playToClient(
            com.trd.network.packet.explosion.SyncCraterTintsPacket.TYPE,
            com.trd.network.packet.explosion.SyncCraterTintsPacket.STREAM_CODEC,
            com.trd.network.packet.explosion.SyncCraterTintsPacket::handle
        );

        registrar.playToClient(
            com.trd.network.packet.explosion.SpawnExplosionParticlesPacket.TYPE,
            com.trd.network.packet.explosion.SpawnExplosionParticlesPacket.STREAM_CODEC,
            com.trd.network.packet.explosion.SpawnExplosionParticlesPacket::handle
        );

        // === ТУРЕЛИ ===

        registrar.playToClient(
            com.trd.network.packet.turrets.PacketChipFeedback.TYPE,
            com.trd.network.packet.turrets.PacketChipFeedback.STREAM_CODEC,
            com.trd.network.packet.turrets.PacketChipFeedback::handle
        );

        registrar.playToServer(
            com.trd.network.packet.turrets.PacketToggleTurret.TYPE,
            com.trd.network.packet.turrets.PacketToggleTurret.STREAM_CODEC,
            com.trd.network.packet.turrets.PacketToggleTurret::handle
        );

        registrar.playToServer(
            com.trd.network.packet.turrets.PacketUpdateTurretSettings.TYPE,
            com.trd.network.packet.turrets.PacketUpdateTurretSettings.STREAM_CODEC,
            com.trd.network.packet.turrets.PacketUpdateTurretSettings::handle
        );

        registrar.playToServer(
            com.trd.network.packet.turrets.PacketToggleExtraButton.TYPE,
            com.trd.network.packet.turrets.PacketToggleExtraButton.STREAM_CODEC,
            com.trd.network.packet.turrets.PacketToggleExtraButton::handle
        );

        registrar.playToServer(
            com.trd.network.packet.turrets.PacketModifyTurretChip.TYPE,
            com.trd.network.packet.turrets.PacketModifyTurretChip.STREAM_CODEC,
            com.trd.network.packet.turrets.PacketModifyTurretChip::handle
        );
    }
}
