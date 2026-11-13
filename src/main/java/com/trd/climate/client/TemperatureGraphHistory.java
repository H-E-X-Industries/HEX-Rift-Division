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
 * считается, на сколько градусов температура изменилась за секунду, — из этого
 * значения получается строка графика.
 * <p>
 * Шкала графика — {@link #DEGREES_PER_PIXEL} градуса на пиксель, то есть изменение
 * температуры на 1°C сразу сдвигает точку на пиксель вверх (нагрев) или вниз (охлаждение).
 * Ноль — центр окна, строк всего {@link #ROWS}, поэтому максимум ±3°C/сек.
 * <p>
 * Точка не перепрыгивает больше чем на один пиксель за шаг: новая строка
 * ограничена предыдущей, поэтому график поднимается и опускается плавно,
 * а не перескакивает сразу на несколько строк.
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
    /** Максимальный сдвиг точки за один шаг, пикселей (плавный график). */
    public static final int MAX_STEP = 1;

    /** Строки графика — по одной на столбец, от старой к новой. */
    private static final Deque<Integer> COLUMN_ROWS = new ArrayDeque<>();

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

        int targetRow = rowForChange(change);
        Integer previousRow = COLUMN_ROWS.peekLast();
        // Плавность: точка не может перепрыгнуть больше чем на пиксель за шаг.
        int row = previousRow == null
                ? targetRow
                : Mth.clamp(targetRow, previousRow - MAX_STEP, previousRow + MAX_STEP);

        COLUMN_ROWS.addLast(row);
        while (COLUMN_ROWS.size() > COLUMNS) {
            COLUMN_ROWS.removeFirst();
        }
    }

    /**
     * Строки графика: слева самые старые, справа самые новые.
     * Столбец без данных помечен -1.
     *
     * @return массив длиной {@link #COLUMNS} со строками графика (0 — верх)
     */
    public static int[] getRows() {
        int[] rows = new int[COLUMNS];
        Arrays.fill(rows, -1);
        int index = COLUMNS - COLUMN_ROWS.size();
        for (Integer row : COLUMN_ROWS) {
            if (index >= COLUMNS) {
                break;
            }
            rows[index++] = row;
        }
        return rows;
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
        COLUMN_ROWS.clear();
        lastTemp = Float.NaN;
        lastSampleTick = Long.MIN_VALUE;
    }
}
