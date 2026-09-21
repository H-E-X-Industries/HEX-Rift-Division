package com.trd.multiblock.system;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import com.trd.multiblock.system.roles.IMultiblockPart;

public class MultiblockPartBlock extends BaseEntityBlock implements net.minecraft.world.level.block.SimpleWaterloggedBlock {

        public static final com.mojang.serialization.MapCodec<MultiblockPartBlock> CODEC = simpleCodec(MultiblockPartBlock::new);
    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public MultiblockPartBlock(Properties properties) {
        super(properties.strength(1.0f, 6.0f).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED);
    }

    @Override
    public net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
        return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED) ? net.minecraft.world.level.material.Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, net.minecraft.core.Direction facing, BlockState facingState, net.minecraft.world.level.LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
            level.scheduleTick(currentPos, net.minecraft.world.level.material.Fluids.WATER, net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
            BlockState ctrlState = level.getBlockState(part.getControllerPos());
            if (ctrlState.getBlock() instanceof IMultiblockController controller) {
                VoxelShape masterShape = ctrlState.getShape(level, part.getControllerPos(), context);
                BlockPos offset = part.getControllerPos().subtract(pos);
                return masterShape.move(offset.getX(), offset.getY(), offset.getZ());
            }
        }
        return Shapes.block();
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
            BlockPos ctrlPos = part.getControllerPos();
            BlockState ctrlState = level.getBlockState(ctrlPos);
            if (ctrlState.getBlock() instanceof IMultiblockController) {
                BlockHitResult newHit = new BlockHitResult(
                        hit.getLocation(),
                        hit.getDirection(),
                        ctrlPos,
                        hit.isInside()
                );
                return ctrlState.useWithoutItem(level, player, newHit);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
            BlockPos ctrlPos = part.getControllerPos();
            BlockState ctrlState = level.getBlockState(ctrlPos);
            if (ctrlState.getBlock() instanceof IMultiblockController) {
                BlockHitResult newHit = new BlockHitResult(hit.getLocation(), hit.getDirection(), ctrlPos, hit.isInside());
                return ctrlState.useItemOn(stack, level, player, hand, newHit);
            }
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
            BlockPos ctrlPos = part.getControllerPos();
            BlockState ctrlState = level.getBlockState(ctrlPos);
            if (ctrlState != null && ctrlState.getBlock() != this) {
                return ctrlState.getBlock().getExplosionResistance(ctrlState, level, ctrlPos, explosion);
            }
        }
        return super.getExplosionResistance(state, level, pos, explosion);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            if (!MultiblockStructureHelper.isDestroying()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
                    BlockPos ctrlPos = part.getControllerPos();
                    BlockState ctrlState = level.getBlockState(ctrlPos);
                    
                    if (ctrlState.getBlock() instanceof IMultiblockController controller) {
                        ItemStack ctrlDrop = new ItemStack(ctrlState.getBlock());
                        BlockEntity ctrlBe = level.getBlockEntity(ctrlPos);

                        if (ctrlBe != null) {
                            ctrlBe.saveToItem(ctrlDrop, level.registryAccess());
                        }
                        
                        Block.popResource(level, ctrlPos, ctrlDrop);

                        net.minecraft.core.Direction facing = ctrlState.hasProperty(HorizontalDirectionalBlock.FACING)
                                ? ctrlState.getValue(HorizontalDirectionalBlock.FACING) : net.minecraft.core.Direction.NORTH;
                        
                        controller.getStructureHelper().destroyStructure(level, ctrlPos, facing);
                        level.removeBlock(ctrlPos, false);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }


    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            System.out.println("TEST DESTROY: be=" + be + " at " + pos);
            if (be instanceof IMultiblockPart part) {
                System.out.println("TEST DESTROY: part.getControllerPos()=" + part.getControllerPos());
            }
            if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
                BlockPos ctrlPos = part.getControllerPos();
                BlockState ctrlState = level.getBlockState(ctrlPos);
                if (ctrlState.getBlock() instanceof IMultiblockController controller) {
                    if (!player.isCreative()) {
                        ItemStack ctrlDrop = new ItemStack(ctrlState.getBlock());
                        BlockEntity ctrlBe = level.getBlockEntity(ctrlPos);

                        if (ctrlBe != null) {
                            ctrlBe.saveToItem(ctrlDrop, level.registryAccess());
                        }

                        Block.popResource(level, ctrlPos, ctrlDrop);
                    }

                    net.minecraft.core.Direction facing = ctrlState.hasProperty(HorizontalDirectionalBlock.FACING)
                            ? ctrlState.getValue(HorizontalDirectionalBlock.FACING) : net.minecraft.core.Direction.NORTH;
                    controller.getStructureHelper().destroyStructure(level, ctrlPos, facing);
                    level.removeBlock(ctrlPos, false);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
            BlockPos ctrlPos = part.getControllerPos();
            BlockState ctrlState = level.getBlockState(ctrlPos);
            if (ctrlState.getBlock() instanceof IMultiblockController) {
                ItemStack ctrlDrop = new ItemStack(ctrlState.getBlock());
                BlockEntity ctrlBe = level.getBlockEntity(ctrlPos);

                if (ctrlBe != null) {
                    ctrlBe.saveToItem(ctrlDrop, level.registryAccess());
                }

                return ctrlDrop;
            }
        }
        return super.getCloneItemStack(level, pos, state);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }
    
    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, net.minecraft.world.entity.LivingEntity entity) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMultiblockPart part) {
            return part.getPartRole() != null && part.getPartRole().isLadder();
        }
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockPartEntity(pos, state);
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter world, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return 0;
    }
}


