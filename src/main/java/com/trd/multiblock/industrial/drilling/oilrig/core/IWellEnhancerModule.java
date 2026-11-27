package com.trd.multiblock.industrial.drilling.oilrig.core;

import net.minecraft.server.level.ServerLevel;

/**
 * Хук для будущих модулей интенсификации добычи (SteamInjector, FrackingModule и т.д.).
 */
public interface IWellEnhancerModule extends IRigModule {

    default void onExtractionTick(ServerLevel level, RigExtractionContext context) {
    }
}
