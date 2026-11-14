package com.trd.climate.client;

import com.trd.climate.TemperatureGraph;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

/**
 * Клиентская история изменения температуры — источник данных для графика
 * на дисплее предметного электронного термометра.
 * <p>
 * Температура снимается раз в секунду (в такт клиента). По двум соседним замерам
 * считается, на сколько градусов температура изменилась за секунду, — из этого
 * значения получается строка графика.
 * <p>
 * Шкала и правило плавности — в {@link TemperatureGraph}: один градус — один пиксель,
 * точка не перепрыгивает больше чем на пиксель за шаг.
 * <p>
 * Хранится не больше {@link TemperatureGraph#COLUMNS} значений, поэтому график показывает
 * последние секунды изменения температуры: новое значение всегда справа, самое старое уходит влево.
 */
@EventBusSubscriber(modid = "trd", value = Dist.CLIENT)
public final class TemperatureGraphHistory {

    /** Интервал между точками графика, тиков (20 тиков = 1 секунда). */
    public static final int SAMPLE_INTERVAL_TICKS = 20;

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
        while (CHANGE_COLUMNS.size() > TemperatureGraph.COLUMNS) {
            CHANGE_COLUMNS.removeFirst();
        }
    }

    /**
     * Строки графика: слева самые старые, справа самые новые.
     * Столбец без данных помечен -1.
     *
     * @return массив длиной {@link TemperatureGraph#COLUMNS} со строками графика (0 — верх)
     */
    public static int[] getRows() {
        return TemperatureGraph.rowsFromChanges(new ArrayList<>(CHANGE_COLUMNS));
    }

    /** Сбрасывает историю замеров и график. */
    public static void reset() {
        CHANGE_COLUMNS.clear();
        lastTemp = Float.NaN;
        lastSampleTick = Long.MIN_VALUE;
    }
}
