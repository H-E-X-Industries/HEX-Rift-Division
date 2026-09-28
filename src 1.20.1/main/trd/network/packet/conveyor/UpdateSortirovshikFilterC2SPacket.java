package com.trd.network.packet.conveyor;

import com.trd.block.entity.industrial.conveyors.SortirovshikBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клик по фантомному слоту фильтра сортировщика.
 * В руке предмет — сортировщик запоминает его копию вместе с NBT (предмет не тратится).
 * Рука пустая — фантомный предмет исчезает, сортировщик забывает его.
 */
public class UpdateSortirovshikFilterC2SPacket {
    private final BlockPos pos;
    private final int filterIndex;

    public UpdateSortirovshikFilterC2SPacket(BlockPos pos, int filterIndex) {
        this.pos = pos;
        this.filterIndex = filterIndex;
    }

    public UpdateSortirovshikFilterC2SPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.filterIndex = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(filterIndex);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (!(player.level().getBlockEntity(pos) instanceof SortirovshikBlockEntity sorter)) return;
            if (filterIndex < 0 || filterIndex >= SortirovshikBlockEntity.TOTAL_FILTER_SLOTS) return;
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;

            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                sorter.setFilter(filterIndex, carried);
            } else {
                sorter.clearFilter(filterIndex);
            }
        });
        return true;
    }
}
