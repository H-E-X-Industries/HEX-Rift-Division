package com.trd.network.packet.redstone;

import com.trd.block.entity.redstone.RedstoneRadioBlockEntity;
import com.trd.block.entity.redstone.RedstoneRadioTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RedstoneRadioChannelPacket {
    private final BlockPos pos;
    private final String channelId;

    public RedstoneRadioChannelPacket(BlockPos pos, String channelId) {
        this.pos = pos;
        this.channelId = channelId;
    }

    public static void encode(RedstoneRadioChannelPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeUtf(packet.channelId);
    }

    public static RedstoneRadioChannelPacket decode(FriendlyByteBuf buf) {
        return new RedstoneRadioChannelPacket(buf.readBlockPos(), buf.readUtf());
    }

    public static void handle(RedstoneRadioChannelPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var sender = context.getSender();
            if (sender != null) {
                Level level = sender.level();
                var be = level.getBlockEntity(packet.pos);
                if (be instanceof RedstoneRadioBlockEntity radio) {
                    radio.setChannelId(packet.channelId); // теперь вызовет sendSyncPacket()
                    if (be instanceof RedstoneRadioTransmitterBlockEntity transmitter) {
                        transmitter.forceRescan(level);
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}