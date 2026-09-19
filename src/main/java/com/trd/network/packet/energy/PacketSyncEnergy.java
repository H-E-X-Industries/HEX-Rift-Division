package com.trd.network.packet.energy;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.trd.client.handler.ClientEnergySyncHandler;
import com.trd.main.MainRegistry;

public record PacketSyncEnergy(int containerId, long energy, long maxEnergy, long delta, long chargingSpeed, long unchargingSpeed, int filledCellCount) implements CustomPacketPayload {
    public static final Type<PacketSyncEnergy> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "sync_energy"));

    public static final StreamCodec<FriendlyByteBuf, PacketSyncEnergy> STREAM_CODEC = StreamCodec.ofMember(
        PacketSyncEnergy::write, PacketSyncEnergy::new
    );

    public PacketSyncEnergy(FriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readInt());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt(containerId);
        buffer.writeLong(energy);
        buffer.writeLong(maxEnergy);
        buffer.writeLong(delta);
        buffer.writeLong(chargingSpeed);
        buffer.writeLong(unchargingSpeed);
        buffer.writeInt(filledCellCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientEnergySyncHandler.handle(
                this.containerId,
                this.energy,
                this.maxEnergy,
                this.delta,
                this.chargingSpeed,
                this.unchargingSpeed,
                this.filledCellCount
            );
        });
    }
}
