package com.trd.item.tools.cast_pickaxes;

import com.trd.main.MainRegistry;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Блокирует начало разрушения блока, пока литая кирка заряжается или стоит на кулдауне.
 * <p>
 * В 1.20.1 это делало переопределение {@code Item#onBlockStartBreak}. В 1.21.1 метода
 * у {@code Item} больше нет, а {@code ServerPlayerGameMode#handleBlockBreakAction}
 * вызывается напрямую, поэтому блокируем входную точку — NeoForge-событие
 * {@link PlayerInteractEvent.LeftClickBlock}.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class CastPickaxeBreakHandler {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }

        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof CastPickaxeItem pickaxe)) {
            return;
        }

        if (pickaxe.isLockedOut(player)) {
            event.setCanceled(true);
        }
    }
}
