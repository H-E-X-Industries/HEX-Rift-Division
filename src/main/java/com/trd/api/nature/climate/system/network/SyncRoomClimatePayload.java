package com.trd.api.nature.climate.system.network;

import com.trd.api.nature.climate.system.room.SealedRoomManager;
import com.trd.api.nature.climate.system.room.SyncedRoom;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Синхронизация герметичных комнат рядом с игроком (Сервер -> Клиент).
 * <p>
 * Клиент не имеет доступа к блок-сущностям контроллеров комнат на сервере,
 * поэтому без этого пакета клиентский дебаг-оверлей и термометры внутри
 * помещения показывали уличную температуру вместо комнатной.
 * Пакет содержит <b>полный список</b> комнат в радиусе наблюдения: клиент
 * заменяет свой кэш целиком, поэтому удалённые на сервере комнаты исчезают сами.
 */
public record SyncRoomClimatePayload(List<RoomData> rooms) implements CustomPacketPayload {

    /**
     * Слепок одной комнаты: позиция контроллера, границы, объём, температура, герметичность.
     */
    public record RoomData(BlockPos controllerPos,
                           double minX, double minY, double minZ,
                           double maxX, double maxY, double maxZ,
                           int volume, float temperature, boolean sealed) {
    }

    public static final Type<SyncRoomClimatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("trd", "sync_room_climate"));

    private static final StreamCodec<ByteBuf, RoomData> ROOM_CODEC = StreamCodec.of(
            (buf, room) -> {
                BlockPos.STREAM_CODEC.encode(buf, room.controllerPos());
                buf.writeDouble(room.minX());
                buf.writeDouble(room.minY());
                buf.writeDouble(room.minZ());
                buf.writeDouble(room.maxX());
                buf.writeDouble(room.maxY());
                buf.writeDouble(room.maxZ());
                buf.writeInt(room.volume());
                buf.writeFloat(room.temperature());
                buf.writeBoolean(room.sealed());
            },
            buf -> new RoomData(
                    BlockPos.STREAM_CODEC.decode(buf),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readInt(), buf.readFloat(), buf.readBoolean()
            )
    );

    public static final StreamCodec<ByteBuf, SyncRoomClimatePayload> STREAM_CODEC = StreamCodec.composite(
            ROOM_CODEC.apply(ByteBufCodecs.list(64)), SyncRoomClimatePayload::rooms,
            SyncRoomClimatePayload::new
    );

    public SyncRoomClimatePayload {
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Обработчик пакета на клиенте: заменяет кэш комнат целиком.
     */
    public static void handle(SyncRoomClimatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            List<SyncedRoom> synced = new ArrayList<>(payload.rooms().size());
            for (RoomData room : payload.rooms()) {
                synced.add(new SyncedRoom(
                        room.controllerPos(),
                        new AABB(room.minX(), room.minY(), room.minZ(),
                                room.maxX(), room.maxY(), room.maxZ()),
                        room.volume(), room.temperature(), room.sealed()
                ));
            }
            SealedRoomManager.replaceClientRooms(synced);
        });
    }
}
