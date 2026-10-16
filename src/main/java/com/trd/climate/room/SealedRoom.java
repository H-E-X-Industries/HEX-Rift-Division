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
     * Действительна ли структура помещения (комната полностью герметична или временно имеет локальную утечку).
     * В отличие от полного отсутствия герметизации, комната с утечкой продолжает существовать,
     * сохраняя геометрию и плавно выравнивая температуру благодаря теплоемкости объема.
     */
    default boolean isStructureValid() {
        return isSealed();
    }

    /**
     * Имеется ли в данный момент утечка (пробоина в стене или открытая дверь).
     */
    default boolean hasLeak() {
        return !isSealed();
    }

    /**
     * Проверяет, находится ли указанная координата внутри изолированного помещения.
     * По умолчанию выполняет быструю проверку по AABB (Tier 1).
     * Контроллеры произвольной формы (Tier 2) переопределяют данный метод для точной поблочной проверки.
     */
    default boolean contains(BlockPos pos) {
        if (getInteriorBox() == null) return false;
        return getInteriorBox().contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /**
     * Проверяет, является ли блок внешней граничной оболочкой комнаты (стеной, полом, потолком).
     */
    default boolean isBoundary(BlockPos pos) {
        return false;
    }

    /**
     * Помечает геометрию комнаты как измененную (сломан или установлен блок).
     * Запускает отложенный таймер (дебаунс 3 секунды) для пересчета без потери TPS.
     */
    default void markDirty() {
    }

    /**
     * Находится ли комната в процессе ожидания дебаунс-пересчета геометрии.
     */
    default boolean isDirty() {
        return false;
    }
}
