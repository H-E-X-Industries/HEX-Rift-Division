package com.trd.network.packet.chemistry;

import com.trd.block.entity.industrial.chemistry.ChemicalPlantPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdatePortModePacket {
    private final BlockPos pos;
    private final int mode;

    public UpdatePortModePacket(BlockPos pos, int mode) {
        this.pos = pos;
        this.mode = mode;
    }

    public static void encode(UpdatePortModePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.mode);
    }

    public static UpdatePortModePacket decode(FriendlyByteBuf buf) {
        return new UpdatePortModePacket(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(UpdatePortModePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (be instanceof ChemicalPlantPortBlockEntity port) {
                port.setMode(msg.mode);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}