package com.trd.climate.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Клиентская история изменения температуры — источник данных для графика
 * на дисплее электронного термометра.
 * <p>
 * Температура снимается раз в секунду (в такт клиента). По двум соседним замерам
 * считается, на сколько градусов температура изменилась за секунду, — именно это
 * значение становится новым столбцом графика.
 * <p>
 * Шкала графика — {@link #DEGREES_PER_PIXEL} градуса на пиксель, то есть изменение
 * температуры на 1°C сразу сдвигает точку на пиксель вверх (нагрев) или вниз (охлаждение).
 * Ноль — центр окна, строк всего {@link #ROWS}, поэтому максимум ±3°C/сек.
 * <p>
 * Хранится не больше {@link #COLUMNS} значений, поэтому график показывает последние
 * секунды изменения температуры: новое значение всегда справа, самое старое уходит влево.
 */
@EventBusSubscriber(modid = "trd", value = Dist.CLIENT)
public final class TemperatureGraphHistory {

    /** Ширина окна графика (число столбцов). */
    public static final int COLUMNS = 7;
    /** Высота окна графика (число строк). */
    public static final int ROWS = 7;
    /** Интервал между точками графика, тиков (20 тиков = 1 секунда). */
    public static final int SAMPLE_INTERVAL_TICKS = 20;
    /** Сколько градусов (в секунду) соответствует одному пикселю графика. */
    public static final float DEGREES_PER_PIXEL = 1.0f;

    /** Изменение температуры — по одному значению на столбец графика, от старого к новому. */
    private static final Deque<Float> CHANGE_COLUMNS = new ArrayDeque<>();

    /** Температура последнего замера. */
    private static float lastTemp = Float.NaN;
    /** Тик последнего замера температуры. */
    private static long lastSampleTick = Long.MIN_VALUE;

    private TemperatureGraphHistory() {}

    /**
     * Клиентский тик локального игрока: снимает текущую температуру раз в секунду.
     */
    @SubscribeEvent
    public static void onClientPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        if (!player.level().isClientSide() || player != mc.player) {
            return;
        }
        record(player.tickCount, ClientTemperatureHandler.getCurrentTemp());
    }

    /**
     * Респавн: температура тела сбрасывается на 20°C, поэтому историю начинаем заново,
     * иначе график показал бы ложный скачок.
     */
    @SubscribeEvent
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        reset();
    }

    /**
     * Вход в мир: сбрасываем историю, чтобы график не тянул данные прошлой сессии.
     */
    @SubscribeEvent
    public static void onPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        reset();
    }

    /**
     * Кладёт новый замер температуры и, раз в секунду, новый столбец графика.
     *
     * @param tick текущий тик клиента
     * @param temp текущая температура тела, °C
     */
    public static void record(long tick, float temp) {
        if (lastSampleTick != Long.MIN_VALUE) {
            if (tick < lastSampleTick) {
                // Время клиента откатилось (смена мира) — начинаем историю заново.
                reset();
            } else if (tick - lastSampleTick < SAMPLE_INTERVAL_TICKS) {
                return;
            }
        }

        float previousTemp = lastTemp;
        long previousTick = lastSampleTick;
        lastSampleTick = tick;
        lastTemp = temp;

        if (Float.isNaN(previousTemp)) {
            // Нужен как минимум второй замер, чтобы посчитать изменение.
            return;
        }

        long span = tick - previousTick;
        // Изменение температуры за секунду (°C/сек): интервал между замерами ~1 сек.
        float change = span > 0 ? (temp - previousTemp) / (span / 20.0f) : 0.0f;

        CHANGE_COLUMNS.addLast(change);
        while (CHANGE_COLUMNS.size() > COLUMNS) {
            CHANGE_COLUMNS.removeFirst();
        }
    }

    /**
     * Значения столбцов графика: слева самые старые, справа самые новые.
     * Столбец без данных помечен {@link Float#NaN}.
     *
     * @return массив длиной {@link #COLUMNS} с изменением температуры, °C/сек
     */
    public static float[] getColumns() {
        float[] columns = new float[COLUMNS];
        Arrays.fill(columns, Float.NaN);
        int index = COLUMNS - CHANGE_COLUMNS.size();
        for (Float change : CHANGE_COLUMNS) {
            if (index >= COLUMNS) {
                break;
            }
            columns[index++] = change;
        }
        return columns;
    }

    /**
     * Номер строки окна графика для изменения температуры: ноль — в центре,
     * нагрев (положительное изменение) — вверх, охлаждение — вниз.
     *
     * @param change изменение температуры, °C/сек
     * @return индекс строки окна графика (0 — верх)
     */
    public static int rowForChange(float change) {
        int center = (ROWS - 1) / 2;
        float clamped = Mth.clamp(change, -center * DEGREES_PER_PIXEL, center * DEGREES_PER_PIXEL);
        int offset = Math.round(clamped / DEGREES_PER_PIXEL);
        return center - offset;
    }

    /** Сбрасывает историю замеров и график. */
    public static void reset() {
        CHANGE_COLUMNS.clear();
        lastTemp = Float.NaN;
        lastSampleTick = Long.MIN_VALUE;
    }
}
