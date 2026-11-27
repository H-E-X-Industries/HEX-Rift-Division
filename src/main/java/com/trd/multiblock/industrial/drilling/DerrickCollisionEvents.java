package com.trd.multiblock.industrial.drilling;

import com.trd.block.basic.ModBlocks;
import com.trd.main.MainRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Запрещает прямое ломание прокси-коллизии каркаса вышки:
 * разборка возможна исключительно путём демонтажа роторного стола.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public final class DerrickCollisionEvents {

    private DerrickCollisionEvents() {}

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getState().is(ModBlocks.DERRICK_COLLISION.get())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().getBlockState(event.getPos()).is(ModBlocks.DERRICK_COLLISION.get())) {
            event.setCanceled(true);
        }
    }
}
