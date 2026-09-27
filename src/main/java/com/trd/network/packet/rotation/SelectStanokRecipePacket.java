package com.trd.network.packet.rotation;

import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.stanok.StanokBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SelectStanokRecipePacket(BlockPos pos, ResourceLocation recipeId) implements CustomPacketPayload {
    public static final Type<SelectStanokRecipePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "select_stanok_recipe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectStanokRecipePacket> STREAM_CODEC =
            StreamCodec.ofMember(SelectStanokRecipePacket::write, SelectStanokRecipePacket::new);

    public SelectStanokRecipePacket(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readResourceLocation());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeResourceLocation(recipeId);
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
                    if (be instanceof StanokBlockEntity stanok) {
                        stanok.setCurrentRecipeId(recipeId);
                    }
                }
            }
        });
    }
}
