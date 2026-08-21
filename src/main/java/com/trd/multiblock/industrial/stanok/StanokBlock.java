package com.trd.multiblock.industrial.stanok;

import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.multiblock.system.IMultiblockController;
import com.trd.multiblock.system.MultiblockStructureHelper;
import com.trd.multiblock.system.PartRole;
import com.trd.api.rotation.KineticNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Блок-контроллер кинетического станка (stanok).
 *
 * Структура 3W × 2H × 2D:
 *
 *  Y=0 (нижний слой), вид сверху (контроллер @ смотрит на север):
 *    Z=0  Z=1
 *  X=-1: %    #    (кинетический порт West)
 *  X= 0: #    @    (дефолт    контроллер)
 *  X=+1: %    #    (кинетический порт East)
 *
 * В координатах MultiblockStructureHelper (строки = X слева→вправо, столбцы = Z вперёд→назад):
 *   Layer Y=0: ["%#%", "#@#"]  — ряд Z=0: X: West/Center/East; ряд Z=1: X: default/ctrl/default
 *   Layer Y=1: ["###", "###"]
 *
 * Контроллер @ находится на позиции X=0, Y=0, Z=1 в локальных координатах,
 * то есть смотрит на север (facing=NORTH).
 *
 * Кинетические порты % — на Z=0, X=±1 → с запада и востока от контроллера.
 */
public class StanokBlock extends BaseEntityBlock implements IMultiblockController {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static MultiblockStructureHelper helper;

    public StanokBlock(BlockBehaviour.Properties properties) {
        super(properties.noOcclusion().strength(3.0f, 10.0f));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Контроллер смотрит на игрока
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // Рендер через Flywheel
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                               BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return getStructureHelper().generateShapeFromParts(facing);
    }

    @Override
    public MultiblockStructureHelper getStructureHelper() {
        if (helper == null) {
            // Строки массива = Z (от 0 до 1, то есть Z=0 ряд первый, Z=1 ряд второй)
            // Символы в строке = X (слева направо, от -1 до +1)
            // %=кин. порт, #=пустышка, @=контроллер
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
                            // Y=0: Z=0 ряд, Z=1 ряд
                            {"%#%", "#@#"},
                            // Y=1: все дефолт
                            {"###", "###"}
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                             @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING);
            getStructureHelper().placeStructure(level, pos, facing, this);

            KineticNetworkManager manager = KineticNetworkManager.get((ServerLevel) level);
            manager.updateNetworkAfterPlace(pos);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof StanokBlockEntity sbe) {
                manager.updateNetworkAfterPlace(sbe.getWestPortPos());
                manager.updateNetworkAfterPlace(sbe.getEastPortPos());
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            KineticNetworkManager manager = KineticNetworkManager.get((ServerLevel) level);
            
            if (be instanceof StanokBlockEntity stanok) {
                stanok.dropContents();
                manager.updateNetworkAfterRemove(stanok.getWestPortPos());
                manager.updateNetworkAfterRemove(stanok.getEastPortPos());
            }
            manager.updateNetworkAfterRemove(pos);

            Direction facing = state.getValue(FACING);
            getStructureHelper().destroyStructure(level, pos, facing);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                  Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StanokBlockEntity stanok) {
            NetworkHooks.openScreen((ServerPlayer) player, stanok, pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StanokBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.STANOK_BE.get(),
                StanokBlockEntity::serverTick);
    }
}
