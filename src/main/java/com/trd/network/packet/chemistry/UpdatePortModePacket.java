package com.trd.network.packet.chemistry;

import com.trd.block.entity.industrial.chemistry.ChemicalPlantPortBlockEntity;
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

public record UpdatePortModePacket(BlockPos pos, int mode) implements CustomPacketPayload {
    public static final Type<UpdatePortModePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "update_port_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdatePortModePacket> STREAM_CODEC =
            StreamCodec.ofMember(UpdatePortModePacket::write, UpdatePortModePacket::new);

    public UpdatePortModePacket(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readInt());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(mode);
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
                    if (be instanceof ChemicalPlantPortBlockEntity port) {
                        port.setMode(mode);
                    }
                }
            }
        });
    }
}
