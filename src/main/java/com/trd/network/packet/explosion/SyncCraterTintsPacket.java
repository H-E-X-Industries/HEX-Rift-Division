package com.trd.network.packet.explosion;

import com.trd.client.render.CraterTints;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: сервер передаёт клиенту списки затемняемых позиций (добавление/удаление)
 * для позиционного тинта твёрдых блоков кратера водородной гранаты.
 * Пересылается по чанкам, чтобы не выходить за лимит пакета.
 */
public class SyncCraterTintsPacket {

    private final ResourceLocation dimension;
    private final long[] adds;
    private final int[] addDark;
    private final long[] removes;

    public SyncCraterTintsPacket(ResourceLocation dimension, long[] adds, int[] addDark, long[] removes) {
        this.dimension = dimension;
        this.adds = adds;
        this.addDark = addDark;
        this.removes = removes;
    }

    public static void encode(SyncCraterTintsPacket p, FriendlyByteBuf buf) {
        buf.writeResourceLocation(p.dimension);
        buf.writeVarInt(p.adds.length);
        for (long l : p.adds) buf.writeLong(l);
        for (int d : p.addDark) buf.writeByte(d);
        buf.writeVarInt(p.removes.length);
        for (long l : p.removes) buf.writeLong(l);
    }

    public static SyncCraterTintsPacket decode(FriendlyByteBuf buf) {
        ResourceLocation dim = buf.readResourceLocation();
        int na = buf.readVarInt();
        long[] adds = new long[na];
        for (int i = 0; i < na; i++) adds[i] = buf.readLong();
        int[] addDark = new int[na];
        for (int i = 0; i < na; i++) addDark[i] = buf.readByte();
        int nr = buf.readVarInt();
        long[] removes = new long[nr];
        for (int i = 0; i < nr; i++) removes[i] = buf.readLong();
        return new SyncCraterTintsPacket(dim, adds, addDark, removes);
    }

    public static void handle(SyncCraterTintsPacket packet, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            if (packet.adds.length > 0) CraterTints.addTints(packet.dimension, packet.adds, packet.addDark);
            if (packet.removes.length > 0) CraterTints.removeTints(packet.dimension, packet.removes);
        });
        ctx.setPacketHandled(true);
    }
}