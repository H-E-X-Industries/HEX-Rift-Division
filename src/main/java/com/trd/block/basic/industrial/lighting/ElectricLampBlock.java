package com.trd.block.basic.industrial.lighting;

import com.google.common.collect.ImmutableMap;
import com.trd.api.components.LampComponent;
import com.trd.api.components.ModDataComponents;
import com.trd.api.energy.EnergyNetworkManager;
import com.trd.block.basic.industrial.energy.WireBlock;
import com.trd.block.entity.industrial.lighting.SocketBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Base class for all industrial electric light fixtures.
 * Supports mounting in 6 directions on solid blocks or directly onto {@link WireBlock} cable runs (string light effect).
 * Power and cables connect strictly to the base/socket mount face.
 * Features realistic thermal inertia, warmup/cooldown curves, durability wear, and lamp unscrewing interaction.
 */
public abstract class ElectricLampBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");
    public static final IntegerProperty LIGHT_LEVEL = IntegerProperty.create("light_level", 0, 15);

    // Hitbox geometries for 6 mounting directions (6x6 base, up to 11 pixels height)
    private static final Map<Direction, VoxelShape> SHAPES = ImmutableMap.<Direction, VoxelShape>builder()
            .put(Direction.UP, Block.box(5.0, 0.0, 5.0, 11.0, 11.0, 11.0))
            .put(Direction.DOWN, Block.box(5.0, 5.0, 5.0, 11.0, 16.0, 11.0))
            .put(Direction.NORTH, Block.box(5.0, 5.0, 5.0, 11.0, 11.0, 16.0))
            .put(Direction.SOUTH, Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 11.0))
            .put(Direction.WEST, Block.box(5.0, 5.0, 5.0, 16.0, 11.0, 11.0))
            .put(Direction.EAST, Block.box(0.0, 5.0, 5.0, 11.0, 11.0, 11.0))
            .build();

    private final int maxLightLevel;
    private final Supplier<? extends Item> lampItemSupplier;
    private final Supplier<? extends Block> socketBlockSupplier;

    public ElectricLampBlock(Properties properties, int maxLightLevel,
                             Supplier<? extends Item> lampItemSupplier,
                             Supplier<? extends Block> socketBlockSupplier) {
        super(properties.lightLevel(state -> {
            if (state.hasProperty(BROKEN) && state.getValue(BROKEN)) return 0;
            return state.hasProperty(LIGHT_LEVEL) ? state.getValue(LIGHT_LEVEL) : (state.getValue(LIT) ? maxLightLevel : 0);
        }).randomTicks());
        this.maxLightLevel = maxLightLevel;
        this.lampItemSupplier = lampItemSupplier;
        this.socketBlockSupplier = socketBlockSupplier;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false)
                .setValue(BROKEN, false)
                .setValue(LIGHT_LEVEL, 0));
    }

    /**
     * Power consumption in energy units per tick.
     */
    public abstract long getConsumptionPerTick();

    /**
     * Physical durability degradation per random block tick while lit.
     */
    public abstract float getWearPerRandomTick();

    public int getMaxLightLevel() {
        return maxLightLevel;
    }

    public Item getLampItem() {
        return lampItemSupplier.get();
    }

    public Block getSocketBlock() {
        return socketBlockSupplier.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SocketBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, BROKEN, LIGHT_LEVEL);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.UP));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        BlockState state = this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(LIT, false)
                .setValue(BROKEN, false)
                .setValue(LIGHT_LEVEL, 0);
        if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
            return state;
        }
        return null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof SocketBlockEntity socketBE) {
                socketBE.serverTick((ServerLevel) lvl, pos, st);
            }
        };
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        BlockState support = level.getBlockState(supportPos);
        // Allow attachment to wires (string-light effect) or solid block faces
        if (support.getBlock() instanceof WireBlock) {
            return true;
        }
        return support.isFaceSturdy(level, supportPos, facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!state.is(oldState.getBlock()) && !level.isClientSide()) {
            Direction connectionSide = state.getValue(FACING).getOpposite();
            BlockPos wirePos = pos.relative(connectionSide);
            level.neighborChanged(wirePos, state.getBlock(), pos);
            level.updateNeighborsAt(pos, state.getBlock());
            if (level instanceof ServerLevel serverLevel) {
                EnergyNetworkManager.get(serverLevel).addNode(pos);
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide()) {
            if (!state.canSurvive(level, pos)) {
                dropResources(state, level, pos, level.getBlockEntity(pos));
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                EnergyNetworkManager.get(serverLevel).removeNode(pos);
            }
            super.onRemove(state, level, pos, newState, isMoving);
            level.updateNeighborsAt(pos, state.getBlock());
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);

        if (!state.getValue(LIT) || state.getValue(BROKEN)) {
            return;
        }

        float wear = getWearPerRandomTick();
        if (wear <= 0.0f) {
            return;
        }

        if (level.getBlockEntity(pos) instanceof SocketBlockEntity socketBE) {
            float newDurability = socketBE.getDurability() - wear;
            if (newDurability <= 0.0f) {
                socketBE.setDurability(0.0f);
                socketBE.setBroken(true);

                level.setBlock(pos, state.setValue(BROKEN, true).setValue(LIT, false), Block.UPDATE_CLIENTS);
                level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8f, 1.1f);
                level.sendParticles(ParticleTypes.SMOKE,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        12, 0.15, 0.15, 0.15, 0.02);
            } else {
                socketBE.setDurability(newDurability);
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        if (level.getBlockEntity(pos) instanceof SocketBlockEntity socketBE) {
            socketBE.onScheduledTick(level, pos, state);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (state.getValue(BROKEN) || player.isShiftKeyDown()) {
            unscrewLamp(state, level, pos, player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                              Player player, BlockHitResult hitResult) {
        if (state.getValue(BROKEN) || player.isShiftKeyDown()) {
            unscrewLamp(state, level, pos, player);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    /**
     * Unscrews the light bulb from the socket, reverting the world block to {@link EmptySocketBlock}
     * and yielding the corresponding {@link LampItem} with its current durability and broken state preserved.
     */
    public void unscrewLamp(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide()) {
            Direction facing = state.getValue(FACING);

            float durability = 1.0f;
            boolean isBroken = state.getValue(BROKEN);
            long savedEnergy = 0L;

            if (level.getBlockEntity(pos) instanceof SocketBlockEntity socketBE) {
                durability = socketBE.getDurability();
                isBroken = socketBE.isBroken() || isBroken;
                savedEnergy = socketBE.getEnergyStored();
            }

            BlockState socketState = getSocketBlock().defaultBlockState().setValue(EmptySocketBlock.FACING, facing);
            level.setBlock(pos, socketState, Block.UPDATE_ALL);

            if (level.getBlockEntity(pos) instanceof SocketBlockEntity newSocketBE) {
                newSocketBE.setEnergyStored(savedEnergy);
            }

            if (isBroken) {
                level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.7f, 1.2f);
            } else {
                level.playSound(null, pos, SoundEvents.LANTERN_HIT, SoundSource.BLOCKS, 0.7f, 1.3f);
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, 0.9f);
            }

            ItemStack lampStack = new ItemStack(getLampItem());
            LampComponent component = new LampComponent(durability, isBroken);
            lampStack.set(ModDataComponents.LAMP_DATA.get(), component);

            if (!player.addItem(lampStack)) {
                player.drop(lampStack, false);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        ItemStack tool = builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL);
        if (tool == null || !tool.isCorrectToolForDrops(state)) {
            return java.util.Collections.emptyList();
        }

        java.util.List<ItemStack> drops = new java.util.ArrayList<>();
        drops.add(new ItemStack(getSocketBlock()));

        BlockEntity be = builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        float durability = 1.0f;
        boolean isBroken = state.getValue(BROKEN);
        if (be instanceof SocketBlockEntity socketBE) {
            durability = socketBE.getDurability();
            isBroken = socketBE.isBroken() || isBroken;
        }

        ItemStack lampStack = new ItemStack(getLampItem());
        lampStack.set(ModDataComponents.LAMP_DATA.get(), new LampComponent(durability, isBroken));
        drops.add(lampStack);
        return drops;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(getLampItem());
        float durability = 1.0f;
        boolean isBroken = state.getValue(BROKEN);
        if (level.getBlockEntity(pos) instanceof SocketBlockEntity socketBE) {
            durability = socketBE.getDurability();
            isBroken = socketBE.isBroken() || isBroken;
        }
        stack.set(ModDataComponents.LAMP_DATA.get(), new LampComponent(durability, isBroken));
        return stack;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
