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
}
