package com.trd.event;

import com.trd.explosion.data.CraterTintSync;
import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Сброс позиционного тинта кратера, когда блок сломали/поставили либо игрок зашёл
 * в мир/сменил измерение. Тинт — чисто косметический клиентский слой над серверной базой
 * {@link com.trd.explosion.data.CraterTintData}: установил новый блок в затемнённой зоне —
 * запись удаляется, и цвет возвращается к исходному.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public final class CraterTintEvents {

    private CraterTintEvents() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        clear(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        clear(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        for (BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
            clear(event.getLevel(), snapshot.getPos());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CraterTintSync.syncToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CraterTintSync.syncToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CraterTintSync.syncToPlayer(player);
        }
    }

    private static void clear(LevelAccessor level, BlockPos pos) {
        if (level == null || pos == null) return;
        if (!(level instanceof ServerLevel server)) return;
        CraterTintSync.removeAt(server, pos.asLong());
    }
}
