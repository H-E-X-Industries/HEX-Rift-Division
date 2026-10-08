package com.trd.block.basic.industrial.rotation;

import com.mojang.serialization.MapCodec;
import com.trd.api.energy.EnergyNetworkManager;
import com.trd.api.rotation.KineticNetworkManager;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.rotation.StatorBlockEntity;
import com.trd.item.ModItems;
import com.trd.item.industrial.energy.StatorCoilItem;
import com.trd.multiblock.system.IMultiblockController;
import com.trd.multiblock.system.MultiblockStructureHelper;
import com.trd.multiblock.system.PartRole;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class StatorBlock extends BaseEntityBlock implements IMultiblockController {
    public static final MapCodec<StatorBlock> CODEC = simpleCodec(StatorBlock::new);
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    private static MultiblockStructureHelper helper;

    public StatorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(AXIS, Direction.Axis.Z));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, AXIS);
    }

    @Override
    public MultiblockStructureHelper getStructureHelper() {
        if (helper == null) {
            Map<Character, Supplier<BlockState>> symbols = Map.of(
                    '#', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    'E', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    'O', () -> this.defaultBlockState()
            );
            Map<Character, PartRole> roles = Map.of(
                    '#', PartRole.DEFAULT,
                    'E', PartRole.ENERGY_CONNECTOR,
                    'O', PartRole.CONTROLLER
            );
            helper = MultiblockStructureHelper.createFromLayersWithRoles(
                    new String[][]{
                            {"EOE"},   // базовый слой: энергопорты снизу для скрытой проводки
                            {"E E"},
                            {"#E#"}
                    },
                    symbols,
                    () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    roles
            );
        }
        return helper;
    }

    @Override
    public PartRole getPartRole(BlockPos pos) {
        return PartRole.DEFAULT;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        Direction.Axis axis = state.getValue(AXIS);
        return getStructureHelper().generateStatorShape(facing, axis);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING);
            Direction.Axis axis = state.getValue(AXIS);
            getStructureHelper().placeStructureStator(level, pos, facing, axis, this);
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        BlockPos clickedPos = context.getClickedPos();
        // Блок, по которому кликнули (например, вал)
        BlockPos targetPos = clickedPos.relative(clickedFace.getOpposite());
        BlockState target = context.getLevel().getBlockState(targetPos);

        Direction facing;
        Direction.Axis axis;

        if (target.getBlock() instanceof ShaftBlock) {
            // Игрок кликнул по валу: ось отверстия статора совпадает с осью вала
            axis = target.getValue(ShaftBlock.FACING).getAxis();
            // Отверстие статора должно указывать ровно на вал (targetPos relative clickedPos)
            // holeOffset = facing.getOpposite(), поэтому facing = clickedFace
            facing = clickedFace;
            // facing и axis никогда не должны быть параллельны (иначе cross-product даёт 0)
            if (facing.getAxis() == axis) {
                facing = (axis == Direction.Axis.Y) ? Direction.NORTH : Direction.DOWN;
            }
        } else {
            // Обычная установка на твёрдый блок
            facing = clickedFace.getOpposite();
            if (facing.getAxis() == Direction.Axis.Y) {
                axis = context.getHorizontalDirection().getAxis();
            } else {
                axis = Direction.Axis.Y;
            }
            if (facing.getAxis() == axis) {
                axis = (facing.getAxis() == Direction.Axis.Y) ? Direction.Axis.Z : Direction.Axis.Y;
            }
        }

        return this.defaultBlockState().setValue(FACING, facing).setValue(AXIS, axis);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && state.getBlock() != oldState.getBlock()) {
            EnergyNetworkManager.get((ServerLevel) level).addNode(pos);
            KineticNetworkManager.get((ServerLevel) level).updateNetworkAfterPlace(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            EnergyNetworkManager.get((ServerLevel) level).removeNode(pos);
            KineticNetworkManager.get((ServerLevel) level).updateNetworkAfterRemove(pos);

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof StatorBlockEntity stator) {
                IItemHandler handler = stator.getCoilsInventory();
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                    }
                }
            }

            Direction facing = state.getValue(FACING);
            Direction.Axis axis = state.getValue(AXIS);
            getStructureHelper().destroyStructureStator(level, pos, facing, axis);

            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    public static Vec3 getHoleCenter(BlockPos pos, Direction facing, Direction.Axis axis) {
        BlockPos holeOffset = MultiblockStructureHelper.rotateStatorPos(new BlockPos(0, 1, 0), facing, axis);
        return new Vec3(pos.getX() + 0.5 + holeOffset.getX(), pos.getY() + 0.5 + holeOffset.getY(), pos.getZ() + 0.5 + holeOffset.getZ());
    }

    public int calculateSlot(BlockState state, BlockPos pos, Player player, BlockHitResult hit) {
        return calculateSlot(state, pos, player, hit, 1.0f);
    }

    public int calculateSlot(BlockState state, BlockPos pos, Player player, BlockHitResult hit, float partialTick) {
        Direction facing = state.getValue(FACING);
        Direction.Axis axis = state.getValue(AXIS);
        Vec3 holeCenter = getHoleCenter(pos, facing, axis);
        Vec3 normal;
        if (axis == Direction.Axis.X) normal = new Vec3(1, 0, 0);
        else if (axis == Direction.Axis.Y) normal = new Vec3(0, 1, 0);
        else normal = new Vec3(0, 0, 1);

        Vec3 eyePos = player.getEyePosition(partialTick);
        Vec3 lookVec = player.getViewVector(partialTick);

        Vec3 hitVec = hit.getLocation().subtract(holeCenter);
        double denom = normal.dot(lookVec);
        if (Math.abs(denom) > 0.0001) {
            double t = normal.dot(holeCenter.subtract(eyePos)) / denom;
            if (t > 0 && t < 10) {
                hitVec = eyePos.add(lookVec.scale(t)).subtract(holeCenter);
            }
        }

        org.joml.Vector3f localVec = new org.joml.Vector3f((float) hitVec.x, (float) hitVec.y, (float) hitVec.z);

        if (axis == Direction.Axis.X) {
            localVec.rotateY((float) Math.toRadians(-90));
        } else if (axis == Direction.Axis.Y) {
            if (facing == Direction.NORTH) {
                localVec.rotateY((float) Math.toRadians(-180));
            } else if (facing == Direction.EAST) {
                localVec.rotateY((float) Math.toRadians(-90));
            } else if (facing == Direction.WEST) {
                localVec.rotateY((float) Math.toRadians(-270));
            }
            localVec.rotateX((float) Math.toRadians(90));
        }

        double u = localVec.y;
        double v = -localVec.x;

        double angle = Math.toDegrees(Math.atan2(v, u));
        if (angle < 0) angle += 360;
        int slot = (int) Math.round(angle / 30.0) % 12;
        return (slot % 12 + 12) % 12;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof StatorBlockEntity stator)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        boolean isCoil = stack.getItem() instanceof StatorCoilItem;
        boolean isScrewdriver = stack.is(ModItems.SCREWDRIVER.get());

        if (isCoil || isScrewdriver) {
            int slot = calculateSlot(state, pos, player, hit);
            IItemHandler handler = stator.getCoilsInventory();

            if (isCoil) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) {
                    if (!level.isClientSide) {
                        ItemStack toInsert = stack.copy();
                        toInsert.setCount(1);
                        handler.insertItem(slot, toInsert, false);
                        if (!player.isCreative()) {
                            stack.shrink(1);
                        }
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }
            } else if (isScrewdriver) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (!inSlot.isEmpty()) {
                    if (!level.isClientSide) {
                        ItemStack extracted = handler.extractItem(slot, 1, false);
                        if (!player.getInventory().add(extracted)) {
                            player.drop(extracted, false);
                        }
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof StatorBlockEntity stator)) return InteractionResult.PASS;

        if (player.isCrouching()) {
            int slot = calculateSlot(state, pos, player, hit);
            IItemHandler handler = stator.getCoilsInventory();
            ItemStack inSlot = handler.getStackInSlot(slot);
            if (!inSlot.isEmpty()) {
                if (!level.isClientSide) {
                    ItemStack extracted = handler.extractItem(slot, 1, false);
                    if (!player.getInventory().add(extracted)) {
                        player.drop(extracted, false);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StatorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.STATOR_BE.get(), StatorBlockEntity.createTicker());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal("Максимальная скорость: ∞").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.trd.machine.stator.desc").withStyle(ChatFormatting.DARK_GRAY));
    }
}
