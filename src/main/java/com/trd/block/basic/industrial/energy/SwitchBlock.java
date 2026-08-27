package com.trd.block.basic.industrial.energy;

import com.trd.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
import com.trd.api.energy.EnergyNetworkManager;
import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.energy.SwitchBlockEntity;

import javax.annotation.Nullable;

public class SwitchBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public SwitchBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(POWERED, false);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // Ручное переключение (работает как override, если редстоун не активен)
        boolean newPowered = !state.getValue(POWERED);
        setPoweredState(state, (ServerLevel) level, pos, newPowered, true);
        return InteractionResult.SUCCESS;
    }

    /**
     * Устанавливает POWERED напрямую. Если состояние не меняется — ничего не делает.
     */
    public void setPoweredState(BlockState state, ServerLevel level, BlockPos pos, boolean powered, boolean playSound) {
        if (state.getValue(POWERED) == powered) return;

        BlockState newState = state.setValue(POWERED, powered);
        level.setBlock(pos, newState, 3);

        EnergyNetworkManager manager = EnergyNetworkManager.get(level);
        if (powered) {
            manager.addNode(pos);
        } else {
            manager.removeNode(pos);
        }

        if (playSound) {
            level.playSound(null, pos, ModSounds.LEVER1.get(), SoundSource.BLOCKS, 0.3f, powered ? 1.0f : 0.9f);
        }
        level.updateNeighborsAt(pos, this);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean isMoving) {
        if (level.isClientSide) return;
        if (!(level.getBlockEntity(pos) instanceof SwitchBlockEntity switchEntity)) return;

        boolean signal = level.hasNeighborSignal(pos);
        if (signal == switchEntity.prevSignal) return;

        switchEntity.prevSignal = signal;
        switchEntity.setChanged();

        // Прямое управление: блок повторяет редстоун-сигнал
        setPoweredState(state, (ServerLevel) level, pos, signal, true);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide && !oldState.is(state.getBlock())) {
            if (level.getBlockEntity(pos) instanceof SwitchBlockEntity be) {
                boolean signal = level.hasNeighborSignal(pos);
                be.prevSignal = signal;
                be.setChanged();

                // При установке сразу синхронизируемся с редстоуном
                if (state.getValue(POWERED) != signal) {
                    setPoweredState(state, (ServerLevel) level, pos, signal, true);
                } else if (signal) {
                    // Если совпадает и включён — регистрируем в сети
                    EnergyNetworkManager.get((ServerLevel) level).addNode(pos);
                }
            }
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            EnergyNetworkManager.get((ServerLevel) level).removeNode(pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SwitchBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.SWITCH_BE.get(), SwitchBlockEntity::tick);
    }
}