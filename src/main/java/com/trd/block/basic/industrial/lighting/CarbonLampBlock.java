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
 * Early incandescent carbon filament lamp fixture.
 * Light level: 8.
 * Power consumption: 12 energy units/tick.
 * Durability wear: 0.04f per random tick (fast degradation).
 */
public class CarbonLampBlock extends ElectricLampBlock {
    // Compact voxel shapes for carbon filament lamp (6x11x6 pixels)
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.UP,    Block.box(5.0, 0.0, 5.0, 11.0, 11.0, 11.0),
            Direction.DOWN,  Block.box(5.0, 5.0, 5.0, 11.0, 16.0, 11.0),
            Direction.NORTH, Block.box(5.0, 5.0, 5.0, 11.0, 11.0, 16.0),
            Direction.SOUTH, Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 11.0),
            Direction.WEST,  Block.box(5.0, 5.0, 5.0, 16.0, 11.0, 11.0),
            Direction.EAST,  Block.box(0.0, 5.0, 5.0, 11.0, 11.0, 11.0)
    );

    public CarbonLampBlock(Properties properties,
                           Supplier<? extends Item> lampItemSupplier,
                           Supplier<? extends Block> socketBlockSupplier) {
        super(properties, 8, lampItemSupplier, socketBlockSupplier);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.UP));
    }

    @Override
    public long getConsumptionPerTick() {
        return 12L;
    }

    @Override
    public float getWearPerRandomTick() {
        return 0.04f;
    }
}
