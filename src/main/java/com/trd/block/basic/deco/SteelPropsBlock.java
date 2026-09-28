package com.trd.block.basic.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SteelPropsBlock extends FullOBlock {

    // 1.25 пикселя: 1.25/16 = 0.078125
    private static final double INSET = 1.25 / 16.0;

    // Стойка почти на всю грань блока: 6 одинаковых граней по 14 пикселей
    private static final VoxelShape[] SHAPES = new VoxelShape[6];

    static {
        for (Direction dir : Direction.values()) {
            SHAPES[dir.get3DDataValue()] = calculateShape(dir);
        }
    }

    public SteelPropsBlock(Properties properties) {
        super(properties);
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
        double minX = INSET, minY = INSET, minZ = INSET;
        double maxX = 1.0 - INSET, maxY = 1.0 - INSET, maxZ = 1.0 - INSET;

        // Отступаем грань, смотрящую наружу по facing
        switch (facing) {
            case DOWN -> minY = 0;
            case UP -> maxY = 1;
            case NORTH -> minZ = 0;
            case SOUTH -> maxZ = 1;
            case WEST -> minX = 0;
            case EAST -> maxX = 1;
        }

        // Противоположная грань уходит вглубь блока (стойка примыкает к опоре)
        switch (facing) {
            case DOWN -> maxY = 1;
            case UP -> minY = 0;
            case NORTH -> maxZ = 1;
            case SOUTH -> minZ = 0;
            case WEST -> maxX = 1;
            case EAST -> minX = 0;
        }

        return Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
