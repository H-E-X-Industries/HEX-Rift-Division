package com.trd.network.packet.energy;


import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncMotorRpmPacket(BlockPos pos, int rpm) implements CustomPacketPayload {
    public static final Type<SyncMotorRpmPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "sync_motor_rpm"));

    public static final StreamCodec<FriendlyByteBuf, SyncMotorRpmPacket> STREAM_CODEC = StreamCodec.ofMember(
        SyncMotorRpmPacket::write, SyncMotorRpmPacket::new
    );

    public SyncMotorRpmPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(rpm);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player == null) return;
            if (!(player.level() instanceof ServerLevel level)) return;
        });
    }
}
