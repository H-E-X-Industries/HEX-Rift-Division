package com.trd.block.basic.industrial;

import com.trd.block.entity.industrial.conveyors.ConveyorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class ConveyorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    protected static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null; // Убрали тиканье, теперь всем управляет ConveyorNetworkManager
    }

    public ConveyorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.trd.api.conveyor.ConveyorNetworkManager.get(serverLevel).addBlock(pos, state.getValue(FACING));
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConveyorBlockEntity(pos, state);
    }

    // 4. Возможность забрать предмет ПКМ пустой рукой
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        if (!level.isClientSide() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.trd.api.conveyor.ConveyorNetworkManager manager = com.trd.api.conveyor.ConveyorNetworkManager.get(serverLevel);
            com.trd.api.conveyor.ConveyorNetwork net = manager.getNetworkFor(pos);
            if (net != null) {
                double index = net.getPath().indexOf(pos);
                java.util.Iterator<com.trd.api.conveyor.ConveyorItem> iterator = net.getItems().iterator();
                while (iterator.hasNext()) {
                    com.trd.api.conveyor.ConveyorItem item = iterator.next();
                    if (item.getProgress() >= index && item.getProgress() < index + 1) {
                        if (player.getItemInHand(hand).isEmpty()) {
                            player.setItemInHand(hand, item.getStack());
                        } else if (!player.getInventory().add(item.getStack())) {
                            player.drop(item.getStack(), false);
                        }
                        iterator.remove();
                        manager.syncNetwork(net);
                        manager.setDirty();
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        } else {
            com.trd.api.conveyor.client.ClientConveyorManager.ClientNetworkData netData = com.trd.api.conveyor.client.ClientConveyorManager.getNetworkFor(pos);
            if (netData != null) {
                double index = netData.getIndexFor(pos);
                for (com.trd.api.conveyor.ConveyorItem item : netData.items) {
                    if (item.getProgress() >= index && item.getProgress() < index + 1) {
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        }
        return InteractionResult.PASS;
    }

    // 5. При столкновении с предметом, забираем его на ленту
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.Entity entity) {
        if (!level.isClientSide && entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.trd.api.conveyor.ConveyorNetwork net = com.trd.api.conveyor.ConveyorNetworkManager.get(serverLevel).getNetworkFor(pos);
            if (net != null) {
                double index = net.getPath().indexOf(pos);
                if (index >= 0) {
                    if (!itemEntity.isRemoved() && net.tryInsertItem(itemEntity.getItem().copy(), index + 0.5)) {
                        itemEntity.discard();
                    }
                }
            }
        }
        super.entityInside(state, level, pos, entity);
    }

    // 6. При разрушении конвейера предметы выпадают (обрабатывается в менеджере)
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                com.trd.api.conveyor.ConveyorNetworkManager.get(serverLevel).removeBlock(pos);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}