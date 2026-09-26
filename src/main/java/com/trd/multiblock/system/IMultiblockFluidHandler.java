package com.trd.multiblock.system;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Интерфейс для мультиблоков, части которых предоставляют флюидные возможности
 * в зависимости от роли части (PartRole) и стороны подключения (Direction).
 */
public interface IMultiblockFluidHandler {
    @Nullable
    IFluidHandler getCapabilityForPart(@Nullable Direction side, PartRole role);
}
