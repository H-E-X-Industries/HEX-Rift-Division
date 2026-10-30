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
 * Modern solid-state industrial LED lamp fixture.
 * Light level: 15 (maximum).
 * Power consumption: 1 energy unit/tick (high efficiency).
 * Durability wear: 0.0f (no filament burnout).
 */
public class LedLampBlock extends ElectricLampBlock {
    // Compact voxel shapes for LED lamp fixture (6x10.2x6 pixels)
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
