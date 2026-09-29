package com.trd.network.packet.explosion;

import com.trd.client.render.CraterTints;
import com.trd.main.MainRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: сервер сообщает клиенту координаты свежего кратера, чтобы клиент мог
 * затемнить ванильные твёрдые блоки у края воронки (без замены самих блоков).
 */
public record SyncCraterPacket(double x, double y, double z, float radius, float band)
        implements CustomPacketPayload {

    public static final Type<SyncCraterPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "sync_crater"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCraterPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, SyncCraterPacket::x,
                    ByteBufCodecs.DOUBLE, SyncCraterPacket::y,
                    ByteBufCodecs.DOUBLE, SyncCraterPacket::z,
                    ByteBufCodecs.FLOAT, SyncCraterPacket::radius,
                    ByteBufCodecs.FLOAT, SyncCraterPacket::band,
                    SyncCraterPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level instanceof ClientLevel clientLevel) {
                CraterTints.addCrater(x, y, z, radius, band);
                CraterTints.refreshSections(clientLevel);
            }
        });
    }

    /** Разослать всем игрокам измерения (замена DIMENSION-дистрибьютора 1.20.1). */
    public static void broadcast(net.minecraft.server.level.ServerLevel level,
                                 double x, double y, double z, float radius, float band) {
        PacketDistributor.sendToPlayersInDimension(level, new SyncCraterPacket(x, y, z, radius, band));
    }
}
