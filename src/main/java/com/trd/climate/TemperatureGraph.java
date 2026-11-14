package com.trd.climate;

import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.List;

/**
 * Общая математика графика температуры.
 * <p>
 * Используется и предметным термометром (клиентская история замеров), и стационарным
 * блоком (история изменений в BlockEntity) — поэтому живёт в общем пакете, а не в клиентском.
 * <p>
 * Шкала: {@link #DEGREES_PER_PIXEL} градуса (в секунду) на пиксель, ноль — в центре окна.
 * Точка не перепрыгивает больше чем на {@link #MAX_STEP} пикселей за шаг, поэтому график
 * поднимается и опускается плавно.
 */
public final class TemperatureGraph {

    /** Ширина окна графика (число столбцов). */
    public static final int COLUMNS = 7;
    /** Высота окна графика (число строк). */
    public static final int ROWS = 7;
    /** Сколько градусов (в секунду) соответствует одному пикселю графика. */
    public static final float DEGREES_PER_PIXEL = 1.0f;
    /** Максимальный сдвиг точки за один шаг, пикселей (плавный график). */
    public static final int MAX_STEP = 1;

    private TemperatureGraph() {}

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

    /**
     * Строки графика по истории изменений температуры (от старого к новому).
     * Новое значение справа, самое старое уходит влево; столбец без данных — -1.
     *
     * @param changes история изменений температуры, °C/сек
     * @return массив длиной {@link #COLUMNS} со строками графика (0 — верх)
     */
    public static int[] rowsFromChanges(List<Float> changes) {
        int[] rows = new int[COLUMNS];
        Arrays.fill(rows, -1);
        if (changes == null || changes.isEmpty()) {
            return rows;
        }
        int size = changes.size();
        int start = Math.max(0, size - COLUMNS);
        int index = COLUMNS - (size - start);
        Integer previous = null;
        for (int i = start; i < size; i++) {
            int target = rowForChange(changes.get(i));
            int row = previous == null ? target : Mth.clamp(target, previous - MAX_STEP, previous + MAX_STEP);
            rows[index++] = row;
            previous = row;
        }
        return rows;
    }
}
