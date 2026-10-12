package com.trd.climate.calculation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;

/**
 * Калькулятор температуры окружающей среды для игрока.
 * Учитывает: биом, высоту, погоду, время суток, нахождение в воде, укрытие и источники тепла.
 */
public class TemperatureCalculator {

    /**
     * Рассчитывает целевую температуру окружающей среды для игрока.
     * 
     * @param player Игрок
     * @param level Мир сервера
     * @param shelterLevel Уровень укрытия (передается извне для кэширования)
     * @return Температура в градусах Цельсия
     */
    public static float calculateTargetEnvTemp(Player player, ServerLevel level, float shelterLevel) {
        BlockPos pos = player.blockPosition();
        
        // 1. Базовая температура биома
        float envTemp = getBiomeTemperature(level, pos);

        // 2. Влияние высоты (Y-координата)
        envTemp += getAltitudeEffect(pos.getY());

        // 3. Влияние погоды и времени суток
        float weatherEffect = getWeatherEffect(level, pos);
        
        // 4. Применяем укрытие (смягчает негативные погодные эффекты)
        envTemp += applyShelterToWeather(weatherEffect, shelterLevel);

        // 5. Влияние воды
        if (player.isInWater()) {
            envTemp += getWaterEffect(envTemp);
        }

        // 6. Добавляем тепло от источников (костры, печи, лава)
        envTemp += HeatSourceHelper.calculateTotalHeat(level, pos, shelterLevel);

        // 7. Тепло от факела в руке
        envTemp += getTorchHeat(player);

        // 8. Тепловая защита от брони
        envTemp += ArmorInsulationHelper.calculateArmorInsulation(player);

        return envTemp;
    }
    
    /**
     * Рассчитывает тепло от факела в левой руке игрока.
     * Факел дает +3°C, когда игрок держит его в левой руке (offhand).
     */
    private static float getTorchHeat(Player player) {
        // Проверяем, держит ли игрок факел в левой руке
        if (player.getOffhandItem().is(net.minecraft.world.level.block.Blocks.TORCH.asItem()) ||
            player.getOffhandItem().is(net.minecraft.world.level.block.Blocks.SOUL_TORCH.asItem()) ||
            player.getOffhandItem().is(net.minecraft.world.level.block.Blocks.REDSTONE_TORCH.asItem())) {
            return 3.0f; // Факел согревает на 3°C
        }
        return 0.0f;
    }
    
    /**
     * Применяет эффект укрытия к погодным условиям.
     * Укрытие защищает от холода, но не от жары.
     */
    private static float applyShelterToWeather(float weatherEffect, float shelterLevel) {
        if (weatherEffect < 0) {
            // Негативный эффект (холод) смягчается укрытием
            // При полном укрытии (1.0) холод блокируется на 80%
            return weatherEffect * (1.0f - shelterLevel * 0.8f);
        }
        // Положительный эффект (тепло) не изменяется
        return weatherEffect;
    }

    /**
     * Конвертирует ванильную температуру биома в градусы Цельсия.
     * Формула: T = 20.0 + (vanillaTemp - 0.8) * multiplier
     * Холодные биомы усилены в 2 раза для более сурового выживания.
     * 
     * Примеры после баланса:
     * - Ice Spikes (-0.5) → -32°C (было -6°C)
     * - Snowy Plains (0.0) → -12°C (было 4°C)
     * - Plains (0.8) → 20°C (без изменений)
     * - Desert (2.0) → 44°C (без изменений)
     */
    private static float getBiomeTemperature(ServerLevel level, BlockPos pos) {
        Biome biome = level.getBiome(pos).value();
        float vanillaTemp = biome.getBaseTemperature();
        float delta = vanillaTemp - 0.8f;
        
        // Усиливаем холодные биомы в 2 раза
        if (delta < 0) {
            delta *= 2.0f; // Холод × 2
        }
        
        return 20.0f + delta * 20.0f;
    }

    /**
     * Расчет влияния высоты на температуру.
     * Уровень моря Y=64. Каждые 10 блоков вверх → -1°C
     * Ниже уровня моря: баланс между холодом пещер и теплом недр
     * - Эффект пещер: -0.5°C на каждые 10 блоков вниз
     * - Эффект недр (в 2 раза слабее): +0.25°C на каждые 10 блоков вниз
     * - Итого: -0.25°C на каждые 10 блоков вниз
     */
    private static float getAltitudeEffect(int y) {
        if (y > 64) {
            // Высоко в горах холоднее
            return -((y - 64) / 10.0f);
        } else {
            // Баланс: холод пещер (-0.5) + тепло недр (+0.25) = -0.25°C на 10 блоков
            // На Y=64: 0°C, на Y=44: -0.5°C, на Y=24: -1.0°C, на Y=0: -1.6°C
            float caveEffect = -((64 - y) / 20.0f);    // Холод пещер
            float depthEffect = ((64 - y) / 40.0f);    // Тепло недр (в 2 раза слабее)
            return caveEffect + depthEffect;
        }
    }

    /**
     * Расчет влияния погоды и времени суток.
     * - Ясный день: +3°C
     * - Ясная ночь: -4°C
     * - Дождь: -6°C
     * - Гроза/Метель: -10°C
     * - Снегопад (в холодном биоме): дополнительно -8°C
     */
    private static float getWeatherEffect(ServerLevel level, BlockPos pos) {
        boolean seesSky = level.canSeeSky(pos);
        
        if (level.isRainingAt(pos)) {
            // Проверяем, идет ли снег (холодный биом)
            Biome biome = level.getBiome(pos).value();
            boolean isSnowing = biome.coldEnoughToSnow(pos);
            
            float weatherPenalty = level.isThundering() ? -10.0f : -6.0f;
            
            // Снегопад дает дополнительный штраф -8°C
            if (isSnowing) {
                weatherPenalty -= 8.0f;
            }
            
            return weatherPenalty;
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
