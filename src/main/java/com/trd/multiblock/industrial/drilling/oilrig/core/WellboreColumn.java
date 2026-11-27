package com.trd.multiblock.industrial.drilling.oilrig.core;

/**
 * Осевой столбец скважины (мировые координаты X, Z центра проходки).
 * Все осевые модули одной вышки разделяют один и тот же WellboreColumn.
 */
public record WellboreColumn(int wellX, int wellZ) {
}
