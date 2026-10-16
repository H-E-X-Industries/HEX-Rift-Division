package com.trd.climate.room;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Результат валидации герметичности кубического помещения (Tier 1).
 */
public record ValidationResult(
        boolean isSuccess,
        int volume,
        @Nullable BlockPos leakPos,
        @Nullable String leakBlockName,
        boolean isDoor,
        @Nullable String errorMessage
) {
    public static ValidationResult success(int volume) {
        return new ValidationResult(true, volume, null, null, false, null);
    }

    public static ValidationResult leak(BlockPos pos, BlockState state) {
        boolean isDoor = state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock;
        String name = state.isAir() ? "Air" : state.getBlock().getName().getString();
        return new ValidationResult(false, 0, pos, name, isDoor, "Обнаружена утечка в оболочке комнаты");
    }

    public static ValidationResult fail(String message) {
        return new ValidationResult(false, 0, null, null, false, message);
    }
}
