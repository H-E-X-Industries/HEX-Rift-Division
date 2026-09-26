package com.trd.block.basic;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Мягкий базальт кратера водородной гранаты.
 *
 * <p>Свойство {@link #DARKNESS} хранит ступень затемнения (0 = обычная текстура на ободе,
 * 7 = максимум темноты у эпицентра). Затемнение выполняется КОДОМ — цветовым тинтом
 * в бирт-модели, поэтому не нужна куча дубликатов текстур по ступеням.
 *
 * <p>Свойство {@link #VARIANT} выбирает одну из {@link #VARIANT_COUNT} текстур того же блока
 * ({@code basalt_soft}, {@code basalt_soft_2}, {@code basalt_soft_3}, {@code basalt_soft_4}) —
 * раньше это были четыре отдельных блока. Воронка расставляет вариант по своему паттерну
 * (центр / обод / случайный микс), игрок при установке получает случайный.
 *
 * <p>Блоки {@code basalt_soft_2/3/4} остались в реестре ради старых миров, но новой воронке
 * больше не выдаются и из креатива убраны.
 */
public class CraterBasaltBlock extends Block {

    /** Максимальная ступень затемнения (всего ступеней: 0..MAX_DARK). */
    public static final int MAX_DARK = 7;

    public static final IntegerProperty DARKNESS = IntegerProperty.create("darkness", 0, MAX_DARK);

    /** Сколько текстур у одного мягкого базальта (basalt_soft / _2 / _3 / _4). */
    public static final int VARIANT_COUNT = 4;

    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, VARIANT_COUNT - 1);

    public CraterBasaltBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(DARKNESS, 0)
                .setValue(VARIANT, 0));
    }

    /** Установка игроком: случайная текстура из четырёх. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(VARIANT, context.getLevel().getRandom().nextInt(VARIANT_COUNT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DARKNESS, VARIANT);
    }
}