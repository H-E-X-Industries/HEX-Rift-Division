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
 * Світлодіодна лампа (LED Lamp).
 * Рівень світла: 15.
 * Споживання: 1 од./такт (мінімальне споживання).
 * Знос: 0.0f (без фізичного зносу).
 */
public class LedLampBlock extends ElectricLampBlock {
    // Компактні VoxelShape хітбокси для Світлодіодної LED лампи (6×10.2×6 px)
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.UP,    Block.box(5.0, 0.0, 5.0, 11.0, 10.2, 11.0),
            Direction.DOWN,  Block.box(5.0, 5.8, 5.0, 11.0, 16.0, 11.0),
            Direction.NORTH, Block.box(5.0, 5.0, 5.8, 11.0, 11.0, 16.0),
            Direction.SOUTH, Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 10.2),
            Direction.WEST,  Block.box(5.8, 5.0, 5.0, 16.0, 11.0, 11.0),
            Direction.EAST,  Block.box(0.0, 5.0, 5.0, 10.2, 11.0, 11.0)
    );

    public LedLampBlock(Properties properties,
                        Supplier<? extends Item> lampItemSupplier,
                        Supplier<? extends Block> socketBlockSupplier) {
        super(properties, 15, lampItemSupplier, socketBlockSupplier);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.UP));
    }

    @Override
    public long getConsumptionPerTick() {
        return 1L;
    }

    @Override
    public float getWearPerRandomTick() {
        return 0.0f;
    }
}
