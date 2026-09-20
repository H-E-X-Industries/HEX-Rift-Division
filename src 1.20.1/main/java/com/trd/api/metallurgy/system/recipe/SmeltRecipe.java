package com.trd.api.metallurgy.system.recipe;

import com.trd.api.metallurgy.system.Metal;
import net.minecraft.world.item.Item;

/**
 * @param input Входной предмет
 * @param output Выходной металл
 * @param outputUnits Выход в единицах металла (не в UNITS_PER_INGOT * n, а точное значение!)
 * @param minTemp Минимальная температура для начала плавки
 * @param heatConsumption Потребление температуры за тик (градусы/тик)
 * @param smeltTimeTicks Время плавки в тиках
 */
public record SmeltRecipe(
        Item input,
        Metal output,
        int outputUnits,
        int minTemp,
        float heatConsumption,
        int smeltTimeTicks,
        int inputCount
) {
    /** Обратная совместимость: по умолчанию 1 предмет */
    public SmeltRecipe(Item input, Metal output, int outputUnits, int minTemp,
                       float heatConsumption, int smeltTimeTicks) {
        this(input, output, outputUnits, minTemp, heatConsumption, smeltTimeTicks, 1);
    }

    public float getTotalHeatConsumption() {
        return heatConsumption * smeltTimeTicks;
    }
}