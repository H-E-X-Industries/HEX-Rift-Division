package com.trd.api.metallurgy.system;

/**
 * Приёмник жидкого металла от литейного спуска (CastingDescent).
 * Реализуется и кастинговыми котлами (CastingPotBlockEntity), и
 * машиной непрерывного литья (CCMachineBlockEntity).
 */
public interface IMetalReceiver {
    /**
     * Можно ли принять металл данного типа в текущий момент.
     */
    boolean canAcceptMetal(Metal metal);

    /**
     * Добавить металл в буфер.
     * @return сколько реально принято
     */
    int addMetal(Metal metal, int amount);

    /**
     * Сколько ещё единиц металла можно принять.
     */
    int getRemainingCapacity();

    /**
     * Текущий тип металла в буфере (или null, если пусто).
     */
    Metal getCurrentMetal();

    /**
     * Степень заполнения буфера от 0.0 до 1.0.
     */
    float getFillLevel();
}
