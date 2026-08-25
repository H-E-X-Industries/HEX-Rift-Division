package com.trd.block.basic.industrial.energy;

import com.trd.api.energy.ConnectorTier;
import com.trd.block.entity.industrial.energy.ConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.Map;

public class ConnectorBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public final ConnectorTier tier;
    private final Map<Direction, VoxelShape> shapes;

    public ConnectorBlock(Properties properties, ConnectorTier tier) {
        super(properties);
        this.tier = tier;
        this.shapes = generateShapes(tier.width(), tier.height());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    // Динамическая генерация коллизий на основе ширины и высоты
    private Map<Direction, VoxelShape> generateShapes(double w, double h) {
        double min = (16.0 - w) / 2.0; // Центрируем по ширине
        double max = min + w;
        return Map.of(
                Direction.UP,    Block.box(min, 0, min, max, h, max),
                Direction.DOWN,  Block.box(min, 16 - h, min, max, 16, max),
                Direction.NORTH, Block.box(min, min, 16 - h, max, max, 16),
                Direction.SOUTH, Block.box(min, min, 0, max, max, h),
                Direction.WEST,  Block.box(16 - h, min, min, 16, max, max),
                Direction.EAST,  Block.box(0, min, min, h, max, max)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        BlockState support = level.getBlockState(supportPos);
        // Провода имеют тонкую форму и их грани не "sturdy" — разрешаем крепление к ним напрямую
        if (support.getBlock() instanceof WireBlock) return true;
        return support.isFaceSturdy(level, supportPos, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConnectorBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ConnectorBlockEntity connector) {
                connector.onRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}