package com.trd.multiblock.industrial.drilling.oilrig.core;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Базовый контракт любого модуля в составе модульной нефтевышки.
 */
public interface IRigModule {

    /** Мировая позиция контроллера модуля. */
    BlockPos getRigPos();

    /** Осевой столбец скважины (wellX, wellZ), к которому относится модуль. */
    WellboreColumn getWellboreColumn();

    RigModuleRole getModuleRole();

    default boolean isAxialModule() {
        RigModuleRole role = getModuleRole();
        return role == RigModuleRole.WELLHEAD
                || role == RigModuleRole.BLOWOUT_PREVENTER
                || role == RigModuleRole.DRILL_DRIVE;
    }

    void setAssembly(@Nullable OilRigAssembly assembly);

    @Nullable
    OilRigAssembly getAssembly();

    boolean isModuleValid();

    /** Вызывается при изменении состояния сборки вышки, чтобы модуль мог обновить состояние/синхронизацию. */
    default void onAssemblyStateChanged(RigAssemblyState newState) {}
}
