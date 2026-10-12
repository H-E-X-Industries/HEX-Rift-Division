package com.trd.climate.calculation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;

/**
 * Калькулятор температуры окружающей среды для игрока.
 * Учитывает: биом, высоту, погоду, время суток, нахождение в воде.
 */
public class TemperatureCalculator {

    /**
     * Рассчитывает целевую температуру окружающей среды для игрока.
     * 
     * @param player Игрок
     * @param level Мир сервера
     * @return Температура в градусах Цельсия
     */
    public static float calculateTargetEnvTemp(Player player, ServerLevel level) {
        BlockPos pos = player.blockPosition();
        
        // 1. Базовая температура биома
        float envTemp = getBiomeTemperature(level, pos);

        // 2. Влияние высоты (Y-координата)
        envTemp += getAltitudeEffect(pos.getY());

        // 3. Влияние погоды и времени суток
        envTemp += getWeatherEffect(level, pos);

        // 4. Влияние воды
        if (player.isInWater()) {
            envTemp += getWaterEffect(envTemp);
        }

        return envTemp;
    }

    /**
     * Конвертирует ванильную температуру биома в градусы Цельсия.
     * Формула: T = 20.0 + (vanillaTemp - 0.8) * 20.0
     * 
     * Примеры:
     * - Ice Spikes (-0.5) → -6°C
     * - Snowy Plains (0.0) → 4°C
     * - Plains (0.8) → 20°C
     * - Desert (2.0) → 44°C
     */
    private static float getBiomeTemperature(ServerLevel level, BlockPos pos) {
        Biome biome = level.getBiome(pos).value();
        float vanillaTemp = biome.getBaseTemperature();
        return 20.0f + (vanillaTemp - 0.8f) * 20.0f;
    }

    /**
     * Расчет влияния высоты на температуру.
     * Уровень моря Y=64. Каждые 10 блоков вверх → -1°C
     * Ниже уровня моря стабилизируется к +15°C
     */
    private static float getAltitudeEffect(int y) {
        if (y > 64) {
            return -((y - 64) / 10.0f);
        } else {
            // Подземелья теплее
            return (64 - y) / 40.0f;
        }
    }

    /**
     * Расчет влияния погоды и времени суток.
     * - Ясный день: +3°C
     * - Ясная ночь: -4°C
     * - Дождь: -6°C
     * - Гроза/Метель: -10°C
     */
    private static float getWeatherEffect(ServerLevel level, BlockPos pos) {
        boolean seesSky = level.canSeeSky(pos);
        
        if (level.isRainingAt(pos)) {
            // Дождь или снег
            return level.isThundering() ? -10.0f : -6.0f;
        } else if (seesSky) {
            // Влияние времени суток (только если видит небо)
            if (level.isDay()) {
                return 3.0f; // Солнечный прогрев
            } else {
                return -4.0f; // Ночное выхолаживание
            }
        }
        
        return 0.0f;
    }

    /**
     * Влияние воды на температуру тела.
     * Вода обладает высокой теплоемкостью:
     * - В холодной среде (< 20°C) → вода вытягивает тепло (-8°C)
     * - В жаркой среде (> 25°C) → вода охлаждает (стремится к 18°C)
     */
    private static float getWaterEffect(float currentEnvTemp) {
        if (currentEnvTemp < 20.0f) {
            return -8.0f; // Переохлаждение в холодной воде
        } else if (currentEnvTemp > 25.0f) {
            return 18.0f - currentEnvTemp; // Охлаждение в жаркой среде
        }
        return 0.0f;
    }

    /**
     * Применяет тепловую инерцию тела игрока.
     * Температура тела плавно стремится к целевой температуре среды.
     * 
     * @param current Текущая температура тела
     * @param target Целевая температура среды
     * @param adaptationSpeed Скорость адаптации (обычно 0.02-0.025)
     * @return Новая температура тела
     */
    public static float applyThermalInertia(float current, float target, float adaptationSpeed) {
        return current + (target - current) * adaptationSpeed;
    }
}
