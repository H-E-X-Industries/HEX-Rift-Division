package com.trd.climate.room;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;

/**
 * Хелпер физики климата герметичных помещений.
 * 
 * 1. Инерция объема: скорость остывания и нагрева обратно пропорциональна объему комнаты.
 *    Огромная пещера (12 000+ блоков) удерживает тепло часами, тогда как маленькая будка остывает быстрее.
 * 
 * 2. Микроклимат малых помещений (10 - 500 блоков):
 *    - Нейтральная отметка: +15.0°C.
 *    - Помещения до 500 блоков пассивно сдвигают температуру в сторону +15.0°C на 100%..20% от модуля разницы.
 *    - ВНИМАНИЕ: Помещения больше 500 блоков ВООБЩЕ НЕ ИМЕЮТ стабилизации к 15 градусам.
 * 
 * 3. Потолки нагрева и баланс источников тепла:
 *    - Костер: потолок +22.0°C (комфортное жилое тепло). Костер физически НЕ МОЖЕТ нагреть комнату выше 22°C!
 *    - Костер душ: потолок +18.0°C.
 *    - Факел: потолок +16.0°C.
 *    - Обычная печь / коптильня: потолок +32.0°C.
 *    - Доменная печь: потолок +45.0°C.
 *    - Лава / открытый огонь: потолок +65.0°C.
 *    - Лед / плотный лед / синий лед: охладители до +8°C / 0°C / -10°C.
 *    - В помещениях > 500 блоков костры и факелы имеют нулевой импакт.
 * 
 * 4. Разгерметизация (утечка) и открытые двери:
 *    - При открытой двери (сквозняк): малое помещение выдувается за 15-25 секунд,
 *      тогда как пещера 12к блоков удерживает тепло 15-20 минут.
 *    - При обычной пробоине в стене: пещера 12к блоков остывает 25-35 минут.
 */
public final class RoomClimateHelper {

    public static final float NEUTRAL_TEMP = 15.0f;

    private RoomClimateHelper() {
    }

    public record ThermalSourceInfo(float targetTemp, float power, boolean isCooling) {}

    /**
     * Возвращает параметры теплового источника (потолок температуры и мощность).
     */
    public static ThermalSourceInfo getSourceInfo(BlockState state, boolean isLargeRoom) {
        if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
            if (isLargeRoom) return null;
            return new ThermalSourceInfo(24.0f, 3.6f, false);
        }
        if (state.is(Blocks.SOUL_CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
            if (isLargeRoom) return null;
            return new ThermalSourceInfo(20.0f, 2.4f, false);
        }
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) {
            if (isLargeRoom) return null;
            return new ThermalSourceInfo(18.0f, 0.4f, false);
        }
        if (state.is(com.trd.block.basic.ModBlocks.CARBON_LAMP.get())
                && state.getValue(com.trd.block.basic.industrial.lighting.ElectricLampBlock.LIT)
                && !state.getValue(com.trd.block.basic.industrial.lighting.ElectricLampBlock.BROKEN)) {
            if (isLargeRoom) return null;
            return new ThermalSourceInfo(21.0f, 0.5f, false);
        }
        if (state.is(Blocks.BLAST_FURNACE) && state.getValue(AbstractFurnaceBlock.LIT)) {
            return new ThermalSourceInfo(45.0f, 5.0f, false);
        }
        if ((state.is(Blocks.FURNACE) || state.is(Blocks.SMOKER)) && state.getValue(AbstractFurnaceBlock.LIT)) {
            return new ThermalSourceInfo(34.0f, 3.5f, false);
        }
        if (state.is(Blocks.FIRE)) {
            return new ThermalSourceInfo(55.0f, 4.0f, false);
        }
        if (state.is(Blocks.LAVA)) {
            return new ThermalSourceInfo(68.0f, 7.0f, false);
        }
        if (state.is(Blocks.BLUE_ICE)) {
            return new ThermalSourceInfo(-10.0f, 2.0f, true);
        }
        if (state.is(Blocks.PACKED_ICE)) {
            return new ThermalSourceInfo(0.0f, 1.0f, true);
        }
        if (state.is(Blocks.ICE)) {
            return new ThermalSourceInfo(8.0f, 0.6f, true);
        }
        return null;
    }

    /**
     * Рассчитывает целевую температуру пассивного микроклимата для малых помещений (10 - 500 блоков).
     */
    public static float calculatePassiveTarget(float ambientTemp, int volume) {
        int v = Math.max(1, volume);
        if (v > 500) {
            return ambientTemp;
        }

        float buff;
        if (v <= 10) {
            buff = 1.0f; // 100% буст
        } else {
            float t = (v - 10.0f) / (500.0f - 10.0f);
            buff = 1.0f - 0.8f * t; // линейная интерполяция от 1.0 (100%) до 0.2 (20%)
        }

        if (ambientTemp < 0.0f) {
            float shift = buff * Math.abs(ambientTemp);
            return Math.min(NEUTRAL_TEMP, ambientTemp + shift);
        } else if (ambientTemp < NEUTRAL_TEMP) {
            float shift = buff * (NEUTRAL_TEMP - ambientTemp);
            return Math.min(NEUTRAL_TEMP, ambientTemp + shift);
        } else {
            float shift = buff * (ambientTemp - NEUTRAL_TEMP);
            return Math.max(NEUTRAL_TEMP, ambientTemp - shift);
        }
    }

    /**
     * Возвращает коэффициент скорости пассивного теплообмена (в секунду).
     */
    public static float getThermalInertiaRate(int volume) {
        int v = Math.max(1, volume);
        float rate = 1.0f / (50.0f + v * 0.25f);
        return Mth.clamp(rate, 0.0002f, 0.03f);
    }

    /**
     * Выполняет 1-секундный шаг изменения температуры герметичной комнаты.
     */
    public static float updateRoomTemperature(
            float currentRoomTemp,
            float ambientTemp,
            int volume,
            ServerLevel level,
            Collection<BlockPos> cachedSources
    ) {
        int v = Math.max(1, volume);
        float newTemp = currentRoomTemp;
        boolean isLargeRoom = v > 500;

        // 1. Пассивное стремление к +15°C работает ИСКЛЮЧИТЕЛЬНО для малых помещений (до 500 блоков включительно).
        if (v <= 500) {
            float passiveTarget = calculatePassiveTarget(ambientTemp, v);
            float inertiaRate = getThermalInertiaRate(v);
            newTemp += (passiveTarget - newTemp) * inertiaRate;
        }

        // 2. Активные источники тепла и холода (с учетом потолка температуры каждого прибора)
        if (cachedSources != null && !cachedSources.isEmpty()) {
            float totalDelta = 0.0f;
            for (BlockPos pos : cachedSources) {
                BlockState state = level.getBlockState(pos);
                ThermalSourceInfo src = getSourceInfo(state, isLargeRoom);
                if (src == null) continue;

                if (!src.isCooling()) {
                    // Источник нагрева: греет ТОЛЬКО если текущая температура ниже его потолка!
                    // Костер нагревает кибитку до комфортных +21..+24°C и не дает перегрева выше 24°C.
                    if (newTemp < src.targetTemp()) {
                        float potential = src.targetTemp() - newTemp;
                        float rate = src.power() / (18.0f + v * 0.12f);
                        totalDelta += potential * rate;
                    }
                } else {
                    // Источник охлаждения (лед): охлаждает только если температура выше его порога
                    if (newTemp > src.targetTemp()) {
                        float potential = src.targetTemp() - newTemp;
                        float rate = src.power() / (18.0f + v * 0.12f);
                        totalDelta += potential * rate;
                    }
                }
            }

            totalDelta = Mth.clamp(totalDelta, -1.5f, 1.5f);
            newTemp += totalDelta;
        }

        return newTemp;
    }

    /**
     * Расчет температуры для разгерметизированного помещения с учетом сквозняка через дверь.
     */
    public static float updateBreachedRoomTemperature(float currentRoomTemp, float ambientTemp, int volume, boolean isDoor) {
        int v = Math.max(1, volume);
        float leakRate;
        if (isDoor) {
            // Открытая дверь / люк: мощная вентиляция и сквозняк
            // Будка 10 бл: ~10% в сек (выдувает за 10-15 сек)
            // Комната 96 бл: ~6% в сек (выдувает за 20-25 сек)
            // Помещение 500 бл: ~3% в сек (выдувает за 40 сек)
            // Пещера 12 000 бл: ~0.0015 в сек (держит тепло 15-20 мин)
            leakRate = 1.0f / (10.0f + v * 0.07f);
            leakRate = Mth.clamp(leakRate, 0.001f, 0.12f);
        } else {
            // Обычная пробоина в породе / стене (1 блок)
            // Пещера 12 000+ бл: ~0.0005 в сек (25-35 мин)
            leakRate = 1.0f / (40.0f + v * 0.15f);
            leakRate = Mth.clamp(leakRate, 0.0004f, 0.03f);
        }

        return currentRoomTemp + (ambientTemp - currentRoomTemp) * leakRate;
    }

    public static float updateBreachedRoomTemperature(float currentRoomTemp, float ambientTemp, int volume) {
        return updateBreachedRoomTemperature(currentRoomTemp, ambientTemp, volume, false);
    }
}
