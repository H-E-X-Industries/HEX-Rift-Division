package com.trd.block.basic.industrial.lighting;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import com.trd.api.components.LampComponent;
import com.trd.api.components.ModDataComponents;
import com.trd.api.energy.EnergyNetworkManager;
import com.trd.block.basic.industrial.energy.WireBlock;
import com.trd.block.entity.industrial.lighting.SocketBlockEntity;
import com.trd.item.industrial.lighting.LampItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Industrial E27 carbolite lamp socket fixture.
 * Supports mounting in 6 directions on solid blocks or directly onto {@link WireBlock} cable runs (string light effect).
 * Power and cables connect strictly to the base/socket mount face.
 */
public class EmptySocketBlock extends Block implements EntityBlock {
    public static final MapCodec<EmptySocketBlock> CODEC = simpleCodec(EmptySocketBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    // Compact voxel shapes for E27 socket fixture (6x4x6 bounding box across 6 directions)
    private static final Map<Direction, VoxelShape> SHAPES = ImmutableMap.<Direction, VoxelShape>builder()
            .put(Direction.UP, Block.box(5.0, 0.0, 5.0, 11.0, 4.0, 11.0))
            .put(Direction.DOWN, Block.box(5.0, 12.0, 5.0, 11.0, 16.0, 11.0))
            .put(Direction.NORTH, Block.box(5.0, 5.0, 12.0, 11.0, 11.0, 16.0))
            .put(Direction.SOUTH, Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 4.0))
            .put(Direction.WEST, Block.box(12.0, 5.0, 5.0, 16.0, 11.0, 11.0))
            .put(Direction.EAST, Block.box(0.0, 5.0, 5.0, 4.0, 11.0, 11.0))
            .build();

    public EmptySocketBlock(Properties properties) {
        super(properties.lightLevel(state -> 0));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SocketBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.UP));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        BlockState state = this.defaultBlockState().setValue(FACING, facing);
        if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
            return state;
        }
        return null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        BlockState support = level.getBlockState(supportPos);
        // Allow attachment to wires (string-light effect) or solid block faces
        if (support.getBlock() instanceof WireBlock) {
            return true;
        }
        return support.isFaceSturdy(level, supportPos, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!state.is(oldState.getBlock()) && !level.isClientSide()) {
            Direction connectionSide = state.getValue(FACING).getOpposite();
            BlockPos wirePos = pos.relative(connectionSide);
            level.neighborChanged(wirePos, state.getBlock(), pos);
            level.updateNeighborsAt(pos, state.getBlock());
            if (level instanceof ServerLevel serverLevel) {
                EnergyNetworkManager.get(serverLevel).addNode(pos);
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide()) {
            if (!state.canSurvive(level, pos)) {
                dropResources(state, level, pos, level.getBlockEntity(pos));
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                EnergyNetworkManager.get(serverLevel).removeNode(pos);
            }
            super.onRemove(state, level, pos, newState, isMoving);
            level.updateNeighborsAt(pos, state.getBlock());
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof LampItem lampItem) {
            if (!level.isClientSide()) {
                ElectricLampBlock targetBlock = lampItem.getLampBlock();
                if (targetBlock != null) {
                    LampComponent component = stack.getOrDefault(ModDataComponents.LAMP_DATA.get(), LampComponent.DEFAULT);
                    boolean isBroken = component.isBroken();
                    float durability = component.durability();
                    Direction facing = state.getValue(FACING);

                    long savedEnergy = 0L;
                    if (level.getBlockEntity(pos) instanceof SocketBlockEntity oldBE) {
                        savedEnergy = oldBE.getEnergyStored();
                    }

                    BlockState newLampState = targetBlock.defaultBlockState()
                            .setValue(ElectricLampBlock.FACING, facing)
                            .setValue(ElectricLampBlock.LIT, false)
                            .setValue(ElectricLampBlock.BROKEN, isBroken)
                            .setValue(ElectricLampBlock.LIGHT_LEVEL, 0);

                    level.setBlock(pos, newLampState, Block.UPDATE_ALL);

                    if (level.getBlockEntity(pos) instanceof SocketBlockEntity socketBE) {
                        socketBE.setEnergyStored(savedEnergy);
                        socketBE.setDurability(durability);
                        socketBE.setBroken(isBroken);
                        socketBE.updateLitState();
                    }

                    level.playSound(null, pos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 0.9f, 1.2f);
                    level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, 1.4f);

                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
