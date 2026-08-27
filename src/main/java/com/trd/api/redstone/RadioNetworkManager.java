package com.trd.api.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RadioNetworkManager {
    public static final ResourceLocation ID = new ResourceLocation("trd", "radio_network");

    private final Level level;
    // канал → позиции приёмников
    private final Map<String, Set<BlockPos>> receiversByChannel = new ConcurrentHashMap<>();
    // позиция → текущий канал (быстрое удаление)
    private final Map<BlockPos, String> channelByPos = new ConcurrentHashMap<>();

    public RadioNetworkManager(Level level) {
        this.level = level;
    }

    public static RadioNetworkManager get(Level level) {
        return level.getCapability(CAPABILITY).orElse(null);
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

    // ── Capability boilerplate ──
    public static final Capability<RadioNetworkManager> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    public static class Provider implements ICapabilityProvider {
        private final LazyOptional<RadioNetworkManager> holder;
        public Provider(Level level) {
            this.holder = LazyOptional.of(() -> new RadioNetworkManager(level));
        }
        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable net.minecraft.core.Direction side) {
            return CAPABILITY.orEmpty(cap, holder);
        }
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Level> event) {
        event.addCapability(ID, new Provider(event.getObject()));
    }

    public static void registerCapability(RegisterCapabilitiesEvent event) {
        event.register(RadioNetworkManager.class);
    }
}