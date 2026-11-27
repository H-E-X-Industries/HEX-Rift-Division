package com.trd.multiblock.industrial.drilling.oilrig.core;

import com.trd.api.nature.ore.oil.OilComposition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Устьевой модуль (буровая крестовина MudCross и её аналоги).
 * Хранит буфер скважинного флюида, параметры жилы, давление и обработку аварии.
 */
public interface IWellheadModule extends IRigModule {

    BlockPos getWellheadCenterPos();

    int getFluidAmount();

    int getFluidCapacity();

    int fillWellheadFluid(FluidStack stack, boolean simulate);

    /**
     * Формирует и заливает скважинный флюид на основе параметров жилы в буфер устья.
     *
     * @return фактически принятый объём (mB)
     */
    int extractWellheadFluid(int amountMb, @Nullable OilComposition composition, boolean simulate);

    @Nullable
    OilComposition getReservoirComposition();

    @Nullable
    BlockPos getActivePocketPos();

    @Nullable
    UUID getActiveVeinId();

    void bindReservoir(@Nullable OilComposition composition, @Nullable BlockPos pocketPos, @Nullable UUID veinId);

    float getPressureBar();

    void setPressureBar(float pressureBar);

    /** Эффект стравливания газа при достижении 200 бар (свист + частицы). */
    void onGasVentTick(ServerLevel level);

    /** Аварийная детонация при достижении 250 бар. */
    void onBlowoutDetonate(ServerLevel level, @Nullable IDrillDriveModule drive);
}
