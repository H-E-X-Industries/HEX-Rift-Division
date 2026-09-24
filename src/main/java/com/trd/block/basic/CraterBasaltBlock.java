package com.trd.block.basic;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Мягкий базальт кратера водородной гранаты (basalt_soft / basalt_soft_2 / basalt_soft_3).
 * Свойство {@link #DARKNESS} хранит ступень затемнения (0 = обычная текстура на ободе,
 * 7 = максимум темноты у эпицентра). Затемнение выполняется КОДОМ — цветовым тинтом
 * в бирт-модели, поэтому не нужна куча дубликатов текстур по ступеням.
 */
public class CraterBasaltBlock extends Block {

    /** Максимальная ступень затемнения (всего ступеней: 0..MAX_DARK). */
    public static final int MAX_DARK = 7;

    public static final IntegerProperty DARKNESS = IntegerProperty.create("darkness", 0, MAX_DARK);

    public CraterBasaltBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(DARKNESS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DARKNESS);
    }
}