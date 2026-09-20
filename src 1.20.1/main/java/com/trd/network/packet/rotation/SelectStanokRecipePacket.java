package com.trd.network.packet.rotation;

import com.trd.multiblock.industrial.stanok.StanokBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SelectStanokRecipePacket {

    private final BlockPos pos;
    private final ResourceLocation recipeId;

    public SelectStanokRecipePacket(BlockPos pos, ResourceLocation recipeId) {
        this.pos = pos;
        this.recipeId = recipeId;
    }

    public static void encode(SelectStanokRecipePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeResourceLocation(msg.recipeId);
    }

    public static SelectStanokRecipePacket decode(FriendlyByteBuf buf) {
        return new SelectStanokRecipePacket(buf.readBlockPos(), buf.readResourceLocation());
    }

    public static void handle(SelectStanokRecipePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (be instanceof StanokBlockEntity stanok) {
                stanok.setCurrentRecipeId(msg.recipeId);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
