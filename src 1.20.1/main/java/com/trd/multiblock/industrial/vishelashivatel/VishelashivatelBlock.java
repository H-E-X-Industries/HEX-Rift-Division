package com.trd.multiblock.industrial.vishelashivatel;

import com.trd.api.rotation.KineticNetworkManager;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.multiblock.system.IMultiblockController;
import com.trd.multiblock.system.MultiblockStructureHelper;
import com.trd.multiblock.system.PartRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Выщелащиватель — контроллер мультиблока 3x3x1:
 * <pre>
 * #$#
 * $@$   $ — жидкостный порт, @ — контроллер, # — мультиблок-парт
 * #$#
 * </pre>
 */
public class VishelashivatelBlock extends BaseEntityBlock implements IMultiblockController {

    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    private static MultiblockStructureHelper helper;

    public VishelashivatelBlock(Properties properties) {
        super(properties.noOcclusion().strength(3.0f, 10.0f));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Форма контроллера = объединение форм всех партов мультиблока.
     * Парты (MultiblockPartBlock) делегируют сюда со сдвигом, поэтому
     * форма обязана накрывать весь мультиблок — иначе у партов не будет
     * коллизии и обводки.
     */
    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return getStructureHelper().generateShapeFromParts(state.getValue(FACING));
    }

    // ===================== МУЛЬТИБЛОК 3x3x1 =====================

    @Override
    public MultiblockStructureHelper getStructureHelper() {
        if (helper == null) {
            Map<Character, Supplier<BlockState>> symbols = Map.of(
                    '#', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    '$', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    '@', () -> this.defaultBlockState()
            );
            Map<Character, PartRole> roles = Map.of(
                    '#', PartRole.DEFAULT,
                    '$', PartRole.FLUID_INPUT,
                    '@', PartRole.CONTROLLER
            );

            helper = MultiblockStructureHelper.createFromLayersWithRoles(
                    new String[][]{
                            {"#$#", "$@$", "#$#"}
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING);
            getStructureHelper().placeStructure(level, pos, facing, this);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            KineticNetworkManager.get((ServerLevel) level).updateNetworkAfterPlace(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof VishelashivatelBlockEntity leacher) {
                leacher.dropContents();
            }
            KineticNetworkManager.get((ServerLevel) level).updateNetworkAfterRemove(pos);
            Direction facing = state.getValue(FACING);
            getStructureHelper().destroyStructure(level, pos, facing);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // ===================== ВЗАИМОДЕЙСТВИЕ =====================

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.sidedSuccess(true);
        if (level.getBlockEntity(pos) instanceof VishelashivatelBlockEntity leacher) {
            net.minecraftforge.network.NetworkHooks.openScreen(
                    (net.minecraft.server.level.ServerPlayer) player, leacher, pos
            );
        }
        return InteractionResult.CONSUME;
    }

    // ===================== BLOCK ENTITY =====================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VishelashivatelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.VISHELASHIVATEL_BE.get(), VishelashivatelBlockEntity::serverTick);
    }
    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter level, java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(net.minecraft.network.chat.Component.literal("Максимальная скорость: 1500 RPM").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
