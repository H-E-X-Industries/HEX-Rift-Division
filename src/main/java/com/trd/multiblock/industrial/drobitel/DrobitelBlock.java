package com.trd.multiblock.industrial.drobitel;

import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.item.ModItems;
import com.trd.multiblock.system.IMultiblockController;
import com.trd.multiblock.system.MultiblockStructureHelper;
import com.trd.multiblock.system.PartRole;
import com.trd.api.rotation.KineticNetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class DrobitelBlock extends BaseEntityBlock implements IMultiblockController {

    public static final MapCodec<DrobitelBlock> CODEC = simpleCodec(DrobitelBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static MultiblockStructureHelper helper;

    public DrobitelBlock(Properties properties) {
        super(properties.noOcclusion().strength(3.0f, 10.0f));
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
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        VoxelShape fullShape = getStructureHelper().generateShapeFromParts(facing);
        VoxelShape hole = Block.box(-8, 8, -12, 24, 32, 28);
        VoxelShape rotatedHole = MultiblockStructureHelper.rotateShape(hole, facing);
        return Shapes.join(fullShape, rotatedHole, BooleanOp.ONLY_FIRST);
    }

    @Override
    public MultiblockStructureHelper getStructureHelper() {
        if (helper == null) {
            Map<Character, Supplier<BlockState>> symbols = Map.of(
                    '#', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    'F', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    'B', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    '$', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    '@', () -> this.defaultBlockState()
            );
            Map<Character, PartRole> roles = Map.of(
                    '#', PartRole.DEFAULT,
                    'F', PartRole.KINETIC_PORT,
                    'B', PartRole.KINETIC_PORT,
                    '$', PartRole.CARGO_PORT,
                    '@', PartRole.CONTROLLER
            );

            helper = MultiblockStructureHelper.createFromLayersWithRoles(
                    new String[][]{
                            // y=0: F — передний порт, B — задний порт, $ — карго порты по 4 углам
                            {"$F$", "#@#", "$B$"},
                            {"###", "###", "###"}
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

            KineticNetworkManager manager = KineticNetworkManager.get((ServerLevel) level);
            manager.updateNetworkAfterPlace(pos);
            manager.updateNetworkAfterPlace(pos.relative(facing));
            manager.updateNetworkAfterPlace(pos.relative(facing.getOpposite()));
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DrobitelBlockEntity drobitel) {
                drobitel.dropContents();
            }
            Direction facing = state.getValue(FACING);

            KineticNetworkManager manager = KineticNetworkManager.get((ServerLevel) level);
            manager.updateNetworkAfterRemove(pos.relative(facing));
            manager.updateNetworkAfterRemove(pos.relative(facing.getOpposite()));
            manager.updateNetworkAfterRemove(pos);

            getStructureHelper().destroyStructure(level, pos, facing);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(ModItems.SCREWDRIVER.get())) {
            if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DrobitelBlockEntity drobitel) {
                InteractionResult res = drobitel.handleScrewdriver(player, hand);
                return res.consumesAction() ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            return ItemInteractionResult.CONSUME;
        }

        if (stack.is(ModItems.BLADE.get())) {
            if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DrobitelBlockEntity drobitel) {
                InteractionResult res = drobitel.handleBladeInsertion(player, hand);
                return res.consumesAction() ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            return ItemInteractionResult.CONSUME;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DrobitelBlockEntity drobitel) {
            player.openMenu(drobitel, pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DrobitelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.DROBITEL_BE.get(), DrobitelBlockEntity::serverTick);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal("Максимальная скорость: 2500 RPM").withStyle(ChatFormatting.GRAY));
    }
}
