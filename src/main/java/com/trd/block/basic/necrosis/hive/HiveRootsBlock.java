package com.trd.block.basic.necrosis.hive;

import com.mojang.serialization.MapCodec;
import com.trd.block.basic.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class HiveRootsBlock extends Block implements BonemealableBlock {
    public static final MapCodec<HiveRootsBlock> CODEC = simpleCodec(HiveRootsBlock::new);

    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty HANGING = BooleanProperty.create("hanging");

    private static final int GROWTH_CHANCE = 4; // 25% шанс
    private static final int MAX_LENGTH = 5;
    private static final int BONE_MEAL_GROWTH = 2;

    public HiveRootsBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AGE, 0)
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(HANGING, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        boolean hanging = state.getValue(HANGING);

        if (hanging) {
            BlockPos abovePos = pos.above();
            BlockState above = level.getBlockState(abovePos);
            return above.is(ModBlocks.HIVE_SOIL.get())
                    || above.is(ModBlocks.DEPTH_WORM_NEST.get())
                    || (above.is(this) && above.getValue(HANGING));
        } else {
            BlockPos belowPos = pos.below();
            BlockState below = level.getBlockState(belowPos);
            return below.is(ModBlocks.HIVE_SOIL.get())
                    || below.is(ModBlocks.DEPTH_WORM_NEST.get())
                    || (below.is(this) && !below.getValue(HANGING));
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();

        boolean hanging;

        if (face == Direction.DOWN) {
            hanging = true;
        } else if (face == Direction.UP) {
            hanging = false;
        } else {
            BlockState above = level.getBlockState(pos.above());
            if (above.is(ModBlocks.HIVE_SOIL.get()) || above.is(ModBlocks.DEPTH_WORM_NEST.get()) ||
                    (above.is(this) && above.getValue(HANGING))) {
                hanging = true;
            } else {
                hanging = false;
            }
        }

        BlockPos supportPos = hanging ? pos.above() : pos.below();
        BlockState support = level.getBlockState(supportPos);
        boolean validSupport = support.is(ModBlocks.HIVE_SOIL.get())
                || support.is(ModBlocks.DEPTH_WORM_NEST.get())
                || support.is(this);

        if (!validSupport) {
            return null;
        }

        return this.defaultBlockState().setValue(HANGING, hanging);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(Items.BONE_MEAL)) {
            if (!level.isClientSide) {
                if (this.isValidBonemealTarget(level, pos, state)) {
                    this.performBonemeal((ServerLevel) level, level.getRandom(), pos, state);
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    level.levelEvent(1505, pos, 0);
                    return ItemInteractionResult.SUCCESS;
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(GROWTH_CHANCE) != 0) return;

        if (isMaxLength(level, pos, state.getValue(HANGING))) return;

        if (canGrowFurther(level, pos, state)) {
            grow(level, pos, state, random);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return canGrowFurther(level, pos, state)
                && !isMaxLength(level, pos, state.getValue(HANGING));
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockState currentState = state;
        BlockPos currentPos = pos;

        for (int i = 0; i < BONE_MEAL_GROWTH; i++) {
            if (!canGrowFurther(level, currentPos, currentState)) break;
            if (isMaxLength(level, currentPos, currentState.getValue(HANGING))) break;

            grow(level, currentPos, currentState, random);

            boolean hanging = currentState.getValue(HANGING);
            Direction growDir = hanging ? Direction.DOWN : Direction.UP;
            currentPos = currentPos.relative(growDir);
            currentState = level.getBlockState(currentPos);

            if (!currentState.is(this)) break;
        }
    }

    private boolean canGrowFurther(LevelReader level, BlockPos pos, BlockState state) {
        boolean hanging = state.getValue(HANGING);
        Direction growDir = hanging ? Direction.DOWN : Direction.UP;
        BlockPos growPos = pos.relative(growDir);
        return level.getBlockState(growPos).isAir();
    }

    private boolean isMaxLength(LevelReader level, BlockPos pos, boolean hanging) {
        return getLength(level, pos, hanging) >= MAX_LENGTH;
    }

    private void grow(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        boolean hanging = state.getValue(HANGING);
        Direction growDir = hanging ? Direction.DOWN : Direction.UP;
        BlockPos growPos = pos.relative(growDir);

        if (!level.getBlockState(growPos).isAir()) return;
        if (isMaxLength(level, pos, hanging)) return;

        BlockState newState = this.defaultBlockState()
                .setValue(HANGING, hanging)
                .setValue(AGE, Math.min(state.getValue(AGE) + 1, 3));

        level.setBlock(growPos, newState, 3);

        if (hanging) {
            level.setBlock(pos, state.setValue(DOWN, true), 3);
        } else {
            level.setBlock(pos, state.setValue(UP, true), 3);
        }

        level.levelEvent(1505, growPos, 0);
    }

    private int getLength(LevelReader level, BlockPos pos, boolean hanging) {
        int length = 1;
        Direction dir = hanging ? Direction.DOWN : Direction.UP;
        BlockPos checkPos = pos.relative(dir);

        while (level.getBlockState(checkPos).is(this)) {
            length++;
            checkPos = checkPos.relative(dir);
            if (length > MAX_LENGTH + 5) break;
        }

        return length;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            boolean hanging = state.getValue(HANGING);
            Direction breakDir = hanging ? Direction.DOWN : Direction.UP;

            BlockPos breakPos = pos.relative(breakDir);
            while (level.getBlockState(breakPos).is(this)) {
                BlockState breakState = level.getBlockState(breakPos);
                if (breakState.getValue(HANGING) == hanging) {
                    level.destroyBlock(breakPos, true);
                    breakPos = breakPos.relative(breakDir);
                } else {
                    break;
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        boolean hanging = state.getValue(HANGING);
        Direction supportDir = hanging ? Direction.UP : Direction.DOWN;

        if (direction == supportDir) {
            if (!canSurvive(state, level, pos)) {
                return Blocks.AIR.defaultBlockState();
            }
        }

        if (direction == Direction.UP && !hanging) {
            boolean hasUp = neighborState.is(this) && !neighborState.getValue(HANGING);
            return state.setValue(UP, hasUp);
        }
        if (direction == Direction.DOWN && !hanging) {
            boolean hasDown = neighborState.is(this) && !neighborState.getValue(HANGING);
            return state.setValue(DOWN, hasDown);
        }
        if (direction == Direction.UP && hanging) {
            boolean hasUp = neighborState.is(this) && neighborState.getValue(HANGING);
            return state.setValue(UP, hasUp);
        }
        if (direction == Direction.DOWN && hanging) {
            boolean hasDown = neighborState.is(this) && neighborState.getValue(HANGING);
            return state.setValue(DOWN, hasDown);
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, UP, DOWN, HANGING);
    }
}
