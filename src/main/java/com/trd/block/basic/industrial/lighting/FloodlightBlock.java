package com.trd.block.basic.industrial.lighting;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.lighting.FloodlightBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class FloodlightBlock extends BaseEntityBlock {
    public static final MapCodec<FloodlightBlock> CODEC = simpleCodec(FloodlightBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    // Форми корпусу прожектора з кронштейном на 6 сторін (~10x10x12 пікселів)
    private static final Map<Direction, VoxelShape> SHAPES = ImmutableMap.<Direction, VoxelShape>builder()
            .put(Direction.UP, Block.box(3.0, 0.0, 2.0, 13.0, 12.0, 14.0))
            .put(Direction.DOWN, Block.box(3.0, 4.0, 2.0, 13.0, 16.0, 14.0))
            .put(Direction.NORTH, Block.box(3.0, 2.0, 4.0, 13.0, 14.0, 16.0))
            .put(Direction.SOUTH, Block.box(3.0, 2.0, 0.0, 13.0, 14.0, 12.0))
            .put(Direction.WEST, Block.box(4.0, 2.0, 3.0, 16.0, 14.0, 13.0))
            .put(Direction.EAST, Block.box(0.0, 2.0, 3.0, 12.0, 14.0, 13.0))
            .build();

    public FloodlightBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(LIT) ? 4 : 0));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Спрямований у бік погляду гравця (від очей гравця)
        Direction facing = context.getNearestLookingDirection().getOpposite();
        return this.defaultBlockState().setValue(FACING, facing).setValue(LIT, false);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FloodlightBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) return null;
        return createTickerHelper(blockEntityType, ModBlockEntities.FLOODLIGHT_BE.get(),
                (lvl, p, st, be) -> be.serverTick((ServerLevel) lvl, p, st));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof FloodlightBlockEntity floodlightBE) {
                floodlightBE.markRaycastDirty();
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof FloodlightBlockEntity floodlightBE) {
                floodlightBE.cleanupLightBlock();
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof FloodlightBlockEntity floodlightBE) {
                floodlightBE.cleanupLightBlock();
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
