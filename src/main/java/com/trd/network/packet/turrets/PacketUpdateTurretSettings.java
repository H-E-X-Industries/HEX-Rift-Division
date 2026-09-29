package com.trd.network.packet.turrets;

import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.trd.block.entity.weapons.TurretLightPlacerBlockEntity;

/**
 * C2S: переключение одной из трёх настроек целей (враждебные / нейтральные / игроки).
 */
public record PacketUpdateTurretSettings(BlockPos pos, int settingIndex, boolean value)
        implements CustomPacketPayload {

    public static final Type<PacketUpdateTurretSettings> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "update_turret_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketUpdateTurretSettings> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PacketUpdateTurretSettings::pos,
                    ByteBufCodecs.INT, PacketUpdateTurretSettings::settingIndex,
                    ByteBufCodecs.BOOL, PacketUpdateTurretSettings::value,
                    PacketUpdateTurretSettings::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                BlockEntity be = player.level().getBlockEntity(pos);
                if (be instanceof TurretLightPlacerBlockEntity turretBE) {
                    turretBE.updateAttackSetting(settingIndex, value);
                } else if (be instanceof com.trd.block.entity.weapons.MissileTurretBlockEntity missileBE) {
                    missileBE.updateAttackSetting(settingIndex, value);
                }
            }
        });
    }
}
