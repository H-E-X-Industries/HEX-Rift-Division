package com.trd.network.packet.explosion;

import com.trd.client.render.CraterTints;
import com.trd.network.ModPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: сервер сообщает клиенту координаты свежего кратера, чтобы клиент мог
 * затемнить ванильные твёрдые блоки у края воронки (без замены самих блоков).
 */
public class SyncCraterPacket {
    private final double x;
    private final double y;
    private final double z;
    private final float radius;
    private final float band;

    public SyncCraterPacket(double x, double y, double z, float radius, float band) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.band = band;
    }

    public static void encode(SyncCraterPacket packet, FriendlyByteBuf buf) {
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.y);
        buf.writeDouble(packet.z);
        buf.writeFloat(packet.radius);
        buf.writeFloat(packet.band);
    }

    public static SyncCraterPacket decode(FriendlyByteBuf buf) {
        return new SyncCraterPacket(
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat()
        );
    }

    public static void handle(SyncCraterPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var level = ModPacketHandler.getClientLevel();
            if (level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
                CraterTints.addCrater(packet.x, packet.y, packet.z, packet.radius, packet.band);
                CraterTints.refreshSections(clientLevel);
            }
        });
        context.setPacketHandled(true);
    }
}