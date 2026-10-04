package com.trd.network.packet.redstone;

import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: сервер сообщает клиенту канал и состояние радиоблока.
 * <p>
 * В 1.20.1 рассылка шла через {@code PacketDistributor.TRACKING_CHUNK} с
 * {@code level.getChunkAt(pos)}; здесь тот же эффект даёт
 * {@link PacketDistributor#sendToPlayersTrackingChunk} — в 1.21.1 методов
 * {@code TRACKING_CHUNK.with(...)} у дистрибьютора больше нет.
 */
public record RedstoneRadioSyncPacket(BlockPos pos, String channelId, boolean powered, int signalStrength)
        implements CustomPacketPayload {

    public static final Type<RedstoneRadioSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "redstone_radio_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RedstoneRadioSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RedstoneRadioSyncPacket::pos,
                    ByteBufCodecs.STRING_UTF8, RedstoneRadioSyncPacket::channelId,
                    ByteBufCodecs.BOOL, RedstoneRadioSyncPacket::powered,
                    ByteBufCodecs.VAR_INT, RedstoneRadioSyncPacket::signalStrength,
                    RedstoneRadioSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().level().getBlockEntity(pos) instanceof com.trd.block.entity.redstone.RedstoneRadioBlockEntity radio) {
                radio.syncFromPacket(channelId, powered, signalStrength);
            }
        });
    }

    /** Отправить всем игрокам, отслеживающим этот чанк. */
    public static void sendToTracking(ServerLevel level, BlockPos pos, String channelId, boolean powered, int signalStrength) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos),
                new RedstoneRadioSyncPacket(pos, channelId, powered, signalStrength));
    }
}
