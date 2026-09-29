package com.trd.network.packet.explosion;

import com.trd.client.render.CraterTints;
import com.trd.main.MainRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: сервер передаёт клиенту списки затемняемых позиций (добавление/удаление)
 * для позиционного тинта твёрдых блоков кратера водородной гранаты.
 * Пересылается по чанкам, чтобы не выходить за лимит пакета.
 *
 * <p>Формат на проводе (как в 1.20.1): dimension, varint(N), N×long (добавления),
 * N×byte (ступени затемнения), varint(R), R×long (удаления). Длина массива ступеней
 * не пишется — она равна N.
 */
public record SyncCraterTintsPacket(ResourceLocation dimension, long[] adds, int[] addDark, long[] removes)
        implements CustomPacketPayload {

    public static final Type<SyncCraterTintsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "sync_crater_tints"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCraterTintsPacket> STREAM_CODEC =
            StreamCodec.ofMember(SyncCraterTintsPacket::write, SyncCraterTintsPacket::readFrom);

    /**
     * Декодер 1-в-1 к {@code SyncCraterTintsPacket.decode} из 1.20.1.
     *
     * <p>Важно: ступени затемнения идут по одному байту сразу за позициями, БЕЗ
     * собственного префикса длины — их количество равно числу добавлений, уже
     * прочитанному вместе с {@code adds}.
     */
    private static SyncCraterTintsPacket readFrom(RegistryFriendlyByteBuf buf) {
        ResourceLocation dim = buf.readResourceLocation();
        long[] adds = readLongArray(buf);
        int[] addDark = new int[adds.length];
        for (int i = 0; i < addDark.length; i++) addDark[i] = buf.readByte();
        long[] removes = readLongArray(buf);
        return new SyncCraterTintsPacket(dim, adds, addDark, removes);
    }

    private static long[] readLongArray(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        long[] arr = new long[n];
        for (int i = 0; i < n; i++) arr[i] = buf.readLong();
        return arr;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension);
        buf.writeVarInt(adds.length);
        for (long l : adds) buf.writeLong(l);
        for (int d : addDark) buf.writeByte(d);
        buf.writeVarInt(removes.length);
        for (long l : removes) buf.writeLong(l);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (adds.length > 0) CraterTints.addTints(dimension, adds, addDark);
            if (removes.length > 0) CraterTints.removeTints(dimension, removes);
        });
    }

    /** Разослать добавления затемнения всем игрокам измерения (замена DIMENSION-дистрибьютора 1.20.1). */
    public static void broadcastAdds(net.minecraft.server.level.ServerLevel level, long[] pos, int[] dark) {
        PacketDistributor.sendToPlayersInDimension(level,
                new SyncCraterTintsPacket(level.dimension().location(), pos, dark, new long[0]));
    }

    /** Разослать удаления затемнения всем игрокам измерения. */
    public static void broadcastRemoves(net.minecraft.server.level.ServerLevel level, long[] removes) {
        PacketDistributor.sendToPlayersInDimension(level,
                new SyncCraterTintsPacket(level.dimension().location(), new long[0], new int[0], removes));
    }
}
