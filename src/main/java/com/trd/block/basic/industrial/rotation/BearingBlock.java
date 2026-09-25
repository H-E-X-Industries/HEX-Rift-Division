package com.trd.block.basic.industrial.rotation;

import com.mojang.serialization.MapCodec;
import com.trd.api.rotation.KineticNetworkManager;
import com.trd.api.rotation.ShaftDiameter;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.industrial.rotation.BearingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class BearingBlock extends BaseEntityBlock {
    public static final MapCodec<BearingBlock> CODEC = simpleCodec(BearingBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty HAS_SHAFT = BooleanProperty.create("has_shaft");

    public BearingBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HAS_SHAFT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_SHAFT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BearingBlockEntity bearing)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (!bearing.hasShaft() && stack.getItem() instanceof BlockItem blockItem) {
            if (blockItem.getBlock() instanceof ShaftBlock shaftBlock) {
                if (shaftBlock.getDiameter() == ShaftDiameter.HEAVY) return ItemInteractionResult.FAIL;

                if (!level.isClientSide) {
                    KineticNetworkManager manager = KineticNetworkManager.get((ServerLevel) level);

                    bearing.insertShaft(shaftBlock.getMaterial(), shaftBlock.getDiameter());
                    level.setBlock(pos, state.setValue(HAS_SHAFT, true), 3);
                    if (!player.isCreative()) stack.shrink(1);

                    manager.updateNetworkAfterRemove(pos);
                    manager.updateNetworkAfterPlace(pos);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BearingBlockEntity bearing)) return InteractionResult.PASS;

        if (bearing.hasShaft() && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                KineticNetworkManager manager = KineticNetworkManager.get((ServerLevel) level);

                if (bearing.getShaftMaterial() != null && bearing.getShaftDiameter() != null) {
                    Block shaftBlock = ModBlocks.getShaft(bearing.getShaftMaterial(), bearing.getShaftDiameter()).get();
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(shaftBlock));
                }

                bearing.removeShaft();
                level.setBlock(pos, state.setValue(HAS_SHAFT, false), 3);

                manager.updateNetworkAfterRemove(pos);
                manager.updateNetworkAfterPlace(pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
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
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BearingBlockEntity bearing && bearing.hasShaft()) {
                if (bearing.getShaftMaterial() != null && bearing.getShaftDiameter() != null) {
                    Block shaftBlock = ModBlocks.getShaft(bearing.getShaftMaterial(), bearing.getShaftDiameter()).get();
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(shaftBlock));
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
            KineticNetworkManager.get((ServerLevel) level).updateNetworkAfterRemove(pos);
            return;
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BearingBlockEntity(pos, state);
    }
}
