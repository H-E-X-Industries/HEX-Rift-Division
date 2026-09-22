package com.trd.network.packet.conveyor;

import com.trd.block.entity.industrial.conveyors.SortirovshikBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.Supplier;

/** Переключение режима секции сортировщика (кнопка в GUI). */
public record UpdateSortirovshikModeC2SPacket(BlockPos pos, int section) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<UpdateSortirovshikModeC2SPacket> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID, "update_sortirovshik_mode"));

    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, UpdateSortirovshikModeC2SPacket> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.ofMember(UpdateSortirovshikModeC2SPacket::write, UpdateSortirovshikModeC2SPacket::new);

    public UpdateSortirovshikModeC2SPacket(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readInt());
    }

    public void write(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(section);
    }

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            net.minecraft.world.entity.player.Player player = context.player();
            if (player instanceof ServerPlayer serverPlayer) {
                if (serverPlayer.level().getBlockEntity(pos) instanceof SortirovshikBlockEntity sorter
                        && section >= 0 && section < SortirovshikBlockEntity.SECTIONS
                        && serverPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                    sorter.cycleMode(section);
                }
            }
        });
    }
}
