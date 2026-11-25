package com.trd.api.nature.climate.system.room;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Глобальный менеджер герметичных помещений.
 * Хранит активные изолированные комнаты для каждого измерения мира
 * и обеспечивает мгновенный поиск помещения по координатам сущности/блока O(1..N).
 */
public final class SealedRoomManager {

    private static final Map<ResourceKey<Level>, Map<BlockPos, SealedRoom>> ACTIVE_ROOMS = new ConcurrentHashMap<>();

    /**
     * Клиентский кэш комнат, присланных сервером (см. SyncRoomClimatePayload).
     * На клиенте блок-сущности контроллеров не содержат данных о комнате,
     * поэтому расчёт температуры использует этот снимок.
     * Хранится только для текущего измерения — сервер присылает комнаты рядом с игроком.
     */
    private static final Map<BlockPos, SealedRoom> CLIENT_ROOMS = new ConcurrentHashMap<>();

    private SealedRoomManager() {
    }

    private static Map<BlockPos, SealedRoom> roomsFor(Level level) {
        // На клиенте используем кэш синхронизации: ACTIVE_ROOMS заполняют только
        // серверные блок-сущности контроллеров.
        if (level.isClientSide()) {
            return CLIENT_ROOMS;
        }
        return ACTIVE_ROOMS.get(level.dimension());
    }

    /**
     * Заменяет клиентский кэш комнат (вызывается из обработчика SyncRoomClimatePayload).
     */
    public static void replaceClientRooms(java.util.List<SyncedRoom> rooms) {
        CLIENT_ROOMS.clear();
        for (SyncedRoom room : rooms) {
            CLIENT_ROOMS.put(room.getControllerPos(), room);
        }
    }

    /**
     * Очищает клиентский кэш комнат (смена измерения, выход из мира).
     */
    public static void clearClientRooms() {
        CLIENT_ROOMS.clear();
    }

    /**
     * Все комнаты измерения, чей объём находится в пределах {@code radius} от точки.
     * Используется сервером для синхронизации комнат с клиентом.
     */
    public static java.util.List<SealedRoom> getRoomsNear(Level level, BlockPos center, double radius) {
        java.util.List<SealedRoom> result = new java.util.ArrayList<>();
        if (level == null || center == null) {
            return result;
        }
        Map<BlockPos, SealedRoom> levelRooms = ACTIVE_ROOMS.get(level.dimension());
        if (levelRooms == null || levelRooms.isEmpty()) {
            return result;
        }
        double cx = center.getX() + 0.5;
        double cy = center.getY() + 0.5;
        double cz = center.getZ() + 0.5;
        for (SealedRoom room : levelRooms.values()) {
            net.minecraft.world.phys.AABB box = room.getInteriorBox();
            if (box == null || !room.isStructureValid()) {
                continue;
            }
            // Расстояние от точки до AABB (0, если точка внутри)
            double dx = Math.max(Math.max(box.minX - cx, 0), cx - box.maxX);
            double dy = Math.max(Math.max(box.minY - cy, 0), cy - box.maxY);
            double dz = Math.max(Math.max(box.minZ - cz, 0), cz - box.maxZ);
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                result.add(room);
            }
        }
        return result;
    }

    /**
     * Регистрирует активную герметичную комнату.
     */
    public static void register(Level level, BlockPos controllerPos, SealedRoom room) {
        if (level == null || controllerPos == null || room == null || level.isClientSide()) {
            return;
        }
        ACTIVE_ROOMS.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>())
                .put(controllerPos, room);
    }

    /**
     * Удаляет комнату при разрушении контроллера или нарушении герметичности.
     */
    public static void unregister(Level level, BlockPos controllerPos) {
        if (level == null || controllerPos == null || level.isClientSide()) {
            return;
        }
        Map<BlockPos, SealedRoom> levelRooms = ACTIVE_ROOMS.get(level.dimension());
        if (levelRooms != null) {
            levelRooms.remove(controllerPos);
        }
    }

    /**
     * Находит изолированное помещение, содержащее указанную точку мира.
     * 
     * @return SealedRoom если точка внутри герметичной комнаты, иначе null.
     */
    @Nullable
    public static SealedRoom findRoomAt(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        Map<BlockPos, SealedRoom> levelRooms = roomsFor(level);
        if (levelRooms == null || levelRooms.isEmpty()) {
            return null;
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        for (SealedRoom room : levelRooms.values()) {
            if (room.isStructureValid() && room.contains(pos)) {
                return room;
            }
        }

        return null;
    }

    /**
     * Находит герметичную комнату, если точка находится внутри, на границе оболочки
     * или в непосредственной близости (до 2 блоков от внешнего AABB).
     * Используется для перехвата событий разрушения/установки блоков и запуска дебаунс-пересчета.
     */
    @Nullable
    public static SealedRoom findRoomAtOrNear(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        Map<BlockPos, SealedRoom> levelRooms = roomsFor(level);
        if (levelRooms == null || levelRooms.isEmpty()) {
            return null;
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        for (SealedRoom room : levelRooms.values()) {
            if (room.isStructureValid() || room.isDirty()) {
                if (room.contains(pos) || room.isBoundary(pos)) {
                    return room;
                }
                if (room.getInteriorBox() != null && room.getInteriorBox().inflate(2.0).contains(x, y, z)) {
                    return room;
                }
            }
        }

        return null;
    }

    /**
     * Очищает комнаты для измерения при его выгрузке.
     */
    public static void clearLevel(Level level) {
        if (level != null) {
            ACTIVE_ROOMS.remove(level.dimension());
        }
    }
}
