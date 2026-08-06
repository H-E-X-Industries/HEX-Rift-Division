package com.trd.network.packet.chemistry;

import com.trd.block.entity.industrial.chemistry.ChemicalPlantReactionChamberBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SelectChemicalRecipePacket {
    private final BlockPos pos;
    private final ResourceLocation recipeId;

    public SelectChemicalRecipePacket(BlockPos pos, ResourceLocation recipeId) {
        this.pos = pos;
        this.recipeId = recipeId;
    }

    public static void encode(SelectChemicalRecipePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeResourceLocation(msg.recipeId);
    }

    public static SelectChemicalRecipePacket decode(FriendlyByteBuf buf) {
        return new SelectChemicalRecipePacket(buf.readBlockPos(), buf.readResourceLocation());
    }

    public static void handle(SelectChemicalRecipePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (be instanceof ChemicalPlantReactionChamberBlockEntity chamber) {
                chamber.setRecipe(msg.recipeId);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}