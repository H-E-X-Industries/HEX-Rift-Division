package com.trd.climate.network;

import com.trd.climate.client.ClientTemperatureHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сетевой пакет для синхронизации температуры игрока с клиентом.
 */
public record SyncTemperaturePayload(float currentTemp, float targetTemp) implements CustomPacketPayload {
    
    public static final Type<SyncTemperaturePayload> TYPE = 
        new Type<>(ResourceLocation.fromNamespaceAndPath("trd", "sync_temperature"));

    public static final StreamCodec<ByteBuf, SyncTemperaturePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, SyncTemperaturePayload::currentTemp,
        ByteBufCodecs.FLOAT, SyncTemperaturePayload::targetTemp,
        SyncTemperaturePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Обработчик пакета на клиенте.
     */
    public static void handle(SyncTemperaturePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Обновляем клиентский кэш температуры
            ClientTemperatureHandler.setClientTemp(payload.currentTemp(), payload.targetTemp());
        });
    }
}
