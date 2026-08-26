package com.trd.multiblock.industrial.smelter;

import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.item.ModItems;
import com.trd.multiblock.system.IMultiblockController;
import com.trd.multiblock.system.MultiblockStructureHelper;
import com.trd.multiblock.system.PartRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class SmelterBlock extends BaseEntityBlock implements IMultiblockController {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final float EFFECTS_MIN_TEMP = 300.0F;
    private static MultiblockStructureHelper helper;
    private final Map<Direction, VoxelShape> shapeCache = new EnumMap<>(Direction.class);

    public SmelterBlock(Properties properties) {
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

    @Override
    public MultiblockStructureHelper getStructureHelper() {
        if (helper == null) {
            Map<Character, Supplier<BlockState>> symbols = Map.of(
                    '#', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    '$', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    'O', () -> this.defaultBlockState()
            );
            Map<Character, PartRole> roles = Map.of(
                    '#', PartRole.DEFAULT,
                    // '$' — обычные мультиблок-парты нижнего слоя; роль FLUID_OUTPUT
                    // служит маркером валидных точек крепления литейного спуска
                    // (см. CastingDescentBlock.canSurvive)
                    '$', PartRole.FLUID_OUTPUT,
                    'O', PartRole.CONTROLLER
            );

            helper = MultiblockStructureHelper.createFromLayersWithRoles(
                    new String[][]{
                            {"#$#", "$O$", "#$#"},
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
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeCache.computeIfAbsent(state.getValue(FACING), this::buildShape);
    }

    private VoxelShape buildShape(Direction facing) {
        VoxelShape full = getStructureHelper().generateShapeFromParts(facing);
        VoxelShape hole = MultiblockStructureHelper.rotateShape(Block.box(-2, 16, -2, 18, 32, 18), facing);
        return Shapes.join(full, hole, BooleanOp.ONLY_FIRST);
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
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SmelterBlockEntity smelter) {
                // Выбрасываем инвентарь
                ItemStackHandler inv = smelter.getInventory();
                for (int i = 0; i < inv.getSlots(); i++) {
                    if (!inv.getStackInSlot(i).isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inv.getStackInSlot(i));
                    }
                }

                // Выбрасываем металл как шлак
                if (smelter.hasMetal()) {
                    List<ItemStack> slagItems = smelter.dumpMetalAsSlag();
                    for (ItemStack slag : slagItems) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), slag);
                    }
                }
            }
            Direction facing = state.getValue(FACING);
            getStructureHelper().destroyStructure(level, pos, facing);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SmelterBlockEntity smelter)) {
            return InteractionResult.PASS;
        }

        ItemStack heldItem = player.getItemInHand(hand);

        // === КОЧЕРГА - обрабатывается в PokerItem.useOn() ===
        // Важно: возвращаем PASS чтобы сработал useOn у кочерги
        if (heldItem.is(ModItems.POKER.get())) {
            return InteractionResult.PASS;
        }

        // === Shift + ПКМ без кочерги - сообщение о необходимости кочерги ===
        // Но только если это НЕ кочерга (выше уже проверили)
        if (player.isShiftKeyDown()) {
            player.displayClientMessage(Component.literal("§cДля сброса металла нужна кочерга!"), true);
            return InteractionResult.CONSUME; // CONSUME чтобы не открылся GUI
        }

        // Обычное открытие GUI
        net.minecraftforge.network.NetworkHooks.openScreen(
                (net.minecraft.server.level.ServerPlayer) player,
                smelter,
                pos
        );
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmelterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            // Клиентский тикер для дыма — тот же механизм, что у ванильного костра,
            // который спавнит дым каждый игровой тик (а не через редкий animateTick)
            return createTickerHelper(type, ModBlockEntities.SMELTER_BE.get(), SmelterBlock::clientSmokeTick);
        }
        return createTickerHelper(type, ModBlockEntities.SMELTER_BE.get(), SmelterBlockEntity::serverTick);
    }

    /**
     * Клиентский тикер дыма — копия логики CampfireBlockEntity#particleTick:
     * каждый игровой тик с вероятностью 11% спавнится сигнальный дым.
     * Дым идёт ТОЛЬКО во время активной плавки и ещё ~5 секунд после неё.
     */
    private static void clientSmokeTick(Level level, BlockPos pos, BlockState state, SmelterBlockEntity smelter) {
        if (smelter.getSmokeTicks() <= 0) return;

        if (level.getRandom().nextFloat() < 0.11F) {
            MultiblockStructureHelper structureHelper = helper;
            if (structureHelper != null) {
                BlockPos chimney = structureHelper.getTopCenterAbovePos(pos, state.getValue(FACING));
                makeCampfireSmoke(level, chimney);
            }
        }
    }

    /**
     * Копия ванильного CampfireBlock#makeParticles (сигнальный дым — как у костра
     * со снопом сена снизу): тот же разброс, та же скорость, addAlwaysVisibleParticle,
     * но источник опущен на пол-блока ниже.
     */
    private static void makeCampfireSmoke(Level level, BlockPos pos) {
        RandomSource randomsource = level.getRandom();
        double x = (double) pos.getX() + 0.5D + randomsource.nextDouble() / 3.0D * (double) (randomsource.nextBoolean() ? 1 : -1);
        double y = (double) pos.getY() - 0.5D + randomsource.nextDouble() + randomsource.nextDouble();
        double z = (double) pos.getZ() + 0.5D + randomsource.nextDouble() / 3.0D * (double) (randomsource.nextBoolean() ? 1 : -1);
        level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, x, y, z, 0.0D, 0.07D, 0.0D);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof SmelterBlockEntity smelter) || smelter.getTemperature() < EFFECTS_MIN_TEMP) {
            return;
        }

        if (random.nextDouble() < 0.1D) {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }

        double x = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.6D;
        double y = pos.getY() + 1.5D;
        double z = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.6D;

        if (random.nextDouble() < 0.4D) {
            level.addParticle(ParticleTypes.LAVA, x, y, z, 0.0D, 0.0D, 0.0D);
        }
        if (random.nextDouble() < 0.6D) {
            level.addParticle(ParticleTypes.FLAME, x, y, z,
                    (random.nextDouble() - 0.5D) * 0.05D,
                    0.01D + random.nextDouble() * 0.05D,
                    (random.nextDouble() - 0.5D) * 0.05D);
        }
    }
}