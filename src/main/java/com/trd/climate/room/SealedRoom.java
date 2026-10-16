package com.trd.climate.room;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Интерфейс герметичного изолированного помещения.
 */
public interface SealedRoom {

    /** Позиция контроллера термоизоляции */
    BlockPos getControllerPos();

    /** Границы внутреннего пространства комнаты (без учета внешних стен) */
    AABB getInteriorBox();

    /** Внутренний объем воздуха в блоках */
    int getVolume();

    /** Текущая температура воздуха в комнате в °C */
    float getRoomTemperature();

    /** Герметична ли комната в данный момент */
    boolean isSealed();

    /**
     * Проверяет, находится ли указанная координата внутри изолированного помещения.
     * По умолчанию выполняет быструю проверку по AABB (Tier 1).
     * Контроллеры произвольной формы (Tier 2) переопределяют данный метод для точной поблочной проверки.
     */
    default boolean contains(BlockPos pos) {
        if (getInteriorBox() == null) return false;
        return getInteriorBox().contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
}
