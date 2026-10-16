package com.trd.climate.room;

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

    private SealedRoomManager() {
    }

    /**
     * Регистрирует активную герметичную комнату.
     */
    public static void register(Level level, BlockPos controllerPos, SealedRoom room) {
        if (level == null || controllerPos == null || room == null) {
            return;
        }
        ACTIVE_ROOMS.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>())
                .put(controllerPos, room);
    }

    /**
     * Удаляет комнату при разрушении контроллера или нарушении герметичности.
     */
    public static void unregister(Level level, BlockPos controllerPos) {
        if (level == null || controllerPos == null) {
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
        Map<BlockPos, SealedRoom> levelRooms = ACTIVE_ROOMS.get(level.dimension());
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
        Map<BlockPos, SealedRoom> levelRooms = ACTIVE_ROOMS.get(level.dimension());
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
