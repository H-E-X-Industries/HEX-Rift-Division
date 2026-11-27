package com.trd.multiblock.industrial.drilling;

import com.mojang.serialization.MapCodec;
import com.trd.api.industiral.rotation.KineticNetworkManager;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.multiblock.industrial.drilling.oilrig.core.OilRigAssembly;
import com.trd.multiblock.industrial.drilling.oilrig.core.OilRigManager;
import com.trd.multiblock.industrial.drilling.oilrig.core.WellboreColumn;
import com.trd.multiblock.system.IMultiblockController;
import com.trd.multiblock.system.MultiblockStructureHelper;
import com.trd.multiblock.system.PartRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Буровая труба (DrillPipe, мультиблок 1×1×4).
 * Слои снизу вверх: '@' (y=0, контроллер), '#', '#', '#' (y=1..3).
 */
public class DrillPipeBlock extends BaseEntityBlock implements IMultiblockController {

    public static final MapCodec<DrillPipeBlock> CODEC = simpleCodec(DrillPipeBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty HORIZONTAL = BooleanProperty.create("horizontal");
    private static volatile MultiblockStructureHelper helper;
    private static volatile MultiblockStructureHelper horizontalHelper;

    public DrillPipeBlock(Properties properties) {
        super(properties.noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HORIZONTAL, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HORIZONTAL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        boolean sneaking = context.isSecondaryUseActive();
        if (clickedFace.getAxis().isHorizontal()) {
            if (sneaking) {
                return this.defaultBlockState()
                        .setValue(FACING, context.getHorizontalDirection())
                        .setValue(HORIZONTAL, false);
            }
            return this.defaultBlockState()
                    .setValue(FACING, clickedFace)
                    .setValue(HORIZONTAL, true);
        }
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(HORIZONTAL, sneaking);
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
                            {"@"},
                            {"#"},
                            {"#"},
                            {"#"}
                    },
                    symbols,
                    () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    roles
            );
        }
        return helper;
    }

    private MultiblockStructureHelper getHorizontalStructureHelper() {
        if (horizontalHelper == null) {
            Map<Character, Supplier<BlockState>> symbols = Map.of(
                    '#', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    '@', () -> this.defaultBlockState()
            );
            Map<Character, PartRole> roles = Map.of(
                    '#', PartRole.DEFAULT,
                    '@', PartRole.CONTROLLER
            );
            horizontalHelper = MultiblockStructureHelper.createFromLayersWithRoles(
                    new String[][]{
                            {
                                    "#",
                                    "#",
                                    "#",
                                    "@"
                            }
                    },
                    symbols,
                    () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    roles
            );
        }
        return horizontalHelper;
    }

    @Override
    public MultiblockStructureHelper getStructureHelper(BlockState state) {
        if (state.hasProperty(HORIZONTAL) && state.getValue(HORIZONTAL)) {
            return getHorizontalStructureHelper();
        }
        return getStructureHelper();
    }

    @Override
    public PartRole getPartRole(BlockPos localOffset) {
        return PartRole.DEFAULT;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getStructureHelper(state).generateShapeFromParts(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getStructureHelper(state).generateShapeFromParts(state.getValue(FACING));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            Direction facing = state.getValue(FACING);
            getStructureHelper(state).placeStructure(serverLevel, pos, facing, this);
            if (!state.getValue(HORIZONTAL)) {
                KineticNetworkManager.get(serverLevel).updateNetworkAfterPlace(pos);
                notifyRigWellboreChanged(serverLevel, pos, null);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            boolean horizontal = state.getValue(HORIZONTAL);
            if (!horizontal) {
                KineticNetworkManager.get(serverLevel).updateNetworkAfterRemove(pos);
            }
            Direction facing = state.getValue(FACING);
            getStructureHelper(state).destroyStructure(serverLevel, pos, facing);
            if (!horizontal) {
                notifyRigWellboreChanged(serverLevel, pos, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static void notifyRigWellboreChanged(ServerLevel level, BlockPos pipePos, @Nullable BlockPos removedPos) {
        OilRigAssembly asm = OilRigManager.get(level).getAssembly(new WellboreColumn(pipePos.getX(), pipePos.getZ()));
        if (asm != null && asm.getDrive() != null && asm.getDrive().isModuleValid()) {
            asm.getDrive().onWellborePipeChanged(level, removedPos);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DrillPipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.DRILL_PIPE_BE.get()) {
            return null;
        }
        if (level.isClientSide) {
            return (lvl, pos, st, be) -> ((DrillPipeBlockEntity) be).clientTick();
        }
        return null;
    }
}
