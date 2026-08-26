package com.trd.network.packet.redstone;

import com.trd.network.ModPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RedstoneRadioSyncPacket {
    private final BlockPos pos;
    private final String channelId;
    private final boolean powered;
    private final int signalStrength;

    public RedstoneRadioSyncPacket(BlockPos pos, String channelId, boolean powered, int signalStrength) {
        this.pos = pos;
        this.channelId = channelId;
        this.powered = powered;
        this.signalStrength = signalStrength;
    }

    public static void encode(RedstoneRadioSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeUtf(packet.channelId);
        buf.writeBoolean(packet.powered);
        buf.writeInt(packet.signalStrength);
    }

    public static RedstoneRadioSyncPacket decode(FriendlyByteBuf buf) {
        return new RedstoneRadioSyncPacket(
            buf.readBlockPos(),
            buf.readUtf(),
            buf.readBoolean(),
            buf.readInt()
        );
    }

    public static void handle(RedstoneRadioSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() == null) {
                var level = ModPacketHandler.getClientLevel();
                if (level != null) {
                    var be = level.getBlockEntity(packet.pos);
                    if (be instanceof com.trd.block.entity.redstone.RedstoneRadioBlockEntity radio) {
                        radio.syncFromPacket(packet.channelId, packet.powered, packet.signalStrength);
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}