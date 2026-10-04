package com.trd.block.basic.redstone;

import com.mojang.serialization.MapCodec;
import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.redstone.RedstoneRadioBlockEntity;
import com.trd.block.entity.redstone.RedstoneRadioReceiverBlockEntity;
import com.trd.block.entity.redstone.RedstoneRadioTransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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
import org.jetbrains.annotations.Nullable;

public class RedstoneRadioBlock extends BaseEntityBlock {
    // Два разных блока (передатчик и приёмник) делят класс, поэтому codec всегда
    // строит приёмник: он используется только для декодирования состояния в датапаке
    // и на подстановку блока из реестра никак не влияет.
    public static final MapCodec<RedstoneRadioBlock> CODEC = simpleCodec(p -> new RedstoneRadioBlock(p, false));

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
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace(); // антенна наружу
        BlockPos supportPos = context.getClickedPos().relative(facing.getOpposite());
        if (!context.getLevel().getBlockState(supportPos).isSolid()) {
            return null;
        }
        return this.defaultBlockState().setValue(FACING, facing).setValue(POWERED, false);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        return level.getBlockState(supportPos).isSolid();
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        // Не проверяем canSurvive во время движения поршня — мир временно неконсистентен
        if (!level.isClientSide && !isMoving && !state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
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

    // 1.21.1: Block#canConnectRedstone удалён. Разрешение подключать редстоун
    // теперь задаётся свойствами блока: мы копируем Blocks.REDSTONE_TORCH, который
    // не является проводником, так что пыль по-прежнему цепляется к обоим блокам.

    // 1.21.1: Block#use разделён на useItemOn/useWithoutItem, открытие меню — здесь.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof RedstoneRadioBlockEntity radio) {
                // IPlayerExtension#openMenu(MenuProvider, BlockPos) сам пишет позицию в буфер
                serverPlayer.openMenu(radio, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && state.getBlock() != oldState.getBlock()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RedstoneRadioBlockEntity radio) {
                radio.sendSyncPacket(); // синхронизация нового блока
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RedstoneRadioTransmitterBlockEntity transmitter) {
                transmitter.notifyReceiversOfRemoval();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return !isTransmitter;
    }

    @Override
    protected int getDirectSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Direction direction) {
        if (!isTransmitter) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RedstoneRadioReceiverBlockEntity receiver) {
                return receiver.getOutputSignal();
            }
        }
        return 0;
    }

    @Override
    protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Direction direction) {
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
            return createTickerHelper(type, ModBlockEntities.REDSTONE_RADIO_TRANSMITTER_BE.get(),
                    RedstoneRadioTransmitterBlockEntity::serverTick);
        } else {
            return createTickerHelper(type, ModBlockEntities.REDSTONE_RADIO_RECEIVER_BE.get(),
                    RedstoneRadioReceiverBlockEntity::serverTick);
        }
    }
}
