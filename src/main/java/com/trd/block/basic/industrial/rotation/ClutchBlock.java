package com.trd.block.basic.industrial.rotation;

import com.trd.api.rotation.KineticNetworkManager;
import com.trd.api.rotation.ShaftDiameter;
import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.rotation.ClutchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
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
import org.jetbrains.annotations.Nullable;

public class ClutchBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty HAS_SHAFT = BooleanProperty.create("has_shaft");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ClutchBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HAS_SHAFT, false)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_SHAFT, POWERED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            boolean isPowered = level.hasNeighborSignal(pos);
            if (isPowered != state.getValue(POWERED)) {
                KineticNetworkManager manager = KineticNetworkManager.get((ServerLevel) level);
                manager.updateNetworkAfterRemove(pos);
                
                level.setBlock(pos, state.setValue(POWERED, isPowered), 3);
                
                manager.updateNetworkAfterPlace(pos);
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ClutchBlockEntity clutch)) return InteractionResult.PASS;
        ItemStack itemInHand = player.getItemInHand(hand);

        // 1. Вставка вала
        if (!clutch.hasShaft() && itemInHand.getItem() instanceof BlockItem blockItem) {
            if (blockItem.getBlock() instanceof ShaftBlock shaftBlock) {
                if (shaftBlock.getDiameter() == ShaftDiameter.HEAVY) return InteractionResult.FAIL;

                if (!level.isClientSide) {
                    KineticNetworkManager manager = KineticNetworkManager.get((ServerLevel) level);

                    clutch.insertShaft(shaftBlock.getMaterial(), shaftBlock.getDiameter());
                    level.setBlock(pos, state.setValue(HAS_SHAFT, true), 3);
                    if (!player.isCreative()) itemInHand.shrink(1);

                    manager.updateNetworkAfterRemove(pos);
                    manager.updateNetworkAfterPlace(pos);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // 2. Извлечение вала
        if (clutch.hasShaft() && itemInHand.isEmpty() && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                KineticNetworkManager manager = KineticNetworkManager.get((ServerLevel) level);

                clutch.removeShaft();
                level.setBlock(pos, state.setValue(HAS_SHAFT, false), 3);
                player.addItem(new ItemStack(com.trd.block.basic.ModBlocks.getShaft(clutch.getShaftMaterial(), clutch.getShaftDiameter()).get()));

                manager.updateNetworkAfterRemove(pos);
                manager.updateNetworkAfterPlace(pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && state.getBlock() != oldState.getBlock()) {
            KineticNetworkManager.get((ServerLevel) level).updateNetworkAfterPlace(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            super.onRemove(state, level, pos, newState, isMoving);
            KineticNetworkManager.get((ServerLevel) level).updateNetworkAfterRemove(pos);
            
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ClutchBlockEntity clutch && clutch.hasShaft()) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(com.trd.block.basic.ModBlocks.getShaft(clutch.getShaftMaterial(), clutch.getShaftDiameter()).get()));
            }
            return;
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClutchBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.CLUTCH_BE.get(), ClutchBlockEntity::serverTick);
    }
}
