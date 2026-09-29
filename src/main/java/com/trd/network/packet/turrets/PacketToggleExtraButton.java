package com.trd.network.packet.turrets;

import com.trd.block.entity.weapons.MissileTurretBlockEntity;
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

/**
 * C2S: переключение одной из двух дополнительных кнопок тромбона
 * (1 — первая, 2 — вторая). Кнопки есть только у ракетницы,
 * в отличие от {@link PacketToggleTurret}, который работает с обоими стволами.
 */
public record PacketToggleExtraButton(BlockPos pos, int buttonId) implements CustomPacketPayload {

    public static final Type<PacketToggleExtraButton> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "toggle_extra_button"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketToggleExtraButton> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PacketToggleExtraButton::pos,
                    ByteBufCodecs.INT, PacketToggleExtraButton::buttonId,
                    PacketToggleExtraButton::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                BlockEntity be = player.level().getBlockEntity(pos);
                if (be instanceof MissileTurretBlockEntity turretBE) {
                    turretBE.toggleExtraButton(buttonId);
                }
            }
        });
    }
}
