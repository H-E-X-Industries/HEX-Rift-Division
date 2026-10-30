package com.trd.mixin;

import com.trd.block.basic.ModBlocks;
import com.trd.climate.room.SealedRoom;
import com.trd.climate.room.SealedRoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin for {@link WallTorchBlock} adding random tick execution and extinguishing mechanics,
 * maintaining the original wall-mounting orientation when extinguished.
 */
@Mixin(WallTorchBlock.class)
public abstract class WallTorchBlockMixin extends TorchBlock {

    public WallTorchBlockMixin(SimpleParticleType flameParticle, Properties properties) {
        super(flameParticle, properties);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean shouldExtinguish;
        if (level.isRainingAt(pos)) {
            // High extinguishing chance under direct open rainfall (80% per random tick)
            shouldExtinguish = random.nextFloat() < 0.80f;
        } else {
            // Moderate extinguishing chance in sheltered areas (1 out of 120 random ticks)
            shouldExtinguish = random.nextInt(120) == 0;
        }

        if (shouldExtinguish) {
            Direction facing = state.getValue(WallTorchBlock.FACING);
            BlockState unlitWallTorch = ModBlocks.UNLIT_WALL_TORCH.get().defaultBlockState().setValue(WallTorchBlock.FACING, facing);
            level.setBlock(pos, unlitWallTorch, 3);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 2.0f);

            Direction opp = facing.getOpposite();
            double x = pos.getX() + 0.5 + 0.27 * opp.getStepX();
            double y = pos.getY() + 0.7 + 0.22;
            double z = pos.getZ() + 0.5 + 0.27 * opp.getStepZ();
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 6, 0.05, 0.05, 0.05, 0.02);

            SealedRoom room = SealedRoomManager.findRoomAtOrNear(level, pos);
            if (room != null) {
                room.markDirty();
            }
        }
    }
}
