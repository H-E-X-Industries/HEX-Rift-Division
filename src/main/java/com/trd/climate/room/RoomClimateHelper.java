package com.trd.climate.room;

import com.trd.api.climate.HeatSourceDef;
import com.trd.api.climate.HeatSourceRegistry;
import com.trd.api.climate.IHeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
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
 * 3. Потолки нагрева и баланс источников тепла.
 *    Цель комнатная — та же шкала, что и у игрока ({@code 15..25 °C} комфорт, урон выше
 *    {@code 40 °C}), поэтому цель источника выводится из его <b>ощущаемого</b> выхода
 *    ({@code outputHeat}/{@code coolingPower}), а не из физической температуры блока:
 *    {@code цель = 15 ± выход × запас}. иначе костёр (700 °C) «прогревал» бы комнату
 *    до 600 °C, а коксовая печь — больше 1000 °C.
 *    - Костёр: ~22 °C, душный ~21 °C, факел ~17 °C.
 *    - Обычная печь / коптильня: ~27 °C, доменная ~34 °C.
 *    - Лава в комнате: ~38 °C, открытый огонь ~31 °C.
 *    - Коксовая печь на максимуме: ~40 °C (порог урона).
 *    - Лёд / плотный / синий: ~7 / 4 / 0 °C.
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
     * Возвращает параметры теплового источника (целевая температура и мощность).
     * <p>
     * Цель — нейтраль {@value #NEUTRAL_TEMP} °C плюс/минус ощущаемый выход источника
     * ({@code outputHeat}/{@code coolingPower}) с учётом живого запаса. Физическая
     * температура блока здесь не участвует: она в сотни градусов и сделала бы комнату
     * смертельно горячей.
     * <p>
     * Мощность влияния на комнату зависит от объёма: больший объём требует больше
     * энергии для изменения температуры.
     */
    public static ThermalSourceInfo getSourceInfo(Level level, BlockPos pos, boolean isLargeRoom, int roomVolume) {
        BlockState state = level.getBlockState(pos);

        float deviceTemp = 1.0f; // ванильные источники: активное состояние = полная мощность
        float reserve = 1.0f;    // пассивные: запас эффективности из живого состояния
        HeatSourceDef def;

        if (HeatSourceRegistry.usesBlockEntity(state.getBlock())
                && level.getBlockEntity(pos) instanceof IHeatSource heatSource) {
            if (!heatSource.isHeatActive()) {
                return null;
            }
            def = HeatSourceRegistry.byId(heatSource.getHeatSourceId());
            if (def == null) {
                return null;
            }
            deviceTemp = heatSource.getSourceTemperature();
            if (def.powerAt(deviceTemp) <= 0.0f) {
                return null;
            }
        } else {
            def = HeatSourceRegistry.resolveState(state);
            if (def == null) {
                return null;
            }
            // Пассивный источник: живой запас из хранилища состояний. Даже вынесенный
            // из комнаты блок сохраняет свою эффективность (см. спецификацию v2).
            var stored = ThermalStateStore.stateAt(level, pos);
            if (stored != null) {
                reserve = stored.reserve();
                // deviceTemp остаётся 1.0: у пассивных источников активность задана
                // состоянием блока, а живая температура влияет только на выход.
                // Подставлять её в powerAt() нельзя — у льда surfaceTemp = 0.
            }
        }

        if (isLargeRoom && def.negligibleInLargeRooms()) {
            return null;
        }

        float basePower = def.powerAt(deviceTemp) * reserve;
        if (basePower <= 0.0f) {
            return null;
        }
        
        // Ощущаемый выход источника — в той же шкале, что и температура игрока.
        // Живой запас (лава остывает, лёд тает) срезает и цель, и мощность.
        float baseOutput = def.isCooling() ? def.coolingPower() : def.outputHeat();
        float effectiveOutput = baseOutput * reserve;
        float target = def.isCooling()
                ? NEUTRAL_TEMP - effectiveOutput
                : NEUTRAL_TEMP + effectiveOutput;

        // Мощность: чем больше комната, тем сложнее её прогреть.
        // Делитель 20 (вместо прежних 100) компенсирует переход outputHeat из физических
        // сотен градусов в ощущаемые единицы — иначе комната прогревалась бы за часы.
        float volumeScale = 100.0f; // 100 блоков = 1x масштаб
        float power = (baseOutput / 20.0f) * basePower / (1.0f + roomVolume / volumeScale);

        return new ThermalSourceInfo(target, power, def.isCooling());
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
            Level level,
            Collection<BlockPos> cachedSources
    ) {
        int v = Math.max(1, volume);
        float newTemp = currentRoomTemp;
        boolean isLargeRoom = v > 500;

        // 1. Пассивное стремление к +15°C работает для малых помещений (до 500 блоков).
        if (v <= 500) {
            float passiveTarget = calculatePassiveTarget(ambientTemp, v);
            float inertiaRate = getThermalInertiaRate(v);
            newTemp += (passiveTarget - newTemp) * inertiaRate;
        } else {
            // Для больших комнат: если нет источников, температура спадает к нейтрали
            // Скорость спада зависит от объёма (больше объём = медленнее спад)
            boolean hasSources = cachedSources != null && !cachedSources.isEmpty();
            if (!hasSources) {
                float inertiaRate = getThermalInertiaRate(v);
                newTemp += (NEUTRAL_TEMP - newTemp) * inertiaRate;
            }
        }

        // 2. Активные источники тепла и холода
        if (cachedSources != null && !cachedSources.isEmpty()) {
            float totalDelta = 0.0f;
            for (BlockPos pos : cachedSources) {
                ThermalSourceInfo src = getSourceInfo(level, pos, isLargeRoom, v);
                if (src == null) continue;

                if (!src.isCooling()) {
                    // Источник нагрева: греет ТОЛЬКО если текущая температура ниже его целевой!
                    if (newTemp < src.targetTemp()) {
                        float potential = src.targetTemp() - newTemp;
                        // Скорость зависит от разницы температур (чем больше разница, тем быстрее)
                        float tempFactor = Math.min(potential / 50.0f, 2.0f); // до 2x при разнице 100°C
                        float rate = src.power() * tempFactor / (18.0f + v * 0.12f);
                        totalDelta += potential * rate;
                    }
                } else {
                    // Источник охлаждения: охлаждает только если температура выше его целевой
                    if (newTemp > src.targetTemp()) {
                        float potential = src.targetTemp() - newTemp;
                        // Скорость зависит от разницы температур (чем больше разница, тем быстрее)
                        float tempFactor = Math.min(Math.abs(potential) / 50.0f, 2.0f);
                        float rate = src.power() * tempFactor / (18.0f + v * 0.12f);
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
