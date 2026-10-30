package com.trd.block.basic.industrial.lighting;

import com.mojang.serialization.MapCodec;
import com.trd.climate.room.SealedRoom;
import com.trd.climate.room.SealedRoomManager;
import com.trd.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Extinguished wall-mounted torch fixture.
 * Emits zero light and no flame/smoke particles.
 * Can be relit using flint and steel or an active burning torch, preserving wall mounting orientation.
 */
public class UnlitWallTorchBlock extends WallTorchBlock {
    public static final MapCodec<UnlitWallTorchBlock> CODEC = simpleCodec(UnlitWallTorchBlock::new);

    public UnlitWallTorchBlock(Properties properties) {
        super(ParticleTypes.FLAME, properties.lightLevel(state -> 0));
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<WallTorchBlock> codec() {
        return (MapCodec<WallTorchBlock>) (MapCodec<?>) CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return WallTorchBlock.getShape(state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Unlit wall torches do not emit flame or smoke particles
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.TORCH)) {
            if (!level.isClientSide) {
                Direction facing = state.getValue(FACING);
                BlockState litWallTorch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing);
                level.setBlock(pos, litWallTorch, 3);
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0f, level.random.nextFloat() * 0.4f + 0.8f);

                SealedRoom room = SealedRoomManager.findRoomAtOrNear(level, pos);
                if (room != null) {
                    room.markDirty();
                }

                if (stack.is(Items.FLINT_AND_STEEL) && !player.isCreative()) {
                    stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.UNLIT_TORCH.get());
    }
}
