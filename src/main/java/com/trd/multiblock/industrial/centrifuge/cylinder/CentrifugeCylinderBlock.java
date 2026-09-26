package com.trd.multiblock.industrial.centrifuge.cylinder;

import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.multiblock.industrial.centrifuge.CentrifugeMotorBlock;
import com.trd.multiblock.system.IMultiblockController;
import com.trd.multiblock.system.MultiblockStructureHelper;
import com.trd.multiblock.system.PartRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

public class CentrifugeCylinderBlock extends BaseEntityBlock implements IMultiblockController {

    public static final com.mojang.serialization.MapCodec<CentrifugeCylinderBlock> CODEC = simpleCodec(CentrifugeCylinderBlock::new);

    private static MultiblockStructureHelper helper;

    public CentrifugeCylinderBlock(Properties properties) {
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

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape own = getStructureHelper().generateShapeFromParts(Direction.NORTH);
        if (level.getBlockState(pos.below()).getBlock() instanceof CentrifugeMotorBlock) {
            return Shapes.or(own, Shapes.block().move(0, -1, 0));
        }
        return own;
    }

    @Override
    public MultiblockStructureHelper getStructureHelper() {
        if (helper == null) {
            Map<Character, Supplier<BlockState>> symbols = Map.of(
                    '#', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    '@', () -> this.defaultBlockState()
            );
            Map<Character, PartRole> roles = Map.of(
                    '#', PartRole.DEFAULT,
                    '@', PartRole.CONTROLLER
            );

            helper = MultiblockStructureHelper.createFromLayersWithRoles(
                    new String[][]{
                            {"@"},
                            {"#"}
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
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!(level.getBlockState(pos.below()).getBlock() instanceof CentrifugeMotorBlock)) return false;
        return level.getBlockState(pos.above()).canBeReplaced();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            if (!(level.getBlockState(pos.below()).getBlock() instanceof CentrifugeMotorBlock)
                    || !level.getBlockState(pos.above()).canBeReplaced()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                Block.popResource(level, pos, new ItemStack(this));
                return;
            }
            getStructureHelper().placeStructure(level, pos, Direction.NORTH, this);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
        if (!level.isClientSide && !(level.getBlockState(pos.below()).getBlock() instanceof CentrifugeMotorBlock)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CentrifugeCylinderBlockEntity cylinder) {
                cylinder.dropContents();
            }
            getStructureHelper().destroyStructure(level, pos, Direction.NORTH);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof CentrifugeCylinderBlockEntity cylinder) {
            player.openMenu(cylinder, pos);
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CentrifugeCylinderBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.CENTRIFUGE_CYLINDER_BE.get(),
                CentrifugeCylinderBlockEntity::serverTick);
    }
}
