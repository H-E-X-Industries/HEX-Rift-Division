package com.trd.block.basic.industrial.casting;

import com.trd.api.metallurgy.system.recipe.MoldRecipeRegistry;
import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.casting.CastingPotBlockEntity;
import com.trd.event.HotItemHandler;
import com.trd.event.SlagItem;
import com.trd.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CastingPotBlock extends BaseEntityBlock {
    public static final com.mojang.serialization.MapCodec<CastingPotBlock> CODEC = simpleCodec(CastingPotBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty HAS_METAL = BooleanProperty.create("has_metal");

    public CastingPotBlock(BlockBehaviour.Properties properties) {
        super(properties.lightLevel(state -> state.getValue(HAS_METAL) ? 14 : 0));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HAS_METAL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_METAL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.box(0, 0, 0, 1, 0.5, 1);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CastingPotBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.CASTING_POT.get(), CastingPotBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof CastingPotBlockEntity pot)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (heldItem.is(ModItems.POKER.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (HotItemHandler.isHot(heldItem)) {
            if (pot.tryInsertHotItem(heldItem)) {
                heldItem.shrink(1);
                level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 2.0f);
                return ItemInteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.translatable("gui.trd.casting_pot.cannot_insert"), true);
            return ItemInteractionResult.FAIL;
        }

        if (heldItem.is(ModItems.SCREWDRIVER.get())) {
            ItemStack moldStack = pot.getMold();
            if (!moldStack.isEmpty() && pot.canRemoveMold()) {
                if (!player.getInventory().add(moldStack.copy())) {
                    player.drop(moldStack.copy(), false);
                }
                pot.setMold(ItemStack.EMPTY);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                return ItemInteractionResult.CONSUME;
            }
            if (!moldStack.isEmpty() && !pot.canRemoveMold()) {
                player.displayClientMessage(Component.translatable("gui.trd.casting_pot.cannot_remove_mold"), true);
                return ItemInteractionResult.FAIL;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (pot.getMold().isEmpty() && MoldRecipeRegistry.hasRecipe(heldItem.getItem())) {
            ItemStack toInsert = heldItem.copy();
            toInsert.setCount(1);
            pot.setMold(toInsert);
            heldItem.shrink(1);
            level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return ItemInteractionResult.CONSUME;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof CastingPotBlockEntity pot)) {
            return InteractionResult.PASS;
        }

        // Извлечение шлака
        if (pot.hasSlag()) {
            ItemStack slagStack = pot.getSlagStack();
            boolean isHot = HotItemHandler.isHot(slagStack);

            if (!isHot) {
                ItemStack slag = pot.extractSlag();
                if (!slag.isEmpty()) {
                    if (!player.getInventory().add(slag)) {
                        player.drop(slag, false);
                    }
                    level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
                    return InteractionResult.CONSUME;
                }
            } else {
                player.displayClientMessage(Component.translatable("gui.trd.casting_pot.slag_hot"), true);
                return InteractionResult.PASS;
            }
        }

        // Извлечение готового остывшего предмета
        if (!pot.getOutputItem().isEmpty()) {
            ItemStack output = pot.getOutputItem();
            if (HotItemHandler.isHot(output)) {
                float heatRatio = HotItemHandler.getHeatRatio(output);
                int percent = (int)(heatRatio * 100);
                int temp = HotItemHandler.getTemperature(output);
                player.displayClientMessage(Component.translatable("gui.trd.casting_pot.too_hot", temp, percent), true);
                return InteractionResult.PASS;
            }

            ItemStack drop = pot.takeOutput();
            if (!player.getInventory().add(drop)) {
                player.drop(drop, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }

        // Извлечение формы пустой рукой
        if (!pot.getMold().isEmpty()) {
            if (pot.canRemoveMold()) {
                ItemStack moldStack = pot.getMold().copy();
                pot.setMold(ItemStack.EMPTY);
                if (!player.getInventory().add(moldStack)) {
                    player.drop(moldStack, false);
                }
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.CONSUME;
            } else {
                player.displayClientMessage(Component.translatable("gui.trd.casting_pot.cannot_remove_mold"), true);
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof CastingPotBlockEntity pot) {
                if (!pot.getMold().isEmpty()) {
                    popResource(level, pos, pot.getMold());
                }

                if (!pot.getOutputItem().isEmpty()) {
                    ItemStack drop = pot.getOutputItem().copy();
                    popResource(level, pos, drop);
                }

                if (pot.getStoredUnits() > 0 && pot.getCurrentMetal() != null) {
                    ItemStack slag = SlagItem.createSlag(pot.getCurrentMetal(), pot.getStoredUnits());
                    popResource(level, pos, slag);
                }

                if (pot.hasSlag()) {
                    ItemStack slag = pot.extractSlag();
                    if (!slag.isEmpty()) {
                        popResource(level, pos, slag);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
