package com.trd.block.basic.industrial.lighting;

import com.mojang.serialization.MapCodec;
import com.trd.api.energy.EnergyNetworkManager;
import com.trd.block.basic.industrial.energy.WireBlock;
import com.trd.block.entity.industrial.lighting.FloodlightBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

import java.util.Map;

/**
 * Промисловий поворотний прожектор (Floodlight).
 * Підтримує 6-стороннє кріплення до блоків та кабелів WireBlock (підлога, стеля, 4 стіни, ефект гірлянди),
 * підключення живлення ВИКЛЮЧНО до монтажної основи (підошви)
 * та динамічне 3D наведення за поглядом гравця (Yaw + Pitch).
 */
public class FloodlightBlock extends BaseEntityBlock {
    public static final MapCodec<FloodlightBlock> CODEC = simpleCodec(FloodlightBlock::new);
    public static final DirectionProperty MOUNT_FACE = BlockStateProperties.FACING;
    public static final DirectionProperty FACING = MOUNT_FACE; // Сумісність
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    // Хітбокси для 6 варіантів кріплення MOUNT_FACE (14×15×14 px)
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.UP,    Block.box(1.0, 0.0, 1.0, 15.0, 15.0, 15.0),
            Direction.DOWN,  Block.box(1.0, 1.0, 1.0, 15.0, 16.0, 15.0),
            Direction.NORTH, Block.box(1.0, 1.0, 1.0, 15.0, 15.0, 16.0),
            Direction.SOUTH, Block.box(1.0, 1.0, 0.0, 15.0, 15.0, 15.0),
            Direction.WEST,  Block.box(1.0, 1.0, 1.0, 16.0, 15.0, 15.0),
            Direction.EAST,  Block.box(0.0, 1.0, 1.0, 15.0, 15.0, 15.0)
    );

    public FloodlightBlock(Properties properties) {
        super(properties.lightLevel(state -> 0));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(MOUNT_FACE, Direction.UP)
                .setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MOUNT_FACE, LIT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(MOUNT_FACE), SHAPES.get(Direction.UP));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        BlockState state = this.defaultBlockState()
                .setValue(MOUNT_FACE, clickedFace)
                .setValue(LIT, false);
        if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
            return state;
        }
        return null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction mountFace = state.getValue(MOUNT_FACE);
        BlockPos supportPos = pos.relative(mountFace.getOpposite());
        BlockState support = level.getBlockState(supportPos);
        // Дозволяємо монтаж на кабелі (ефект гірлянди) та тверді грані
        if (support.getBlock() instanceof WireBlock) {
            return true;
        }
        return support.isFaceSturdy(level, supportPos, mountFace);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction == state.getValue(MOUNT_FACE).getOpposite() && !state.canSurvive(level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FloodlightBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return (lvl, pos, st, be) -> {
            if (be instanceof FloodlightBlockEntity floodlightBE) {
                if (lvl.isClientSide) {
                    floodlightBE.clientTick(lvl, pos, st);
                } else {
                    floodlightBE.serverTick((ServerLevel) lvl, pos, st);
                }
            }
        };
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!state.is(oldState.getBlock()) && !level.isClientSide()) {
            Direction connectionSide = state.getValue(MOUNT_FACE).getOpposite();
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
                return;
            }
            if (level.getBlockEntity(pos) instanceof FloodlightBlockEntity floodlightBE) {
                floodlightBE.markRaycastDirty();
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        return handleAimInteraction(state, level, pos, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        ItemInteractionResult res = handleAimInteraction(state, level, pos, player);
        return res.result();
    }

    private ItemInteractionResult handleAimInteraction(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof FloodlightBlockEntity floodlightBE) {
            if (player.isShiftKeyDown()) {
                Direction mount = state.getValue(MOUNT_FACE);
                float defYaw = switch (mount) {
                    case NORTH -> 180.0f;
                    case SOUTH -> 0.0f;
                    case WEST -> 90.0f;
                    case EAST -> 270.0f;
                    default -> player.getDirection().toYRot();
                };
                floodlightBE.setTargetOrientation(defYaw, 0.0f);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.25f, 1.6f);
                if (!level.isClientSide) {
                    player.displayClientMessage(
                            Component.literal("§6[Прожектор] §fСкидання кутів: §eYaw " + Math.round(defYaw) + "° / Pitch 0°"),
                            true
                    );
                }
            } else {
                float targetYaw = player.getYRot();
                float targetPitch = Mth.clamp(-player.getXRot(), -75.0f, 90.0f);
                floodlightBE.setTargetOrientation(targetYaw, targetPitch);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.25f, 1.6f);
                if (!level.isClientSide) {
                    player.displayClientMessage(
                            Component.literal("§6[Прожектор] §fНаведення: §eYaw " + Math.round(targetYaw) + "° / Pitch " + Math.round(targetPitch) + "°"),
                            true
                    );
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof FloodlightBlockEntity floodlightBE) {
                floodlightBE.cleanupLightBlocks();
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide()) {
                if (level instanceof ServerLevel serverLevel) {
                    EnergyNetworkManager.get(serverLevel).removeNode(pos);
                }
                if (level.getBlockEntity(pos) instanceof FloodlightBlockEntity floodlightBE) {
                    floodlightBE.cleanupLightBlocks();
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
            level.updateNeighborsAt(pos, state.getBlock());
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
