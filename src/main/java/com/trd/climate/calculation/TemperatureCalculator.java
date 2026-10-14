package com.trd.climate.calculation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biome;

/**
 * Калькулятор температуры окружающей среды для игрока.
 * Учитывает: биом, высоту, глубину пещер, погоду, время суток, нахождение в воде,
 * укрытие, источники тепла, факелы и термоизоляцию брони.
 */
public class TemperatureCalculator {

    /**
     * Рассчитывает целевую температуру окружающей среды для игрока.
     * 
     * @param player Игрок
     * @param level Мир сервера
     * @param shelterLevel Уровень укрытия (0.0 - 1.0)
     * @return Температура в градусах Цельсия
     */
    public static float calculateTargetEnvTemp(Player player, ServerLevel level, float shelterLevel) {
        BlockPos pos = player.blockPosition();
        
        // 1. Базовая температура биома
        float envTemp = getBiomeTemperature(level, pos);

        // 2. Влияние высоты в горах (Y > 64)
        envTemp += getAltitudeEffect(pos.getY());

        // 3. Стабилизация в недрах земли (Y < 64 подтягивается к +15°C)
        if (pos.getY() < 64) {
            float depthFactor = Math.min(1.0f, (64 - pos.getY()) / 80.0f);
            envTemp = envTemp + (15.0f - envTemp) * depthFactor * 0.75f;
        }

        // 4. Влияние погоды и времени суток
        float weatherEffect = getWeatherEffect(level, pos);
        
        // 5. Применяем укрытие (смягчает негативные погодные эффекты)
        envTemp += applyShelterToWeather(weatherEffect, shelterLevel);

        // 6. Влияние воды (быстрое переохлаждение на холоде, приятное охлаждение в жару)
        if (player.isInWater()) {
            envTemp += getWaterEffect(envTemp);
        }

        // 7. Добавляем лучистое тепло от источников (костры, печи, лава)
        envTemp += HeatSourceHelper.calculateTotalHeat(level, pos, shelterLevel);

        // 8. Тепло от факела в руке (согревает при холоде)
        envTemp += getTorchHeat(player, envTemp);

        // 9. Тепловая изоляция от брони (защищает от холода, не перегревает в жару)
        if (envTemp < 20.0f) {
            float insulation = ArmorInsulationHelper.calculateArmorInsulation(player);
            envTemp = Math.min(20.0f, envTemp + insulation);
        }

        return envTemp;
    }
    
    /**
     * Проверяет, держит ли игрок факел в любой руке.
     * Факел согревает на величину до +3°C в сторону комфортной температуры.
     */
    private static float getTorchHeat(Player player, float currentEnvTemp) {
        if (currentEnvTemp < 20.0f) {
            if (isTorch(player.getMainHandItem()) || isTorch(player.getOffhandItem())) {
                return Math.min(3.0f, 20.0f - currentEnvTemp);
            }
        }
        return 0.0f;
    }

    private static boolean isTorch(ItemStack stack) {
        return stack.is(Blocks.TORCH.asItem()) ||
               stack.is(Blocks.SOUL_TORCH.asItem()) ||
               stack.is(Blocks.WALL_TORCH.asItem()) ||
               stack.is(Blocks.SOUL_WALL_TORCH.asItem());
    }
    
    /**
     * Применяет эффект укрытия к погодным условиям.
     * Укрытие защищает от холода, но не от жары.
     */
    private static float applyShelterToWeather(float weatherEffect, float shelterLevel) {
        if (weatherEffect < 0) {
            // При полном укрытии (1.0) холод блокируется на 80%
            return weatherEffect * (1.0f - shelterLevel * 0.8f);
        }
        return weatherEffect;
    }

    /**
     * Конвертирует ванильную температуру биома в градусы Цельсия.
     * Формула: T = 20.0 + (vanillaTemp - 0.8) * multiplier
     */
    private static float getBiomeTemperature(ServerLevel level, BlockPos pos) {
        Biome biome = level.getBiome(pos).value();
        float vanillaTemp = biome.getBaseTemperature();
        float delta = vanillaTemp - 0.8f;
        
        // Усиливаем суровость холодных биомов
        if (delta < 0) {
            delta *= 2.0f;
        }
        
        return 20.0f + delta * 20.0f;
    }

    /**
     * Расчет влияния высоты на температуру.
     * Выше Y=64: каждые 10 блоков вверх снижают температуру на 1°C.
     */
    private static float getAltitudeEffect(int y) {
        if (y > 64) {
            return -((y - 64) / 10.0f);
        }
        return 0.0f;
    }

    /**
     * Расчет влияния погоды и времени суток.
     */
    private static float getWeatherEffect(ServerLevel level, BlockPos pos) {
        boolean seesSky = level.canSeeSky(pos);
        
        if (level.isRainingAt(pos)) {
            Biome biome = level.getBiome(pos).value();
            boolean isSnowing = biome.coldEnoughToSnow(pos);
            
            float weatherPenalty = level.isThundering() ? -10.0f : -6.0f;
            if (isSnowing) {
                weatherPenalty -= 8.0f; // Метель / снегопад
            }
            return weatherPenalty;
        } else if (seesSky) {
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
     */
    private static float getWaterEffect(float currentEnvTemp) {
        if (currentEnvTemp < 20.0f) {
            return -8.0f; // Резкое переохлаждение в холодной воде
        } else if (currentEnvTemp > 25.0f) {
            return 18.0f - currentEnvTemp; // Вода приятно охлаждает в жару
        }
        return 0.0f;
    }

    /**
     * Применяет тепловую инерцию тела игрока.
     */
    public static float applyThermalInertia(float current, float target, float adaptationSpeed) {
        return current + (target - current) * adaptationSpeed;
    }
}
