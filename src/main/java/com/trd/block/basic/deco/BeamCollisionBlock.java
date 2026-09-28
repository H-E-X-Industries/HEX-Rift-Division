package com.trd.block.basic.deco;

import com.mojang.serialization.MapCodec;
import com.trd.block.entity.deco.BeamCollisionBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BeamCollisionBlock extends BaseEntityBlock {
    public static final MapCodec<BeamCollisionBlock> CODEC = simpleCodec(BeamCollisionBlock::new);

    private static final VoxelShape SHAPE = Shapes.empty();

    public BeamCollisionBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BeamCollisionBlock> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof BeamCollisionBlockEntity beam) {
            return beam.getCollisionShape();
        }
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof BeamCollisionBlockEntity beam) {
            return beam.getCollisionShape();
        }
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new BeamCollisionBlockEntity(pPos, pState);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof BeamCollisionBlockEntity collisionBE) {
                    // Ломаем все балки, которые проходят через этот блок
                    collisionBE.breakEntireBeam(level);
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    // Срабатывает, когда ломается любой соседний блок (например, наша бетонная/стальная опора)
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BeamCollisionBlockEntity collisionBE) {
            List<BeamCollisionBlockEntity.BeamData> beamsCopy = new ArrayList<>(collisionBE.getBeams());
            for (BeamCollisionBlockEntity.BeamData data : beamsCopy) {
                BlockPos startAnchor = BlockPos.containing(data.startPos);
                BlockPos endAnchor = BlockPos.containing(data.endPos);

                // Если один из опорных блоков стал воздухом (или жидкостью)
                if (level.isEmptyBlock(startAnchor) || level.isEmptyBlock(endAnchor)) {
                    collisionBE.breakEntireBeam(level);
                    break;
                }
            }
        }
    }
}
