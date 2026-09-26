package com.trd.network.packet.fluids;

import com.trd.api.fluids.system.ITankWithMode;
import com.trd.main.MainRegistry;
import com.trd.multiblock.system.roles.IMultiblockPart;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Пакет переключения режима бочки/цистерны (Both, Input, Output, Disabled).
 */
public record UpdateBarrelModeC2SPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<UpdateBarrelModeC2SPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "update_barrel_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateBarrelModeC2SPacket> STREAM_CODEC =
            StreamCodec.ofMember(UpdateBarrelModeC2SPacket::write, UpdateBarrelModeC2SPacket::new);

    public UpdateBarrelModeC2SPacket(RegistryFriendlyByteBuf buf) {
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
                    if (be instanceof ITankWithMode tank) {
                        tank.changeMode();
                        if (serverPlayer.containerMenu != null) {
                            serverPlayer.containerMenu.broadcastChanges();
                        }
                    } else if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
                        BlockEntity ctrl = serverPlayer.level().getBlockEntity(part.getControllerPos());
                        if (ctrl instanceof ITankWithMode ctrlTank) {
                            ctrlTank.changeMode();
                            if (serverPlayer.containerMenu != null) {
                                serverPlayer.containerMenu.broadcastChanges();
                            }
                        }
                    }
                }
            }
        });
    }
}
