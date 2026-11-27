package com.trd.multiblock.industrial.drilling;

import com.trd.main.MainRegistry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Set;

/**
 * Позволяет вставлять буровые трубы в пустые боковые слоты барабана PipeDrum
 * по 3D-рейкасту костей даже при клике ПКМ в воздухе по визуальному барабану.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public final class PipeDrumInteractionEvents {

    private PipeDrumInteractionEvents() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!PipeDrumBlockEntity.isDrillPipeItem(stack)) {
            return;
        }

        Level level = event.getLevel();
        Vec3 eye = player.getEyePosition(1.0f);
        double reach = player.blockInteractionRange() + 1.5;
        Vec3 lookEnd = eye.add(player.getViewVector(1.0f).scale(reach));

        Set<PipeDrumBlockEntity> candidates = level.isClientSide
                ? PipeDrumBlockEntity.CLIENT_DRUMS
                : PipeDrumBlockEntity.SERVER_DRUMS;

        PipeDrumBlockEntity bestDrum = null;
        int bestSlot = -1;
        double bestDistSq = Double.MAX_VALUE;

        for (PipeDrumBlockEntity drum : candidates) {
            if (drum.getLevel() != level || drum.isRemoved()) continue;
            if (drum.getBlockPos().distToCenterSqr(eye) > (reach + 5.0) * (reach + 5.0)) continue;

            int slot = drum.raycastEmptySlot(eye, lookEnd);
            if (slot >= 0) {
                double dSq = drum.computeSlotWorldCenter(slot, 0.0f).distanceToSqr(eye);
                if (dSq < bestDistSq) {
                    bestDistSq = dSq;
                    bestDrum = drum;
                    bestSlot = slot;
                }
            }
        }

        if (bestDrum != null && bestSlot >= 0) {
            if (!level.isClientSide) {
                if (bestDrum.insertPipeIntoSlot(bestSlot)) {
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                    level.playSound(null, bestDrum.getBlockPos(), SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.55f, 1.15f);
                }
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            event.setCanceled(true);
        }
    }
}
