package com.trd.block.basic.industrial.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Вольфрамова лампа розжарення (Tungsten Lamp).
 * Рівень світла: 12.
 * Споживання: 6 од./такт.
 * Знос: 0.01f на randomTick (середній ресурс).
 */
public class TungstenLampBlock extends ElectricLampBlock {
    // Компактні VoxelShape хітбокси для Вольфрамової лампи (220В / А60) (6×10.5×6 px)
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.UP,    Block.box(5.0, 0.0, 5.0, 11.0, 10.5, 11.0),
            Direction.DOWN,  Block.box(5.0, 5.5, 5.0, 11.0, 16.0, 11.0),
            Direction.NORTH, Block.box(5.0, 5.0, 5.5, 11.0, 11.0, 16.0),
            Direction.SOUTH, Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 10.5),
            Direction.WEST,  Block.box(5.5, 5.0, 5.0, 16.0, 11.0, 11.0),
            Direction.EAST,  Block.box(0.0, 5.0, 5.0, 10.5, 11.0, 11.0)
    );

    public TungstenLampBlock(Properties properties,
                             Supplier<? extends Item> lampItemSupplier,
                             Supplier<? extends Block> socketBlockSupplier) {
        super(properties, 12, lampItemSupplier, socketBlockSupplier);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.UP));
    }

    @Override
    public long getConsumptionPerTick() {
        return 6L;
    }

    @Override
    public float getWearPerRandomTick() {
        return 0.01f;
    }
}
