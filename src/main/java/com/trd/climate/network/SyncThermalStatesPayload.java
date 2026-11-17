package com.trd.climate.network;

import com.trd.climate.room.ClientThermalStateCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Синхронизация живого состояния пассивных источников (Сервер → Клиент).
 * <p>
 * Без этого пакета клиентский дебаг-оверлей и ПКМ-термометр читают паспортные
 * значения из {@code HeatSourceDef}, а сервер — остывший источник с запасом,
 * и показания расходятся. Пакет содержит полный список состояний рядом с игроком;
 * клиент заменяет кэш целиком, поэтому исчезнувшие записи пропадают сами.
 */
public record SyncThermalStatesPayload(List<Entry> states) implements CustomPacketPayload {

    public record Entry(BlockPos pos, float reserve, boolean physical) {}

    public static final Type<SyncThermalStatesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("trd", "sync_thermal_states"));

    private static final StreamCodec<ByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Entry::pos,
            ByteBufCodecs.FLOAT, Entry::reserve,
            ByteBufCodecs.BOOL, Entry::physical,
            Entry::new
    );

    public static final StreamCodec<ByteBuf, SyncThermalStatesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ENTRY_CODEC.apply(ByteBufCodecs.list(512)), SyncThermalStatesPayload::states,
                    SyncThermalStatesPayload::new
            );

    public SyncThermalStatesPayload {
        states = states == null ? List.of() : List.copyOf(states);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncThermalStatesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientThermalStateCache.replace(payload.states()));
    }
}
