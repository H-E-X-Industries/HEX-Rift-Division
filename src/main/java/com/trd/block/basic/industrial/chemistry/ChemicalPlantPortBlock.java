package com.trd.block.basic.industrial.chemistry;

import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.chemistry.ChemicalPlantPortBlockEntity;
import com.trd.block.entity.industrial.chemistry.ChemicalPlantReactionChamberBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class ChemicalPlantPortBlock extends BaseEntityBlock {

    public ChemicalPlantPortBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction dir = context.getHorizontalDirection().getOpposite();
        BlockPos frontPos = context.getClickedPos().relative(dir);
        if (context.getLevel().getBlockEntity(frontPos) instanceof ChemicalPlantReactionChamberBlockEntity) {
            return this.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, dir);
        }
        return this.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, dir);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChemicalPlantPortBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.CHEMICAL_PLANT_PORT_BE.get(),
                ChemicalPlantPortBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ChemicalPlantPortBlockEntity port) {
                NetworkHooks.openScreen((ServerPlayer) player, port, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}