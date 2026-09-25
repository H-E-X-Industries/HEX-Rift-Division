package com.trd.network.packet.rotation;

import com.trd.block.entity.industrial.rotation.HandCrankBlockEntity;
import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ScrollHandCrankPacket(BlockPos pos, int scrollDelta) implements CustomPacketPayload {
    public static final Type<ScrollHandCrankPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "scroll_hand_crank"));

    public static final StreamCodec<FriendlyByteBuf, ScrollHandCrankPacket> STREAM_CODEC = StreamCodec.ofMember(
        ScrollHandCrankPacket::write, ScrollHandCrankPacket::new
    );

    public ScrollHandCrankPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(scrollDelta);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player instanceof ServerPlayer serverPlayer) {
                if (serverPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                    BlockEntity be = serverPlayer.serverLevel().getBlockEntity(pos);
                    if (be instanceof HandCrankBlockEntity crank) {
                        crank.addScroll(this.scrollDelta);
                    }
                }
            }
        });
    }
}
