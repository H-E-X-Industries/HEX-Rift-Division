package com.trd.api.fluids.system;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import javax.annotation.Nullable;

public class UnbucketableLiquidBlock extends LiquidBlock {

    public UnbucketableLiquidBlock(FlowingFluid pFluid, Properties pProperties) {
        super(pFluid, pProperties);
    }

    // Блокируем ванильную логику зачерпывания ведром!
    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor pLevel, BlockPos pPos, BlockState pState) {
        // Мы НЕ удаляем блок жидкости и просто возвращаем "пустоту"
        return ItemStack.EMPTY;
    }
}