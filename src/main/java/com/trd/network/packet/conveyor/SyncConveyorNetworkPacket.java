package com.trd.network.packet.conveyor;

import com.trd.api.conveyor.ConveyorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncConveyorNetworkPacket(UUID networkId, List<ConveyorItem> items, List<BlockPos> path) implements CustomPacketPayload {
    public static final Type<SyncConveyorNetworkPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID, "sync_conveyor_network"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncConveyorNetworkPacket> STREAM_CODEC = StreamCodec.ofMember(SyncConveyorNetworkPacket::write, SyncConveyorNetworkPacket::new);

    public SyncConveyorNetworkPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readUUID(), readItems(buf), readPath(buf));
    }

    private static List<ConveyorItem> readItems(RegistryFriendlyByteBuf buf) {
        int itemSize = buf.readInt();
        List<ConveyorItem> items = new ArrayList<>(itemSize);
        for (int i = 0; i < itemSize; i++) {
            ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
            double progress = buf.readDouble();
            ConveyorItem item = new ConveyorItem(stack, progress);
            boolean hasPrevOverride = buf.readBoolean();
            if (hasPrevOverride) {
                item.setPrevOverridePos(buf.readBlockPos());
            }
            items.add(item);
        }
        return items;
    }

    private static List<BlockPos> readPath(RegistryFriendlyByteBuf buf) {
        int pathSize = buf.readInt();
        List<BlockPos> path = new ArrayList<>(pathSize);
        for (int i = 0; i < pathSize; i++) {
            path.add(buf.readBlockPos());
        }
        return path;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(networkId);
        buf.writeInt(items.size());
        for (ConveyorItem item : items) {
            ItemStack.STREAM_CODEC.encode(buf, item.getStack());
            buf.writeDouble(item.getProgress());
            net.minecraft.core.BlockPos prev = item.getPrevOverridePos();
            if (prev != null) {
                buf.writeBoolean(true);
                buf.writeBlockPos(prev);
            } else {
                buf.writeBoolean(false);
            }
        }
        buf.writeInt(path.size());
        for (BlockPos pos : path) {
            buf.writeBlockPos(pos);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            com.trd.api.conveyor.client.ClientConveyorManager.updateNetwork(networkId, items, path);
        });
    }
}
