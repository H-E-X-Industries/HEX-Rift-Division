package com.trd.client;

import com.trd.block.basic.ModBlocks;
import com.trd.main.MainRegistry;
import com.trd.multiblock.system.IMultiblockController;
import com.trd.multiblock.system.roles.IMultiblockPart;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Клиентские расширения для {@code trd:multiblock_part}.
 *
 * <p>У партов мультиблока нет ни модели, ни particle-текстуры, поэтому ванильные
 * частицы разрушения/удара рисуются как фиолетовые квадраты. Здесь они
 * подменяются частицами с текстурой блока-контроллера, а ванильные отменяются.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MultiblockPartClientExtensions implements IClientBlockExtensions {

    public static final MultiblockPartClientExtensions INSTANCE = new MultiblockPartClientExtensions();

    private MultiblockPartClientExtensions() {
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerBlock(INSTANCE, ModBlocks.MULTIBLOCK_PART.get());
    }

    private static BlockState controllerState(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
            BlockState ctrlState = level.getBlockState(part.getControllerPos());
            if (ctrlState.getBlock() instanceof IMultiblockController) {
                return ctrlState;
            }
        }
        return null;
    }

    /** Отменяем фиолетовые частицы при РАЗРУШЕНИИ. */
    @Override
    public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
        BlockState ctrlState = controllerState(level, pos);
        if (ctrlState != null) {
            for (int i = 0; i < 8; ++i) {
                double x = pos.getX() + level.random.nextDouble();
                double y = pos.getY() + level.random.nextDouble();
                double z = pos.getZ() + level.random.nextDouble();
                double speedX = (level.random.nextDouble() - 0.5D) * 0.15D;
                double speedY = level.random.nextDouble() * 0.15D;
                double speedZ = (level.random.nextDouble() - 0.5D) * 0.15D;
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ctrlState), x, y, z, speedX, speedY, speedZ);
            }
        }
        // true == "частицы уже нарисованы, ванильные не нужны"
        return true;
    }

    /** Отменяем фиолетовые частицы при УДАРЕ ПО БЛОКУ. */
    @Override
    public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
        if (target instanceof BlockHitResult blockTarget) {
            BlockPos pos = blockTarget.getBlockPos();
            BlockState ctrlState = controllerState(level, pos);
            if (ctrlState != null) {
                Direction side = blockTarget.getDirection();
                double x = pos.getX() + 0.5D + side.getStepX() * 0.5D + (level.random.nextDouble() - 0.5D) * 0.5D;
                double y = pos.getY() + 0.5D + side.getStepY() * 0.5D + (level.random.nextDouble() - 0.5D) * 0.5D;
                double z = pos.getZ() + 0.5D + side.getStepZ() * 0.5D + (level.random.nextDouble() - 0.5D) * 0.5D;
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ctrlState), x, y, z, 0.0D, 0.0D, 0.0D);
            }
        }
        return true;
    }
}