package com.trd.block.basic.industrial.casting;

import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.industrial.casting.CastingDescentBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CastingDescentBlock extends BaseEntityBlock {
    public static final com.mojang.serialization.MapCodec<CastingDescentBlock> CODEC = simpleCodec(CastingDescentBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty POURING = BooleanProperty.create("pouring");

    private static final double MIN_X = 5, MAX_X = 11;
    private static final double MIN_Y = 0, MAX_Y = 3.7;
    private static final double MIN_Z = 8, MAX_Z = 16;

    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            SHAPES[dir.get2DDataValue()] = calculateShape(dir);
        }
    }

    public CastingDescentBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(POURING) ? 13 : 0));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POURING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, POURING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    private static VoxelShape calculateShape(Direction facing) {
        double minX = MIN_X, maxX = MAX_X;
        double minZ = MIN_Z, maxZ = MAX_Z;

        switch (facing) {
            case NORTH -> {
                minX = MIN_X; maxX = MAX_X;
                minZ = MIN_Z; maxZ = MAX_Z;
            }
            case SOUTH -> {
                minZ = 16 - MAX_Z;
                maxZ = 16 - MIN_Z;
            }
            case EAST -> {
                minX = 16 - MAX_Z;
                maxX = 16 - MIN_Z;
                minZ = MIN_X;
                maxZ = MAX_X;
            }
            case WEST -> {
                minX = MIN_Z;
                maxX = MAX_Z;
                minZ = MIN_X;
                maxZ = MAX_X;
            }
        }

        return Block.box(minX, MIN_Y, minZ, maxX, MAX_Y, maxZ);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction back = state.getValue(FACING).getOpposite();
        BlockPos behindPos = pos.relative(back);

        BlockEntity behind = level.getBlockEntity(behindPos);
        if (behind instanceof com.trd.multiblock.system.MultiblockPartEntity part) {
            BlockPos controllerPos = part.getControllerPos();
            return controllerPos != null
                    && level.getBlockState(controllerPos).getBlock() instanceof com.trd.multiblock.industrial.smelter.SmelterBlock
                    && part.getPartRole() == com.trd.multiblock.system.PartRole.FLUID_OUTPUT;
        }

        return true;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (!level.isClientSide() && !state.canSurvive(level, currentPos)) {
            level.destroyBlock(currentPos, true);
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
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
        return new CastingDescentBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.CASTING_DESCENT.get(), CastingDescentBlockEntity::serverTick);
    }
}
