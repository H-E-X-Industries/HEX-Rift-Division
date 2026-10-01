package com.trd.api.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр приёмников радиосети по каналам, один на уровень (измерение).
 * <p>
 * В 1.20.1 это была Forge Capability на {@code Level}
 * ({@code AttachCapabilitiesEvent} + {@code CapabilityManager} + {@code LazyOptional}).
 * В NeoForge 21.1 старой системы Capability для уровней больше нет — остались только
 * {@code BlockCapability}/{@code ItemCapability}, а данные уровня принято хранить
 * либо через {@code level.getDataStorage()}, либо через вложенный статический реестр.
 * Тут второе: состояние чисто рантаймовое (не пишется на диск и не синхронизируется —
 * так же, как и в 1.20.1), поэтому тащить {@code SavedData} с пустым
 * {@code save} незачем.
 */
public class RadioNetworkManager {

    private final Level level;

    // канал → позиции приёмников
    private final Map<String, Set<BlockPos>> receiversByChannel = new ConcurrentHashMap<>();
    // позиция → текущий канал (быстрое удаление)
    private final Map<BlockPos, String> channelByPos = new ConcurrentHashMap<>();

    /** Менеджер на уровень. Ключ — измерение, записей столько же, сколько миров. */
    private static final Map<ResourceKey<Level>, RadioNetworkManager> BY_LEVEL = new ConcurrentHashMap<>();

    public RadioNetworkManager(Level level) {
        this.level = level;
    }

    public static RadioNetworkManager get(Level level) {
        return BY_LEVEL.computeIfAbsent(level.dimension(), key -> new RadioNetworkManager(level));
    }

    /** Регистрация приёмника (вызывать в onLoad и при смене канала) */
    public void registerReceiver(BlockPos pos, String channel) {
        if (level == null || level.isClientSide) return;
        String old = channelByPos.put(pos, channel);
        if (old != null && !old.equals(channel)) {
            receiversByChannel.computeIfAbsent(old, k -> ConcurrentHashMap.newKeySet()).remove(pos);
        }
        if (channel != null && !channel.isEmpty()) {
            receiversByChannel.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(pos);
        }
    }

    /** Удаление приёмника (вызывать в setRemoved) */
    public void unregisterReceiver(BlockPos pos) {
        if (level == null || level.isClientSide) return;
        String old = channelByPos.remove(pos);
        if (old != null) {
            Set<BlockPos> set = receiversByChannel.get(old);
            if (set != null) {
                set.remove(pos);
                if (set.isEmpty()) receiversByChannel.remove(old);
            }
        }
    }

    /** Получить все приёмники канала */
    public Set<BlockPos> getReceivers(String channel) {
        if (channel == null || channel.isEmpty()) return Collections.emptySet();
        Set<BlockPos> set = receiversByChannel.get(channel);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }
}
