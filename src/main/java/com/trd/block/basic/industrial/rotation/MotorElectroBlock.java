package com.trd.block.basic.industrial.rotation;

import com.mojang.serialization.MapCodec;
import com.trd.api.energy.EnergyNetworkManager;
import com.trd.api.rotation.KineticNetworkManager;
import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.rotation.MotorElectroBlockEntity;
import com.trd.menu.rotation.MotorElectroMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class MotorElectroBlock extends BaseEntityBlock {
    public static final MapCodec<MotorElectroBlock> CODEC = simpleCodec(MotorElectroBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public MotorElectroBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (!(level.getBlockEntity(pos) instanceof MotorElectroBlockEntity motor)) {
            return InteractionResult.PASS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new MenuProvider() {
                        @Override
                        public Component getDisplayName() {
                            return Component.translatable("gui.trd.motor_electro");
                        }

                        @Nullable
                        @Override
                        public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                            return new MotorElectroMenu(id, inv, motor, motor.dataAccess);
                        }
                    },
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block fromBlock, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, fromBlock, fromPos, isMoving);
        if (level.isClientSide) return;

        if (!(level.getBlockEntity(pos) instanceof MotorElectroBlockEntity motor)) return;

        boolean hasRedstoneSignal = level.hasNeighborSignal(pos);

        if (hasRedstoneSignal && !motor.isTriggered) {
            motor.isTriggered = true;
            motor.toggleDirection();
        } else if (!hasRedstoneSignal && motor.isTriggered) {
            motor.isTriggered = false;
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> beType) {
        return level.isClientSide
                ? createTickerHelper(beType, ModBlockEntities.MOTOR_ELECTRO_BE.get(), MotorElectroBlockEntity::clientTick)
                : createTickerHelper(beType, ModBlockEntities.MOTOR_ELECTRO_BE.get(), MotorElectroBlockEntity.createTicker());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            KineticNetworkManager.get((ServerLevel) level).updateNetworkAfterPlace(pos);
            EnergyNetworkManager.get((ServerLevel) level).addNode(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            super.onRemove(state, level, pos, newState, isMoving);
            KineticNetworkManager.get((ServerLevel) level).updateNetworkAfterRemove(pos);
            EnergyNetworkManager.get((ServerLevel) level).removeNode(pos);

            Direction.Axis axis = state.getValue(FACING).getAxis();
            ShaftBlock.checkAndBreakUnsupportedShafts(level, pos, axis);
            return;
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MotorElectroBlockEntity(pos, state);
    }
}
