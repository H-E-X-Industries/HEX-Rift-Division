package com.trd.network;

import com.trd.main.MainRegistry;
import com.trd.network.packet.industrial.energy.PacketSyncEnergy;
import com.trd.network.packet.industrial.energy.SyncMotorRpmPacket;
import com.trd.network.packet.industrial.energy.UpdateBatteryC2SPacket;
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
            com.trd.network.packet.industrial.fluids.SelectFluidPacket.TYPE,
            com.trd.network.packet.industrial.fluids.SelectFluidPacket.STREAM_CODEC,
            com.trd.network.packet.industrial.fluids.SelectFluidPacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.industrial.fluids.ToggleFavoriteFluidPacket.TYPE,
            com.trd.network.packet.industrial.fluids.ToggleFavoriteFluidPacket.STREAM_CODEC,
            com.trd.network.packet.industrial.fluids.ToggleFavoriteFluidPacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.industrial.fluids.ClearFluidHistoryPacket.TYPE,
            com.trd.network.packet.industrial.fluids.ClearFluidHistoryPacket.STREAM_CODEC,
            com.trd.network.packet.industrial.fluids.ClearFluidHistoryPacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.industrial.fluids.UpdateBarrelModeC2SPacket.TYPE,
            com.trd.network.packet.industrial.fluids.UpdateBarrelModeC2SPacket.STREAM_CODEC,
            com.trd.network.packet.industrial.fluids.UpdateBarrelModeC2SPacket::handle
        );

        registrar.playToClient(
            com.trd.network.packet.industrial.conveyor.SyncConveyorNetworkPacket.TYPE,
            com.trd.network.packet.industrial.conveyor.SyncConveyorNetworkPacket.STREAM_CODEC,
            com.trd.network.packet.industrial.conveyor.SyncConveyorNetworkPacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.industrial.conveyor.UpdateSortirovshikFilterC2SPacket.TYPE,
            com.trd.network.packet.industrial.conveyor.UpdateSortirovshikFilterC2SPacket.STREAM_CODEC,
            com.trd.network.packet.industrial.conveyor.UpdateSortirovshikFilterC2SPacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.industrial.conveyor.UpdateSortirovshikModeC2SPacket.TYPE,
            com.trd.network.packet.industrial.conveyor.UpdateSortirovshikModeC2SPacket.STREAM_CODEC,
            com.trd.network.packet.industrial.conveyor.UpdateSortirovshikModeC2SPacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.industrial.rotation.ScrollHandCrankPacket.TYPE,
            com.trd.network.packet.industrial.rotation.ScrollHandCrankPacket.STREAM_CODEC,
            com.trd.network.packet.industrial.rotation.ScrollHandCrankPacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.industrial.chemistry.ClearChemicalRecipePacket.TYPE,
            com.trd.network.packet.industrial.chemistry.ClearChemicalRecipePacket.STREAM_CODEC,
            com.trd.network.packet.industrial.chemistry.ClearChemicalRecipePacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.industrial.chemistry.SelectChemicalRecipePacket.TYPE,
            com.trd.network.packet.industrial.chemistry.SelectChemicalRecipePacket.STREAM_CODEC,
            com.trd.network.packet.industrial.chemistry.SelectChemicalRecipePacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.industrial.chemistry.UpdatePortModePacket.TYPE,
            com.trd.network.packet.industrial.chemistry.UpdatePortModePacket.STREAM_CODEC,
            com.trd.network.packet.industrial.chemistry.UpdatePortModePacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.industrial.rotation.SelectStanokRecipePacket.TYPE,
            com.trd.network.packet.industrial.rotation.SelectStanokRecipePacket.STREAM_CODEC,
            com.trd.network.packet.industrial.rotation.SelectStanokRecipePacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.industrial.rotation.ClearStanokRecipePacket.TYPE,
            com.trd.network.packet.industrial.rotation.ClearStanokRecipePacket.STREAM_CODEC,
            com.trd.network.packet.industrial.rotation.ClearStanokRecipePacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.weapons.guns.PacketShoot.TYPE,
            com.trd.network.packet.weapons.guns.PacketShoot.STREAM_CODEC,
            com.trd.network.packet.weapons.guns.PacketShoot::handle
        );

        registrar.playToServer(
            com.trd.network.packet.weapons.guns.PacketReloadGun.TYPE,
            com.trd.network.packet.weapons.guns.PacketReloadGun.STREAM_CODEC,
            com.trd.network.packet.weapons.guns.PacketReloadGun::handle
        );

        registrar.playToServer(
            com.trd.network.packet.weapons.guns.PacketUnloadGun.TYPE,
            com.trd.network.packet.weapons.guns.PacketUnloadGun.STREAM_CODEC,
            com.trd.network.packet.weapons.guns.PacketUnloadGun::handle
        );

        registrar.playToServer(
            com.trd.network.packet.weapons.guns.PacketScopeState.TYPE,
            com.trd.network.packet.weapons.guns.PacketScopeState.STREAM_CODEC,
            com.trd.network.packet.weapons.guns.PacketScopeState::handle
        );

        registrar.playToClient(
            com.trd.network.packet.weapons.guns.PacketMachineGunAnim.TYPE,
            com.trd.network.packet.weapons.guns.PacketMachineGunAnim.STREAM_CODEC,
            com.trd.network.packet.weapons.guns.PacketMachineGunAnim::handle
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
            com.trd.network.packet.weapons.turrets.PacketChipFeedback.TYPE,
            com.trd.network.packet.weapons.turrets.PacketChipFeedback.STREAM_CODEC,
            com.trd.network.packet.weapons.turrets.PacketChipFeedback::handle
        );

        registrar.playToServer(
            com.trd.network.packet.weapons.turrets.PacketToggleTurret.TYPE,
            com.trd.network.packet.weapons.turrets.PacketToggleTurret.STREAM_CODEC,
            com.trd.network.packet.weapons.turrets.PacketToggleTurret::handle
        );

        registrar.playToServer(
            com.trd.network.packet.weapons.turrets.PacketUpdateTurretSettings.TYPE,
            com.trd.network.packet.weapons.turrets.PacketUpdateTurretSettings.STREAM_CODEC,
            com.trd.network.packet.weapons.turrets.PacketUpdateTurretSettings::handle
        );

        registrar.playToServer(
            com.trd.network.packet.weapons.turrets.PacketToggleExtraButton.TYPE,
            com.trd.network.packet.weapons.turrets.PacketToggleExtraButton.STREAM_CODEC,
            com.trd.network.packet.weapons.turrets.PacketToggleExtraButton::handle
        );

        registrar.playToServer(
            com.trd.network.packet.weapons.turrets.PacketModifyTurretChip.TYPE,
            com.trd.network.packet.weapons.turrets.PacketModifyTurretChip.STREAM_CODEC,
            com.trd.network.packet.weapons.turrets.PacketModifyTurretChip::handle
        );

        // === РЕДСТОУН-РАДИО ===

        registrar.playToClient(
            com.trd.network.packet.industrial.redstone.RedstoneRadioSyncPacket.TYPE,
            com.trd.network.packet.industrial.redstone.RedstoneRadioSyncPacket.STREAM_CODEC,
            com.trd.network.packet.industrial.redstone.RedstoneRadioSyncPacket::handle
        );

        registrar.playToServer(
            com.trd.network.packet.industrial.redstone.RedstoneRadioChannelPacket.TYPE,
            com.trd.network.packet.industrial.redstone.RedstoneRadioChannelPacket.STREAM_CODEC,
            com.trd.network.packet.industrial.redstone.RedstoneRadioChannelPacket::handle
        );
    }
}
