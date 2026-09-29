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
 * C2S: тумблер питания буфера турели.
 */
public record PacketToggleTurret(BlockPos pos) implements CustomPacketPayload {

    public static final Type<PacketToggleTurret> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "toggle_turret"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketToggleTurret> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, PacketToggleTurret::pos,
                    PacketToggleTurret::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                BlockEntity be = player.level().getBlockEntity(pos);
                if (be instanceof TurretLightPlacerBlockEntity turretBE) {
                    turretBE.togglePower();
                } else if (be instanceof com.trd.block.entity.weapons.MissileTurretBlockEntity missileBE) {
                    missileBE.togglePower();
                }
            }
        });
    }
}
