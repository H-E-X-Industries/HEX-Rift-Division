package com.trd.network.packet.conveyor;

import com.trd.block.entity.industrial.conveyors.SortirovshikBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.Supplier;

/**
 * Клик по фантомному слоту фильтра сортировщика.
 * В руке предмет — сортировщик запоминает его копию вместе с NBT (предмет не тратится).
 * Рука пустая — фантомный предмет исчезает, сортировщик забывает его.
 */
public record UpdateSortirovshikFilterC2SPacket(BlockPos pos, int filterIndex) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<UpdateSortirovshikFilterC2SPacket> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID, "update_sortirovshik_filter"));

    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, UpdateSortirovshikFilterC2SPacket> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.ofMember(UpdateSortirovshikFilterC2SPacket::write, UpdateSortirovshikFilterC2SPacket::new);

    public UpdateSortirovshikFilterC2SPacket(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readInt());
    }

    public void write(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(filterIndex);
    }

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            net.minecraft.world.entity.player.Player player = context.player();
            if (player instanceof ServerPlayer serverPlayer) {
                if (!(serverPlayer.level().getBlockEntity(pos) instanceof SortirovshikBlockEntity sorter)) return;
                if (filterIndex < 0 || filterIndex >= SortirovshikBlockEntity.TOTAL_FILTER_SLOTS) return;
                if (serverPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;

                ItemStack carried = serverPlayer.containerMenu.getCarried();
                if (!carried.isEmpty()) {
                    sorter.setFilter(filterIndex, carried);
                } else {
                    sorter.clearFilter(filterIndex);
                }
            }
        });
    }
}
