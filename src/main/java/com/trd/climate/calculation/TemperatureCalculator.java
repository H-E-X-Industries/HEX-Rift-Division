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
     * Рассчитывает естественную температуру окружающей среды снаружи для указанных координат.
     * Используется контроллерами герметичных помещений для расчета теплообмена с внешней средой.
     */
    public static float calculateOutdoorAmbient(ServerLevel level, BlockPos pos) {
        float envTemp = getBiomeTemperature(level, pos);
        envTemp += getAltitudeEffect(pos.getY());
        envTemp = applySubterraneanStabilization(level, pos, envTemp);
        envTemp += getWeatherEffect(level, pos);
        return envTemp;
    }

    /**
     * Стабилизация в недрах земли и толще гор (подтягивание к геотермальной нейтрали +15°C).
     * Учитывает как глубину ниже уровня моря (Y < 64), так и толщу породы над головой (пещеры и норы в горах).
     */
    public static float applySubterraneanStabilization(ServerLevel level, BlockPos pos, float currentTemp) {
        boolean underCeiling = !level.canSeeSky(pos);
        int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        int depthFromSurface = surfaceY - pos.getY();

        float depthFactor = 0.0f;
        // 1. Глубина под уровнем моря (шахты, глубокие пещеры Y < 64)
        if (pos.getY() < 64) {
            depthFactor = Math.max(depthFactor, Math.min(1.0f, (64 - pos.getY()) / 60.0f));
        }
        // 2. Толща породы над головой в горах и холмах (горные пещеры, норы, штольни)
        if (underCeiling && depthFromSurface >= 4) {
            depthFactor = Math.max(depthFactor, Math.min(1.0f, (depthFromSurface - 3) / 10.0f));
        }

        if (depthFactor > 0.0f) {
            return currentTemp + (15.0f - currentTemp) * depthFactor * 0.75f;
        }
        return currentTemp;
    }

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
        
        // 0. Проверяем, находится ли игрок в изолированном помещении (Tier 1 или Tier 2)
        com.trd.climate.room.SealedRoom room = com.trd.climate.room.SealedRoomManager.findRoomAt(level, pos);
        if (room != null && room.isStructureValid()) {
            float envTemp = room.getRoomTemperature();

            if (!room.isSealed()) {
                // Если дверь открыта или есть пробоина:
                // Сквозняк! Игрок сразу ощущает уличный холод (35% примесь уличного воздуха)
                float outdoorAmbient = calculateOutdoorAmbient(level, pos);
                envTemp = net.minecraft.util.Mth.lerp(0.35f, envTemp, outdoorAmbient);
            }

            // Влияние воды (если внутри комнаты есть бассейн)
            if (player.isInWater()) {
                envTemp += getWaterEffect(envTemp);
            }

            // Дополнительное локальное лучистое тепло в упор к источникам внутри комнаты
            // (ощутимый уютный жар около огня до +26°C, согревающий в упор)
            float radiant = HeatSourceHelper.calculateTotalHeat(level, pos, 1.0f);
            if (radiant > 0.0f) {
                float roomTargetWithRadiant = Math.min(26.0f, envTemp + radiant * 0.65f);
                envTemp = Math.max(envTemp, roomTargetWithRadiant);
            }

            // Тепло от факела в руке
            envTemp += getTorchHeat(player, envTemp);

            // Тепловая изоляция брони (если в комнате прохладно)
            if (envTemp < 20.0f) {
                float insulation = ArmorInsulationHelper.calculateArmorInsulation(player);
                envTemp = Math.min(20.0f, envTemp + insulation);
            }

            return envTemp;
        }

        // 1. Базовая температура биома
        float envTemp = getBiomeTemperature(level, pos);

        // 2. Влияние высоты в горах (Y > 64)
        envTemp += getAltitudeEffect(pos.getY());

        // 3. Стабилизация в недрах земли и толще гор (подтягивание к геотермальной нейтрали +15°C)
        envTemp = applySubterraneanStabilization(level, pos, envTemp);

        // 4. Влияние погоды и времени суток
        float weatherEffect = getWeatherEffect(level, pos);
        envTemp += applyShelterToWeather(weatherEffect, shelterLevel);

        // 5. Защита базового укрытия (норы в горе/земле, шалаши, землянки, неутепленные дома)
        // Стены и крыша блокируют морозный ветер биома и палящее солнце
        if (shelterLevel > 0.05f) {
            if (envTemp < 15.0f) {
                // Защита от холода: укрытие согревает до 55% от разницы с нейтралью +15°C
                float shelterProtection = (15.0f - envTemp) * shelterLevel * 0.55f;
                envTemp += shelterProtection;
            } else if (envTemp > 25.0f) {
                // Защита от жары: тень от навеса блокирует перегрев
                float shelterProtection = (envTemp - 25.0f) * shelterLevel * 0.40f;
                envTemp -= shelterProtection;
            }
        }

        // 6. Влияние воды (быстрое переохлаждение на холоде, приятное охлаждение в жару)
        if (player.isInWater()) {
            envTemp += getWaterEffect(envTemp);
        }

        // 7. Добавляем лучистое тепло от источников (костры, печи, лава)
        float radiantHeat = HeatSourceHelper.calculateTotalHeat(level, pos, shelterLevel);
        if (radiantHeat > 0.0f) {
            float maxComfortFromFire = 26.0f;
            if (envTemp < maxComfortFromFire) {
                envTemp = Math.min(maxComfortFromFire, envTemp + radiantHeat);
            } else if (radiantHeat > 25.0f) {
                // Экстремальные источники (лава в упор)
                envTemp += (radiantHeat - 25.0f) * 0.5f;
            }
        }

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
     * Холодные биомы усилены для сурового выживания (до -30°C).
     * Жаркие биомы сбалансированы так, чтобы в саванне и пустыне днем
     * на солнце температура достигала 30-32°C (состояние "Жара"),
     * вызывая повышенный расход сытости, но не убивая игрока.
     */
    private static float getBiomeTemperature(ServerLevel level, BlockPos pos) {
        Biome biome = level.getBiome(pos).value();
        float vanillaTemp = biome.getBaseTemperature();
        float delta = vanillaTemp - 0.8f;
        
        // Усиливаем суровость холодных биомов
        if (delta < 0) {
            delta *= 2.0f;
            return 20.0f + delta * 20.0f;
        } else {
            // Жаркие биомы: при vanillaTemp=2.0 (Desert) базовая температура 29°C (на солнце максимум 32°C)
            return 20.0f + delta * 7.5f;
        }
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
