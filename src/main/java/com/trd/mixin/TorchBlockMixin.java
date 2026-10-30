package com.trd.mixin;

import com.trd.block.basic.ModBlocks;
import com.trd.climate.room.SealedRoom;
import com.trd.climate.room.SealedRoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin for {@link TorchBlock} adding random tick execution and extinguishing mechanics under rainfall or over time.
 */
@Mixin(TorchBlock.class)
public abstract class TorchBlockMixin extends Block {

    public TorchBlockMixin(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Wall torches are handled separately in WallTorchBlockMixin
        if ((Object) this instanceof WallTorchBlock) {
            return;
        }

        super.randomTick(state, level, pos, random);

        boolean shouldExtinguish;
        if (level.isRainingAt(pos)) {
            // High extinguishing chance under direct open rainfall (80% per random tick)
            shouldExtinguish = random.nextFloat() < 0.80f;
        } else {
            // Moderate extinguishing chance in sheltered areas (1 out of 120 random ticks)
            shouldExtinguish = random.nextInt(120) == 0;
        }

        if (shouldExtinguish) {
            level.setBlock(pos, ModBlocks.UNLIT_TORCH.get().defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 2.0f);
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 6, 0.05, 0.05, 0.05, 0.02);

            SealedRoom room = SealedRoomManager.findRoomAtOrNear(level, pos);
            if (room != null) {
                room.markDirty();
            }
        }
    }
}
