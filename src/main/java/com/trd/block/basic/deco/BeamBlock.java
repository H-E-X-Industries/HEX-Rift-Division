package com.trd.block.basic.deco;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BeamBlock extends DirectionalBlock {

    public static final MapCodec<BeamBlock> CODEC = simpleCodec(BeamBlock::new);

    // 3 пикселя = 3/16 = 0.1875
    private static final double THIN_INSET = 3.0 / 16.0;

    private static final VoxelShape[] SHAPES = new VoxelShape[6];

    static {
        for (Direction dir : Direction.values()) {
            SHAPES[dir.get3DDataValue()] = calculateShape(dir);
        }
    }

    public BeamBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected MapCodec<? extends BeamBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get3DDataValue()];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getShape(state, level, pos, CollisionContext.empty());
    }

    private static VoxelShape calculateShape(Direction facing) {
        double minX = 0, minY = 0, minZ = 0;
        double maxX = 1, maxY = 1, maxZ = 1;

        switch (facing) {
            case NORTH, SOUTH -> {
                // Балка по Z, ужимаем бока по X, Y и Z полные
                minX = THIN_INSET;
                maxX = 1.0 - THIN_INSET;
            }
            case WEST, EAST -> {
                // Балка по X, ужимаем бока по Z, Y и X полные
                minZ = THIN_INSET;
                maxZ = 1.0 - THIN_INSET;
            }
            case UP, DOWN -> {
                // Балка по Y, ужимаем бока по X и Z
                minX = THIN_INSET;
                maxX = 1.0 - THIN_INSET;
                minZ = THIN_INSET;
                maxZ = 1.0 - THIN_INSET;
            }
        }

        return Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
