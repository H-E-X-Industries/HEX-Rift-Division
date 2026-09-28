package com.trd.block.basic.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WireFenceBlock extends FenceBlock {

    public static final TagKey<Block> CONNECTS_TO = TagKey.create(Registries.BLOCK,
            new ResourceLocation("trd", "wire_fence_connections"));

    private static final VoxelShape POST = box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

    private static final VoxelShape PANEL_X = box(0.0, 0.0, 6.75, 16.0, 16.0, 9.25);
    private static final VoxelShape PANEL_Z = box(6.75, 0.0, 0.0, 9.25, 16.0, 16.0);

    private static final VoxelShape HALF_X_EAST = box(8.0, 0.0, 6.75, 16.0, 16.0, 9.25);
    private static final VoxelShape HALF_X_WEST = box(0.0, 0.0, 6.75, 8.0, 16.0, 9.25);
    private static final VoxelShape HALF_Z_NORTH = box(6.75, 0.0, 0.0, 9.25, 16.0, 8.0);
    private static final VoxelShape HALF_Z_SOUTH = box(6.75, 0.0, 8.0, 9.25, 16.0, 16.0);

    public WireFenceBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    protected VoxelShape postShape() {
        return POST;
    }

    @Override
    public boolean connectsTo(BlockState state, boolean isSticky, Direction direction) {
        if (isExceptionForConnection(state)) {
            return false;
        }
        if (isSticky) {
            return true;
        }
        Block block = state.getBlock();
        return block.getClass() == WireFenceBlock.class
                || block instanceof SteelPropsBlock
                || state.is(CONNECTS_TO)
                || state.canOcclude();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean north = state.getValue(NORTH);
        boolean south = state.getValue(SOUTH);
        boolean east = state.getValue(EAST);
        boolean west = state.getValue(WEST);

        boolean straightEW = east && west && !north && !south;
        boolean straightNS = north && south && !east && !west;

        if (straightEW) {
            return PANEL_X;
        }
        if (straightNS) {
            return PANEL_Z;
        }

        VoxelShape shape = postShape();
        if (east) {
            shape = Shapes.or(shape, HALF_X_EAST);
        }
        if (west) {
            shape = Shapes.or(shape, HALF_X_WEST);
        }
        if (north) {
            shape = Shapes.or(shape, HALF_Z_NORTH);
        }
        if (south) {
            shape = Shapes.or(shape, HALF_Z_SOUTH);
        }
        return shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
}