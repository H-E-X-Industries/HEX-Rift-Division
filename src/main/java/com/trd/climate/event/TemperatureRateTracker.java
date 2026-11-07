package com.trd.climate.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Накопитель замеров температуры тела игрока для расчёта скорости её изменения.
 * <p>
 * Сэмплируется раз в секунду (в такт логике климата) и хранит скользящее окно
 * глубиной {@link #WINDOW_TICKS} тиков. Скорость считается по крайним точкам окна:
 * rate = (T_кон - T_нач) / dT, °C/сек.
 * <p>
 * Используется электронным термометром при ПКМ (сообщение в чат).
 */
@EventBusSubscriber(modid = "trd")
public class TemperatureRateTracker {

    /** Глубина окна измерения, тиков (100 тиков = 5 секунд). */
    private static final long WINDOW_TICKS = 100;
    /** Максимум сэмплов в окне (1 замер в секунду -> 5 сэмплов с запасом). */
    private static final int MAX_SAMPLES = 6;
    /** Минимальный интервал для расчёта, тиков — менее него считаем скорость нулевой. */
    private static final long MIN_SPAN_TICKS = 10;

    private record Sample(long tick, float temp) {}

    private static final Map<UUID, Deque<Sample>> HISTORY = new ConcurrentHashMap<>();

    /** Записывает замер температуры (вызывается при серверном тике игрока, сам фильтрует до 1 раза в сек). */
    public static void record(ServerPlayer player, float temp) {
        if ((player.tickCount + player.getId()) % 20 != 0) {
            return;
        }
        Deque<Sample> deque = HISTORY.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(new Sample(player.tickCount, temp));
            while (!deque.isEmpty() && player.tickCount - deque.peekFirst().tick() > WINDOW_TICKS) {
                deque.removeFirst();
            }
            while (deque.size() > MAX_SAMPLES) {
                deque.removeFirst();
            }
        }
    }

    /** Скорость изменения температуры тела, °C/сек (0.0 если данных мало). */
    public static float getRatePerSecond(ServerPlayer player) {
        Deque<Sample> deque = HISTORY.get(player.getUUID());
        if (deque == null) return 0.0f;
        synchronized (deque) {
            if (deque.size() < 2) return 0.0f;
            Sample first = deque.peekFirst();
            Sample last = deque.peekLast();
            long span = last.tick() - first.tick();
            if (span <= 0 || span < MIN_SPAN_TICKS) return 0.0f;
            float rate = (last.temp() - first.temp()) / (span / 20.0f);
            return Mth.clamp(rate, -10.0f, 10.0f);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            HISTORY.remove(event.getEntity().getUUID());
        }
    }
}
