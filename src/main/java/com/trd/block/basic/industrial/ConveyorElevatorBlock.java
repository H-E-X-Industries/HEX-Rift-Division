package com.trd.block.basic.industrial;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ConveyorElevatorBlock extends ConveyorBlock {
    public static final EnumProperty<ElevatorPart> PART = EnumProperty.create("part", ElevatorPart.class);

    public ConveyorElevatorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, ElevatorPart.BOTTOM));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();

        BlockState belowState = level.getBlockState(pos.below());
        if (belowState.getBlock() == this) {
            facing = belowState.getValue(FACING); // Inherit facing from below
        }

        return this.defaultBlockState().setValue(FACING, facing).setValue(PART, getPartForPos(level, pos));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP || direction == Direction.DOWN) {
            ElevatorPart newPart = getPartForPos(level, pos);
            if (state.getValue(PART) != newPart) {
                if (!level.isClientSide()) {
                    level.scheduleTick(pos, this, 1);
                }
                return state.setValue(PART, newPart);
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    private ElevatorPart getPartForPos(BlockGetter level, BlockPos pos) {
        boolean hasBelow = level.getBlockState(pos.below()).getBlock() == this;
        boolean hasAbove = level.getBlockState(pos.above()).getBlock() == this;

        if (hasAbove) {
            return hasBelow ? ElevatorPart.MIDDLE : ElevatorPart.BOTTOM;
        } else {
            return hasBelow ? ElevatorPart.TOP : ElevatorPart.BOTTOM; // If placed alone, bottom
        }
    }

    @Override
    public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        com.trd.api.conveyor.ConveyorNetworkManager manager = com.trd.api.conveyor.ConveyorNetworkManager.get(level);
        manager.removeBlock(pos);
        manager.addBlock(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Elevators probably occupy the full block in height for middle parts, 
        // bottom has full height? User said "На блоке верхушки он проезжает 8 пикселей вверх (половина блока, это высота обычного конвеера)".
        // Meaning middle is full block, bottom is full block, top is half block.
        ElevatorPart part = state.getValue(PART);
        if (part == ElevatorPart.TOP) {
            return Block.box(0, 0, 0, 16, 8, 16);
        }
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    public enum ElevatorPart implements StringRepresentable {
        BOTTOM("bottom"),
        MIDDLE("middle"),
        TOP("top");

        private final String name;

        ElevatorPart(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
