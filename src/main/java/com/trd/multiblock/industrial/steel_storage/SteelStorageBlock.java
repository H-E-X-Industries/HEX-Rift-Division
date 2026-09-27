package com.trd.multiblock.industrial.steel_storage;

import com.mojang.serialization.MapCodec;
import com.trd.block.basic.ModBlocks;
import com.trd.multiblock.system.IMultiblockController;
import com.trd.multiblock.system.MultiblockStructureHelper;
import com.trd.multiblock.system.PartRole;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class SteelStorageBlock extends BaseEntityBlock implements IMultiblockController {

    public static final MapCodec<SteelStorageBlock> CODEC = simpleCodec(SteelStorageBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static MultiblockStructureHelper helper;

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public SteelStorageBlock(Properties properties) {
        super(properties.noOcclusion().strength(2.5f, 6.0f));
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

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return getStructureHelper().generateShapeFromParts(facing);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public MultiblockStructureHelper getStructureHelper() {
        if (helper == null) {
            Map<Character, Supplier<BlockState>> symbols = Map.of(
                    '#', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    'O', () -> this.defaultBlockState()
            );
            Map<Character, PartRole> roles = Map.of(
                    '#', PartRole.DEFAULT,
                    'O', PartRole.CONTROLLER
            );
            // 1 высота, 1 длина, 2 ширина (по X в паттерне)
            helper = MultiblockStructureHelper.createFromLayersWithRoles(
                    new String[][]{{"O#"}},
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

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SteelStorageBlockEntity storage) {
                CustomData beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
                if (beData != null && !beData.isEmpty()) {
                    beData.loadInto(storage, level.registryAccess());
                    storage.setChanged();
                } else {
                    CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
                    if (customData != null) {
                        CompoundTag tag = customData.copyTag();
                        if (tag.contains("BlockEntityTag")) {
                            storage.loadAdditional(tag.getCompound("BlockEntityTag"), level.registryAccess());
                            storage.setChanged();
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return Collections.emptyList();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player.getAbilities().instabuild) {
            return super.playerWillDestroy(level, pos, state, player);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SteelStorageBlockEntity storage && !level.isClientSide) {
            ItemStack stack = new ItemStack(this);
            if (!storage.isEmpty()) {
                storage.saveToItem(stack, level.registryAccess());
            }
            popResource(level, pos, stack);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide && !MultiblockStructureHelper.isDestroying()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SteelStorageBlockEntity storage) {
                ItemStack stack = new ItemStack(this);
                if (!storage.isEmpty()) {
                    storage.saveToItem(stack, level.registryAccess());
                }
                popResource(level, pos, stack);
            }
        }
        super.onBlockExploded(state, level, pos, explosion);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SteelStorageBlockEntity storage && !storage.isEmpty()) {
            storage.saveToItem(stack, level.registryAccess());
        }
        return stack;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            Direction facing = state.getValue(FACING);
            getStructureHelper().destroyStructure(level, pos, facing);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SteelStorageBlockEntity be) {
            player.openMenu(be, pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        CustomData beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        CompoundTag blockEntityTag = null;
        if (beData != null && !beData.isEmpty()) {
            blockEntityTag = beData.copyTag();
        } else {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag cTag = customData.copyTag();
                if (cTag.contains("BlockEntityTag")) {
                    blockEntityTag = cTag.getCompound("BlockEntityTag");
                }
            }
        }

        if (blockEntityTag == null || !blockEntityTag.contains("Inventory")) {
            tooltip.add(Component.translatable("tooltip.trd.steel_storage.empty").withStyle(ChatFormatting.GRAY));
            return;
        }

        CompoundTag invTag = blockEntityTag.getCompound("Inventory");
        ItemStackHandler handler = new ItemStackHandler();
        if (context.registries() != null) {
            handler.deserializeNBT(context.registries(), invTag);
        }

        int filled = 0;
        int total = handler.getSlots();
        Map<String, Integer> items = new java.util.LinkedHashMap<>();

        for (int i = 0; i < total; i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty()) {
                filled++;
                String name = s.getHoverName().getString();
                items.merge(name, s.getCount(), Integer::sum);
            }
        }

        if (filled == 0) {
            tooltip.add(Component.translatable("tooltip.trd.steel_storage.empty").withStyle(ChatFormatting.GRAY));
            return;
        }

        float ratio = (float) filled / total;
        ChatFormatting color = ratio < 0.33 ? ChatFormatting.GREEN
                : (ratio < 0.66 ? ChatFormatting.YELLOW : ChatFormatting.RED);

        tooltip.add(Component.translatable("tooltip.trd.steel_storage.contains", filled, total).withStyle(color));

        int shown = 0;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            if (shown >= 5) {
                tooltip.add(Component.translatable("tooltip.trd.steel_storage.and_more", items.size() - 5).withStyle(ChatFormatting.GRAY));
                break;
            }
            tooltip.add(Component.translatable("tooltip.trd.steel_storage.item", entry.getKey(), entry.getValue()).withStyle(ChatFormatting.GRAY));
            shown++;
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteelStorageBlockEntity(pos, state);
    }
}
