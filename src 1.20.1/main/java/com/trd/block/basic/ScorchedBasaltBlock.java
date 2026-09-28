package com.trd.block.basic;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Базальт, запёкшийся в кратере водородной гранаты.
 * Свойство {@link #LIGHT} хранит ступень осветления (0 = темный у центра, 7 = +50% у края кратера),
 * по которому подбирается текстура с соответствующим градиентом.
 */
public class ScorchedBasaltBlock extends Block {

    /** Максимальная ступень осветления (всего ступеней: 0..MAX_LIGHT). */
    public static final int MAX_LIGHT = 7;

    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, MAX_LIGHT);

    public ScorchedBasaltBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIGHT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGHT);
    }
}