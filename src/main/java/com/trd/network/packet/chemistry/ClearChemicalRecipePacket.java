package com.trd.network.packet.chemistry;

import com.trd.block.entity.industrial.chemistry.ChemicalPlantReactionChamberBlockEntity;
import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClearChemicalRecipePacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ClearChemicalRecipePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "clear_chemical_recipe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClearChemicalRecipePacket> STREAM_CODEC =
            StreamCodec.ofMember(ClearChemicalRecipePacket::write, ClearChemicalRecipePacket::new);

    public ClearChemicalRecipePacket(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player instanceof ServerPlayer serverPlayer) {
                if (serverPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                    BlockEntity be = serverPlayer.level().getBlockEntity(pos);
                    if (be instanceof ChemicalPlantReactionChamberBlockEntity chamber) {
                        chamber.setRecipe(null);
                    }
                }
            }
        });
    }
}
