package com.trd.item.weapons.activators;

import com.trd.block.basic.weapons.explosives.IDetonatable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Детонатор дальнего действия: взрывает блок, на который игрок смотрит. */
public class RangeDetonatorItem extends Item {

    private static final int MAX_RANGE = 256;

    public RangeDetonatorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            // Используем встроенную трассировку взгляда игрока
            BlockHitResult hitResult = (BlockHitResult) player.pick(MAX_RANGE, 1.0F, false);

            if (hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos targetPos = hitResult.getBlockPos();

                if (!level.isLoaded(targetPos)) {
                    deny(level, player, stack);
                    return InteractionResultHolder.fail(stack);
                }

                // Спавним частицы-маркер на целевой позиции
                spawnRedstoneParticles(level, targetPos);

                BlockState state = level.getBlockState(targetPos);

                if (state.getBlock() instanceof IDetonatable detonatable
                        && detonatable.onDetonate(level, targetPos, state, player)) {
                    player.displayClientMessage(
                            Component.translatable("message.trd.range_detonator.activated")
                                    .withStyle(ChatFormatting.GREEN),
                            true
                    );
                    return InteractionResultHolder.success(stack);
                }

                deny(level, player, stack);
                return InteractionResultHolder.fail(stack);
            }
        }

        return InteractionResultHolder.pass(stack);
    }

    private static void deny(Level level, Player player, ItemStack stack) {
        player.displayClientMessage(
                Component.translatable("message.trd.range_detonator.pos_not_loaded")
                        .withStyle(ChatFormatting.RED),
                true
        );
    }

    /**
     * Спавнит облако частиц на целевой позиции.
     */
    private void spawnRedstoneParticles(Level level, BlockPos pos) {
        for (int i = 0; i < 8; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 0.2;
            double offsetY = (level.random.nextDouble() - 0.5) * 0.2;
            double offsetZ = (level.random.nextDouble() - 0.5) * 0.2;

            level.addParticle(
                    ParticleTypes.FLASH,
                    pos.getX() + 0.5 + offsetX,
                    pos.getY() + 0.5 + offsetY,
                    pos.getZ() + 0.5 + offsetZ,
                    0.0, 0.0, 0.0
            );
        }
    }
}
