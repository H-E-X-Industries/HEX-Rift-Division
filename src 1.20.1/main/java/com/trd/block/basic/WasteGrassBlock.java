package com.trd.block.basic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Выжженная трава водородной гранаты (waste_grass). Свойство {@link #DARKNESS} —
 * ступень затемнения (0 = обычная текстура на ободе, 7 = максимум темноты у эпицентра),
 * ровно как у мягкого базальта {@link CraterBasaltBlock}: затемнение делается цветовым
 * тинтом модели, без дубликатов текстур.
 */
public class WasteGrassBlock extends Block {

    public static final IntegerProperty DARKNESS = CraterBasaltBlock.DARKNESS;

    public WasteGrassBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(DARKNESS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DARKNESS);
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return true;
    }
}