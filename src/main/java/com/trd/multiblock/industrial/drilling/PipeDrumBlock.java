package com.trd.multiblock.industrial.drilling;

import com.mojang.serialization.MapCodec;
import com.trd.api.industiral.energy.EnergyNetworkManager;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.multiblock.industrial.drilling.oilrig.core.OilRigManager;
import com.trd.multiblock.system.IMultiblockController;
import com.trd.multiblock.system.MultiblockStructureHelper;
import com.trd.multiblock.system.PartRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Револьверный барабан для буровых труб (PipeDrum, 5 слоёв по Y).
 * Слой 0 (3×2): " @ " / "###" (широкой стенкой впритык к RotaryTable с локальной -Z).
 * Слои 1–4: "   " / " # " (коллизия центрального вала 1×1×4).
 */
public class PipeDrumBlock extends BaseEntityBlock implements IMultiblockController {

    public static final MapCodec<PipeDrumBlock> CODEC = simpleCodec(PipeDrumBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static volatile MultiblockStructureHelper helper;

    public PipeDrumBlock(Properties properties) {
        super(properties.noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Внешняя энергопринимающая сторона '@' обращена наружу к игроку, стенка '###' уходит к столу
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public MultiblockStructureHelper getStructureHelper() {
        if (helper == null) {
            Map<Character, Supplier<BlockState>> symbols = Map.of(
                    '#', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    '@', () -> this.defaultBlockState()
            );
            Map<Character, PartRole> roles = Map.of(
                    '#', PartRole.DEFAULT,
                    '@', PartRole.CONTROLLER
            );
            helper = MultiblockStructureHelper.createFromLayersWithRoles(
                    new String[][]{
                            {
                                    " @ ",
                                    "###"
                            },
                            {
                                    "   ",
                                    " # "
                            },
                            {
                                    "   ",
                                    " # "
                            },
                            {
                                    "   ",
                                    " # "
                            },
                            {
                                    "   ",
                                    " # "
                            }
                    },
                    symbols,
                    () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    roles
            );
        }
        return helper;
    }

    @Override
    public PartRole getPartRole(BlockPos localOffset) {
        return PartRole.DEFAULT;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getStructureHelper().generateShapeFromParts(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getStructureHelper().generateShapeFromParts(state.getValue(FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!PipeDrumBlockEntity.isDrillPipeItem(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof PipeDrumBlockEntity drum)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 lookEnd = eye.add(player.getViewVector(1.0f).scale(player.blockInteractionRange() + 1.0));
        int slot = drum.raycastEmptySlot(eye, lookEnd);
        if (slot < 0) {
            slot = drum.findNearestEmptySlot(hit.getLocation());
        }
        if (slot < 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            if (drum.insertPipeIntoSlot(slot)) {
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.55f, 1.15f);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            Direction facing = state.getValue(FACING);
            getStructureHelper().placeStructure(serverLevel, pos, facing, this);

            if (serverLevel.getBlockEntity(pos) instanceof PipeDrumBlockEntity drum) {
                OilRigManager.get(serverLevel).onModuleAdded(serverLevel, drum);
                EnergyNetworkManager.get(serverLevel).addNode(pos);
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            if (level.getBlockEntity(pos) instanceof PipeDrumBlockEntity drum) {
                drum.markBrokenInCreative();
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            if (level.getBlockEntity(pos) instanceof PipeDrumBlockEntity drum) {
                if (!drum.isBrokenInCreative()) {
                    int count = drum.getLoadedPipeCount();
                    Item pipeItem = PipeDrumBlockEntity.getDrillPipeItem();
                    if (count > 0 && pipeItem != null) {
                        Containers.dropItemStack(serverLevel, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, new ItemStack(pipeItem, count));
                    }
                }
            }
            OilRigManager.get(serverLevel).onModuleRemoved(serverLevel, pos);
            EnergyNetworkManager.get(serverLevel).removeNode(pos);

            Direction facing = state.getValue(FACING);
            getStructureHelper().destroyStructure(serverLevel, pos, facing);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeDrumBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.PIPE_DRUM_BE.get()) {
            return null;
        }
        if (level.isClientSide) {
            return (lvl, pos, st, be) -> ((PipeDrumBlockEntity) be).clientTick();
        }
        return (lvl, pos, st, be) -> PipeDrumBlockEntity.serverTick(lvl, pos, st, (PipeDrumBlockEntity) be);
    }
}
