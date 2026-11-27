package com.trd.multiblock.industrial.drilling.oilrig.core;

import net.minecraft.server.level.ServerLevel;

/**
 * Хук для будущих противовыбросовых превенторов (BOP),
 * устанавливаемых в осевую колонну вышки между крестовиной и платформой.
 */
public interface IBlowoutPreventerModule extends IRigModule {

    default void onPressureTick(ServerLevel level, RigPressureContext context) {
    }
}
