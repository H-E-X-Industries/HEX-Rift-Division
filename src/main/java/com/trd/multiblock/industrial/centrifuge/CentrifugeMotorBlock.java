package com.trd.multiblock.industrial.centrifuge;

import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Мотор центрифуги — нижняя часть конструкции и её порт:
 * снизу принимает электричество, со всех остальных сторон предметы.
 * Без насадки ничего не делает (HUD: «установите насадку!»).
 */
public class CentrifugeMotorBlock extends BaseEntityBlock {

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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    private boolean hasAttachment(BlockGetter level, BlockPos pos) {
        var above = level.getBlockState(pos.above()).getBlock();
        return above instanceof CentrifugeConusBlock || above instanceof CentrifugeCylinderBlock;
    }

    /**
     * После установки насадки мотор делит с ней общий хитбокс и контур.
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return hasAttachment(level, pos) ? ATTACHED_SHAPE : MOTOR_SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return hasAttachment(level, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (hasAttachment(level, pos)) {
            BlockPos conusPos = pos.above();
            BlockHitResult newHit = new BlockHitResult(
                    hit.getLocation(),
                    hit.getDirection(),
                    conusPos,
                    hit.isInside()
            );
            return level.getBlockState(conusPos).use(level, player, hand, newHit);
        }
        // Без насадки ничего не делаем: PASS позволяет установить насадку
        // удерживаемым в руке предметом (иначе клик съедается впустую)
        return InteractionResult.PASS;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
        // Насадку установили/сняли — мотор получил/потерял жидкостный порт,
        // соседние трубы должны пересчитать подключение
        if (!level.isClientSide && neighborPos.equals(pos.above())) {
            level.getBlockState(pos).updateNeighbourShapes(level, pos, 3);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CentrifugeMotorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }
}
