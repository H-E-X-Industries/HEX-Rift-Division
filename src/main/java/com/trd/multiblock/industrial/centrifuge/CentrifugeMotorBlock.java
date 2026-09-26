package com.trd.multiblock.industrial.centrifuge;

import com.trd.block.entity.ModBlockEntities;
import com.trd.multiblock.industrial.centrifuge.conus.CentrifugeConusBlock;
import com.trd.multiblock.industrial.centrifuge.cylinder.CentrifugeCylinderBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CentrifugeMotorBlock extends BaseEntityBlock {

    public static final com.mojang.serialization.MapCodec<CentrifugeMotorBlock> CODEC = simpleCodec(CentrifugeMotorBlock::new);

    private static final VoxelShape MOTOR_SHAPE = Shapes.block();
    private static final VoxelShape ATTACHED_SHAPE = Shapes.joinUnoptimized(
            Shapes.block(),
            Block.box(0, 16, 0, 16, 48, 16),
            BooleanOp.OR
    ).optimize();

    public CentrifugeMotorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    private boolean hasAttachment(BlockGetter level, BlockPos pos) {
        var above = level.getBlockState(pos.above()).getBlock();
        return above instanceof CentrifugeConusBlock || above instanceof CentrifugeCylinderBlock;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return hasAttachment(level, pos) ? ATTACHED_SHAPE : MOTOR_SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return hasAttachment(level, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (hasAttachment(level, pos)) {
            BlockPos abovePos = pos.above();
            BlockHitResult newHit = new BlockHitResult(
                    hit.getLocation(),
                    hit.getDirection(),
                    abovePos,
                    hit.isInside()
            );
            return level.getBlockState(abovePos).useWithoutItem(level, player, newHit);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
        if (!level.isClientSide && neighborPos.equals(pos.above())) {
            level.updateNeighborsAt(pos, this);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CentrifugeMotorBlockEntity(pos, state);
    }
}
