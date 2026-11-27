package com.trd.multiblock.industrial.drilling;

import com.mojang.serialization.MapCodec;
import com.trd.api.industiral.rotation.KineticNetworkManager;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.multiblock.industrial.drilling.oilrig.core.OilRigManager;
import com.trd.multiblock.system.IMultiblockController;
import com.trd.multiblock.system.MultiblockStructureHelper;
import com.trd.multiblock.system.PartRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Роторный стол буровой установки (RotaryTable, 3×3×1).
 * Управляет буровой колонной, кинетическим входом '%' справа (45–75 RPM),
 * редстоуном на любом блоке, сборкой вышки и возвратом ресурсов при демонтаже.
 */
public class RotaryTableBlock extends BaseEntityBlock implements IMultiblockController {

    public static final MapCodec<RotaryTableBlock> CODEC = simpleCodec(RotaryTableBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static volatile MultiblockStructureHelper helper;

    public RotaryTableBlock(Properties properties) {
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
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
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
                    '%', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    '@', () -> this.defaultBlockState()
            );
            Map<Character, PartRole> roles = Map.of(
                    '#', PartRole.DEFAULT,
                    '%', PartRole.KINETIC_PORT,
                    '@', PartRole.CONTROLLER
            );
            helper = MultiblockStructureHelper.createFromLayersWithRoles(
                    new String[][]{
                            {
                                    "###",
                                    "# %",
                                    "#@#"
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
        Direction facing = state.getValue(FACING);
        return getStructureHelper().generateShapeFromParts(facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            Direction facing = state.getValue(FACING);
            getStructureHelper().placeStructure(serverLevel, pos, facing, this);

            if (serverLevel.getBlockEntity(pos) instanceof RotaryTableBlockEntity rotary) {
                OilRigManager.get(serverLevel).onModuleAdded(serverLevel, rotary);
                KineticNetworkManager kinetic = KineticNetworkManager.get(serverLevel);
                kinetic.updateNetworkAfterPlace(pos);
                kinetic.updateNetworkAfterPlace(rotary.getKineticPortPos());
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            if (level.getBlockEntity(pos) instanceof RotaryTableBlockEntity rotary) {
                rotary.markBrokenInCreative();
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            BlockPos portPos = pos.offset(MultiblockStructureHelper.rotate(RotaryTableBlockEntity.LOCAL_KINETIC_PORT_FROM_CTRL, state.getValue(FACING)));
            if (level.getBlockEntity(pos) instanceof RotaryTableBlockEntity rotary) {
                portPos = rotary.getKineticPortPos();
                rotary.onControllerRemoved(serverLevel);
            }
            OilRigManager.get(serverLevel).onModuleRemoved(serverLevel, pos);
            KineticNetworkManager kinetic = KineticNetworkManager.get(serverLevel);
            kinetic.updateNetworkAfterRemove(pos);
            kinetic.updateNetworkAfterRemove(portPos);

            Direction facing = state.getValue(FACING);
            getStructureHelper().destroyStructure(serverLevel, pos, facing);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RotaryTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.ROTARY_TABLE_BE.get()) {
            return null;
        }
        if (level.isClientSide) {
            return (lvl, pos, st, be) -> ((RotaryTableBlockEntity) be).clientTick();
        }
        return (lvl, pos, st, be) -> RotaryTableBlockEntity.serverTick(lvl, pos, st, (RotaryTableBlockEntity) be);
    }
}
