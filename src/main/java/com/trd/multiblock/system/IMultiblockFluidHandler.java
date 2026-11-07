package com.trd.multiblock.system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Интерфейс для мультиблоков, части которых предоставляют флюидные возможности
 * в зависимости от роли части (PartRole) и стороны подключения (Direction).
 */
public interface IMultiblockFluidHandler {
    /**
     * @param side    сторона ЧАСТИ, через которую к ней подключаются (null = запрос без стороны).
     * @param role    роль части в структуре.
     * @param partPos позиция части в мире — нужен контроллеру, чтобы понять,
     *                порт это и смотрит ли грань наружу конструкции.
     */
    @Nullable
    IFluidHandler getCapabilityForPart(@Nullable Direction side, PartRole role, BlockPos partPos);
}
