package com.trd.climate.room;

import com.trd.climate.network.SyncThermalStatesPayload;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиентская копия живого состояния пассивных источников.
 * <p>
 * Клиент не может читать SavedData сервера, поэтому без этого кэша дебаг-оверлей
 * и термометр видели бы паспортные значения (лаву на 1200 °C и 100% запас),
 * пока сервер считал остывшую лаву с запасом 40%. Пакет приходит раз в секунду
 * вместе с синхронизацией комнат, кэш заменяется целиком.
 */
public final class ClientThermalStateCache {

    private static final Map<Long, ThermalStateStore.ThermalState> STATES = new ConcurrentHashMap<>();
    /** Признак того, что кэш обновился и кэш источников дебага нужно пересобрать. */
    private static volatile boolean dirty = false;

    private ClientThermalStateCache() {
    }

    public static boolean consumeDirty() {
        boolean result = dirty;
        dirty = false;
        return result;
    }

    public static void replace(java.util.List<SyncThermalStatesPayload.Entry> data) {
        STATES.clear();
        dirty = true;
        for (SyncThermalStatesPayload.Entry entry : data) {
            STATES.put(entry.pos().asLong(),
                    new ThermalStateStore.ThermalState(entry.reserve(), entry.physical()));
        }
    }

    public static void clear() {
        STATES.clear();
    }

    public static ThermalStateStore.ThermalState get(BlockPos pos) {
        return STATES.get(pos.asLong());
    }
}
