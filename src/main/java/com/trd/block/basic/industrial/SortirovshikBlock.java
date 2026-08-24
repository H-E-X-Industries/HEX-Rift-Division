package com.trd.block.basic.industrial;

import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.conveyors.SortirovshikBlockEntity;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

/**
 * Конвейерный сортировщик.
 * Блок 1x1, НЕ является частью конвейерной цепи — посредник (как вставщик/извлекатель).
 * Ориентация жёстко фиксирована: красная сторона всегда сверху, маджента снизу,
 * оранжевая/жёлтая/зелёная/циановая — по бокам. Блок не поворачивается к игроку.
 */
public class SortirovshikBlock extends BaseEntityBlock {
    protected static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public SortirovshikBlock(Properties properties) {
        super(properties);
    }

    // Нет BlockStateProperties — состояние всегда одно, "смотрит в одну сторону"
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState();
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SortirovshikBlockEntity sorter) {
                NetworkHooks.openScreen(serverPlayer, sorter, buf -> buf.writeBlockPos(pos));
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SortirovshikBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.SORTIROVSHIK_BE.get(), SortirovshikBlockEntity::tick);
    }
}
