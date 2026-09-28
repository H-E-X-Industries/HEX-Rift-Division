package com.trd.network.packet.rotation;

import com.trd.multiblock.industrial.stanok.StanokBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClearStanokRecipePacket {

    private final BlockPos pos;

    public ClearStanokRecipePacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(ClearStanokRecipePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static ClearStanokRecipePacket decode(FriendlyByteBuf buf) {
        return new ClearStanokRecipePacket(buf.readBlockPos());
    }

    public static void handle(ClearStanokRecipePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (be instanceof StanokBlockEntity stanok) {
                stanok.setCurrentRecipeId(null);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
