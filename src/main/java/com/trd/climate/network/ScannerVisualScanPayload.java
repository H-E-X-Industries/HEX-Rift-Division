package com.trd.climate.network;

import com.trd.climate.client.ScannerVisualRenderHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сетевой пакет для передачи клиенту геометрии отсканированного объема помещения.
 * Используется для голографической подсветки первого слоя проверенных блоков.
 */
public record ScannerVisualScanPayload(
        BlockPos controllerPos,
        long[] interiorPositions,
        boolean isSuccess,
        int volume,
        int clusterSize
) implements CustomPacketPayload {

    public static final Type<ScannerVisualScanPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("trd", "scanner_visual_scan"));

    public static final StreamCodec<ByteBuf, long[]> LONG_ARRAY_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public long[] decode(ByteBuf buffer) {
            int length = ByteBufCodecs.VAR_INT.decode(buffer);
            long[] array = new long[length];
            for (int i = 0; i < length; i++) {
                array[i] = buffer.readLong();
            }
            return array;
        }

        @Override
        public void encode(ByteBuf buffer, long[] value) {
            ByteBufCodecs.VAR_INT.encode(buffer, value.length);
            for (long l : value) {
                buffer.writeLong(l);
            }
        }
    };

    public static final StreamCodec<ByteBuf, ScannerVisualScanPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ScannerVisualScanPayload::controllerPos,
            LONG_ARRAY_STREAM_CODEC, ScannerVisualScanPayload::interiorPositions,
            ByteBufCodecs.BOOL, ScannerVisualScanPayload::isSuccess,
            ByteBufCodecs.VAR_INT, ScannerVisualScanPayload::volume,
            ByteBufCodecs.VAR_INT, ScannerVisualScanPayload::clusterSize,
            ScannerVisualScanPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Обработка пакета на клиенте.
     */
    public static void handle(ScannerVisualScanPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ScannerVisualRenderHandler.onScanResult(
                    payload.controllerPos(),
                    payload.interiorPositions(),
                    payload.isSuccess(),
                    payload.volume(),
                    payload.clusterSize()
            );
        });
    }
}
