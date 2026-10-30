package com.trd.climate.block;

import com.mojang.serialization.MapCodec;
import com.trd.block.entity.ModBlockEntities;
import com.trd.climate.block.entity.AtmosphericScannerBlockEntity;
import com.trd.item.tools.ScrewdriverItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Атмосферный сканер герметичности помещений произвольной формы (Tier 2).
 * Сканирует геометрию комнаты сферическим волновым алгоритмом (BFS, R <= 32, V <= 3000 блоков),
 * огибает колонны и мебель, находит любые сложные закругленные формы, изолирует климат и поддерживает
 * стабильный температурный баланс.
 */
public class AtmosphericScannerBlock extends BaseEntityBlock {

    public static final MapCodec<AtmosphericScannerBlock> CODEC = simpleCodec(AtmosphericScannerBlock::new);
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");

    public AtmosphericScannerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(SEALED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(SEALED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AtmosphericScannerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(blockEntityType, ModBlockEntities.ATMOSPHERIC_SCANNER_BE.get(),
                AtmosphericScannerBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            net.minecraft.world.InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (stack.getItem() instanceof ScrewdriverItem) {
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                BlockEntity be = serverLevel.getBlockEntity(pos);
                if (be instanceof AtmosphericScannerBlockEntity scanner) {
                    scanner.triggerScan(serverLevel, player);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            BlockEntity be = serverLevel.getBlockEntity(pos);
            if (be instanceof AtmosphericScannerBlockEntity scanner) {
                if (player.isShiftKeyDown()) {
                    if (scanner.isSealed()) {
                        scanner.unsealManual(serverLevel);
                        player.displayClientMessage(Component.translatable("message.trd.insulation.scanner_unsealed_manual"), false);
                        serverLevel.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, 0.6f);
                    } else {
                        scanner.triggerScan(serverLevel, player);
                    }
                } else {
                    if (!scanner.isSealed()) {
                        scanner.triggerScan(serverLevel, player);
                    } else {
                        Component msg = scanner.getStatusComponent(serverLevel);
                        if (msg != null) {
                            player.displayClientMessage(msg, false);
                        }
                    }
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AtmosphericScannerBlockEntity scanner) {
                scanner.onRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
