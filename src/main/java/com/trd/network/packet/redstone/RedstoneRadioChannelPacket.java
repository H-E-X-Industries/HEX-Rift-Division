package com.trd.network.packet.redstone;

import com.trd.block.entity.redstone.RedstoneRadioBlockEntity;
import com.trd.block.entity.redstone.RedstoneRadioTransmitterBlockEntity;
import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S: игрок задал новый канал в GUI радиоблока. */
public record RedstoneRadioChannelPacket(BlockPos pos, String channelId) implements CustomPacketPayload {

    public static final Type<RedstoneRadioChannelPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "redstone_radio_channel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RedstoneRadioChannelPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RedstoneRadioChannelPacket::pos,
                    ByteBufCodecs.STRING_UTF8, RedstoneRadioChannelPacket::channelId,
                    RedstoneRadioChannelPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Level level = ctx.player().level();
            if (level.getBlockEntity(pos) instanceof RedstoneRadioBlockEntity radio) {
                radio.setChannelId(channelId); // теперь вызовет sendSyncPacket()
                if (radio instanceof RedstoneRadioTransmitterBlockEntity transmitter) {
                    transmitter.forceRescan(level);
                }
            }
        });
    }
}
