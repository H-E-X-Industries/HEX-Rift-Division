package com.trd.network.packet.conveyor;

import com.trd.api.conveyor.ConveyorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class SyncConveyorNetworkPacket {
    private final UUID networkId;
    private final List<ConveyorItem> items;
    private final List<BlockPos> path;

    public SyncConveyorNetworkPacket(UUID networkId, List<ConveyorItem> items, List<BlockPos> path) {
        this.networkId = networkId;
        this.items = items;
        this.path = path;
    }

    public SyncConveyorNetworkPacket(FriendlyByteBuf buf) {
        this.networkId = buf.readUUID();
        int itemSize = buf.readInt();
        this.items = new ArrayList<>(itemSize);
        for (int i = 0; i < itemSize; i++) {
            ItemStack stack = buf.readItem();
            double progress = buf.readDouble();
            ConveyorItem item = new ConveyorItem(stack, progress);
            boolean hasPrevOverride = buf.readBoolean();
            if (hasPrevOverride) {
                item.setPrevOverridePos(buf.readBlockPos());
            }
            this.items.add(item);
        }
        int pathSize = buf.readInt();
        this.path = new ArrayList<>(pathSize);
        for (int i = 0; i < pathSize; i++) {
            this.path.add(buf.readBlockPos());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(networkId);
        buf.writeInt(items.size());
        for (ConveyorItem item : items) {
            buf.writeItem(item.getStack());
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

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            com.trd.api.conveyor.client.ClientConveyorManager.updateNetwork(networkId, items, path);
        });
        return true;
    }
}
