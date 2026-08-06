package com.trd.network.packet.chemistry;

import com.trd.block.entity.industrial.chemistry.ChemicalPlantReactionChamberBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClearChemicalRecipePacket {
    private final BlockPos pos;

    public ClearChemicalRecipePacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(ClearChemicalRecipePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static ClearChemicalRecipePacket decode(FriendlyByteBuf buf) {
        return new ClearChemicalRecipePacket(buf.readBlockPos());
    }

    public static void handle(ClearChemicalRecipePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (be instanceof ChemicalPlantReactionChamberBlockEntity chamber) {
                chamber.setRecipe(null);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}