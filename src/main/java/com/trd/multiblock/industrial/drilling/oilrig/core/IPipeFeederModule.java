package com.trd.multiblock.industrial.drilling.oilrig.core;

import net.minecraft.core.Direction;

/**
 * Податчик буровых труб (барабан PipeDrum и его аналоги).
 */
public interface IPipeFeederModule extends IRigModule {

    Direction getFeederFacing();

    /** Проверяет, есть ли готовая труба в позиции захвата (слот 0). */
    boolean hasReadyPipe();

    /** Забирает трубу из позиции захвата (слот 0). */
    boolean consumeReadyPipe();

    /** true, если барабан сейчас проворачивается на 60° или ждёт энергии для проворота. */
    boolean isIndexingOrPending();

    /** Запускает поворот барабана на 60° к следующему слоту (с расходом энергии). */
    void requestIndexNextSlot();
}
