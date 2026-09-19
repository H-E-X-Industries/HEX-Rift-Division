package com.trd.network.packet.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.trd.block.entity.industrial.energy.MachineBatteryBlockEntity;
import com.trd.main.MainRegistry;

public record UpdateBatteryC2SPacket(BlockPos pos, int buttonId) implements CustomPacketPayload {
    public static final Type<UpdateBatteryC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "update_battery"));

    public static final StreamCodec<FriendlyByteBuf, UpdateBatteryC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
        UpdateBatteryC2SPacket::write, UpdateBatteryC2SPacket::new
    );

    public UpdateBatteryC2SPacket(FriendlyByteBuf buffer) {
        this(buffer.readBlockPos(), buffer.readInt());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeInt(buttonId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.level().getBlockEntity(pos) instanceof MachineBatteryBlockEntity battery) {
                battery.handleButtonPress(buttonId);
            }
        });
    }
}
