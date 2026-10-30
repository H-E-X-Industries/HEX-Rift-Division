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
 * Compact fluorescent lamp (CFL) fixture.
 * Light level: 13.
 * Power consumption: 3 energy units/tick.
 * Durability wear: 0.003f per random tick (high longevity, features ignition starter flicker).
 */
public class FluorescentLampBlock extends ElectricLampBlock {
    // Compact voxel shapes for CFL fixture (6x11.5x6 pixels)
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.UP,    Block.box(5.0, 0.0, 5.0, 11.0, 11.5, 11.0),
            Direction.DOWN,  Block.box(5.0, 4.5, 5.0, 11.0, 16.0, 11.0),
            Direction.NORTH, Block.box(5.0, 5.0, 4.5, 11.0, 11.0, 16.0),
            Direction.SOUTH, Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 11.5),
            Direction.WEST,  Block.box(4.5, 5.0, 5.0, 16.0, 11.0, 11.0),
            Direction.EAST,  Block.box(0.0, 5.0, 5.0, 11.5, 11.0, 11.0)
    );

    public FluorescentLampBlock(Properties properties,
                                Supplier<? extends Item> lampItemSupplier,
                                Supplier<? extends Block> socketBlockSupplier) {
        super(properties, 13, lampItemSupplier, socketBlockSupplier);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.UP));
    }

    @Override
    public long getConsumptionPerTick() {
        return 3L;
    }

    @Override
    public float getWearPerRandomTick() {
        return 0.003f;
    }
}
