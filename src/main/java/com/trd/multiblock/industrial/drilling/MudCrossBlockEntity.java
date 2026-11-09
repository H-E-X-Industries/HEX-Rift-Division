package com.trd.multiblock.industrial.drilling;

import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MudCrossBlockEntity extends BlockEntity {

    public MudCrossBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MUD_CROSS_BE.get(), pos, state);
    }
}
