package com.trd.api.rotation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collections;
import java.util.List;

/**
 * Базовый интерфейс для всего, что вращается.
 * Используется KineticNetwork для управления скоростью.
 */
public interface Rotational {

    /**
     * Роль блока в кинетической сети.
     *
     * PASSIVE   — валы, подшипники, тахометры: просто передают вращение, вклад в момент = 0.
     * GENERATOR — моторы, ветряки: производят момент. Их getTorque() суммируется в пул сети.
     * CONSUMER  — статоры, жернова: потребляют момент из пула сети через getConsumedTorque().
     *             Если суммарное потребление превышает пул генераторов — сеть перегружается и останавливается.
     */
    enum NodeRole { PASSIVE, GENERATOR, CONSUMER }

    /** Роль этого блока в кинетической сети. По умолчанию — пассивный узел. */
    default NodeRole getNodeRole() {
        if (isSource()) return NodeRole.GENERATOR;
        if (getConsumedTorque() > 0) return NodeRole.CONSUMER;
        return NodeRole.PASSIVE;
    }

    // Получение текущих данных (для расчетов в сети)
    long getSpeed();
    long getTorque();

    // Установка данных (вызывается сетью после recalculate)
    void setSpeed(long speed);

    // Лимиты (нужны для проверки поломки/перегрузки)
    long getMaxSpeed();
    long getMaxTorque();

    // Данные для физики
    double getInertiaContribution();
    long getMaxTorqueTolerance();

    default long getConsumedTorque() { return 0; }

    default long getGeneratedSpeed() {
        return 0;
    }

    default long getVisualSpeed() {
        return getSpeed();
    }

    default boolean isSource() {
        return false;
    }

    default Direction[] getPropagationDirections() {
        return Direction.values();
    }

    default boolean canConnectMechanically(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        return true;
    }

    default void setNetworkScale(float scale) {}
    default float getNetworkScale() { return 1.0f; }

    default List<BlockPos> getPotentialConnections(Level level, BlockPos myPos) {
        return Collections.emptyList();
    }

    default float calculateTransmissionRatio(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        return 1.0f;
    }

    default void forceSyncVisuals(Level level, BlockPos pos) {
        if (!level.isClientSide && this instanceof BlockEntity be) {
            level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
        }
    }
}
