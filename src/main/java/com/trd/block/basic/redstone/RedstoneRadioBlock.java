package com.trd.block.basic.redstone;

import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.redstone.RedstoneRadioBlockEntity;
import com.trd.block.entity.redstone.RedstoneRadioReceiverBlockEntity;
import com.trd.block.entity.redstone.RedstoneRadioTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

public class RedstoneRadioBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape SHAPE_UP = Block.box(6, 0, 6, 10, 10, 10);
    private static final VoxelShape SHAPE_DOWN = Block.box(6, 6, 6, 10, 16, 10);
    private static final VoxelShape SHAPE_NORTH = Block.box(6, 6, 6, 10, 10, 16);
    private static final VoxelShape SHAPE_SOUTH = Block.box(6, 6, 0, 10, 10, 10);
    private static final VoxelShape SHAPE_EAST = Block.box(0, 6, 6, 10, 10, 10);
    private static final VoxelShape SHAPE_WEST = Block.box(6, 6, 6, 16, 10, 10);

    private final boolean isTransmitter;

    public RedstoneRadioBlock(Properties properties, boolean isTransmitter) {
        super(properties);
        this.isTransmitter = isTransmitter;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeForFacing(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeForFacing(state.getValue(FACING));
    }

    private VoxelShape getShapeForFacing(Direction facing) {
        return switch (facing) {
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RedstoneRadioBlockEntity radio) {
                net.minecraftforge.network.NetworkHooks.openScreen((net.minecraft.server.level.ServerPlayer) player, radio, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && state.getBlock() != oldState.getBlock()) {
            level.updateNeighborsAt(pos, this);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            super.onRemove(state, level, pos, newState, isMoving);
            level.updateNeighborsAt(pos, this);
            return;
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide && isTransmitter) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RedstoneRadioTransmitterBlockEntity transmitter) {
                int signal = level.getBestNeighborSignal(pos);
                transmitter.setPowered(signal > 0);
                transmitter.setLastSignalStrength(signal);
            }
        }
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return !isTransmitter;
    }

    @Override
    public int getDirectSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Direction direction) {
        if (!isTransmitter) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RedstoneRadioReceiverBlockEntity receiver) {
                return receiver.getOutputSignal();
            }
        }
        return 0;
    }

    @Override
    public int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Direction direction) {
        return getDirectSignal(state, level, pos, direction);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (isTransmitter) {
            return new RedstoneRadioTransmitterBlockEntity(pos, state);
        } else {
            return new RedstoneRadioReceiverBlockEntity(pos, state);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        if (isTransmitter) {
            return createTickerHelper(type, ModBlockEntities.REDSTONE_RADIO_TRANSMITTER_BE.get(), RedstoneRadioTransmitterBlockEntity::serverTick);
        } else {
            return createTickerHelper(type, ModBlockEntities.REDSTONE_RADIO_RECEIVER_BE.get(), RedstoneRadioReceiverBlockEntity::serverTick);
        }
    }
}