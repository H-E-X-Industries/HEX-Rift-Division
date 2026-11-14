package com.trd.climate.calculation;

import com.trd.climate.room.SealedRoom;
import com.trd.climate.room.SealedRoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Калькулятор температуры окружающей среды для игрока и приборов.
 * Учитывает: биом, высоту, глубину пещер, погоду, время суток, нахождение в воде,
 * укрытие, источники тепла, факелы и термоизоляцию брони.
 * <p>
 * Класс работает и на сервере, и на клиенте ({@link Level}, а не {@code ServerLevel}):
 * клиентский дебаг-оверлей считает температуру тем же кодом, что и серверные
 * приборы, поэтому показания не расходятся.
 */
public class TemperatureCalculator {

    /**
     * Рассчитывает естественную температуру окружающей среды снаружи для указанных координат.
     * Используется контроллерами герметичных помещений для расчета теплообмена с внешней средой.
     */
    public static float calculateOutdoorAmbient(Level level, BlockPos pos) {
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
    public static float applySubterraneanStabilization(Level level, BlockPos pos, float currentTemp) {
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
    public static float calculateTargetEnvTemp(Player player, Level level, float shelterLevel) {
        float envTemp = calculateTargetEnvTempCore(player, level, player.blockPosition(), shelterLevel);

        // Тепло от факела в руке (согревает при холоде)
        envTemp += getTorchHeat(player, envTemp);

        // Тепловая изоляция от брони (защищает от холода, не перегревает в жару)
        if (envTemp < 20.0f) {
            float insulation = ArmorInsulationHelper.calculateArmorInsulation(player);
            envTemp = Math.min(20.0f, envTemp + insulation);
        }

        return envTemp;
    }

    /**
     * Температура в точке мира для стационарных приборов (термометры).
     * <p>
     * Тот же расчёт, что для стоящего в этой точке игрока, но без персональных
     * факторов (факел в руке, изоляция брони): биом, высота, недра, погода, вода,
     * укрытие и лучистое тепло ближайших источников.
     * Внутри герметичного помещения возвращается комнатная температура
     * (со сквозняком при утечке и локальным жаром у источников).
     */
    public static float calculatePointTemp(Level level, BlockPos pos) {
        float shelterLevel = ShelterHelper.calculateShelterLevel(level, pos);
        return calculateTargetEnvTempCore(null, level, pos, shelterLevel);
    }

    /**
     * Вариант для отладочной визуализации: то же измерение в точке, но с
     * заранее найденными источниками тепла (не тратит время на повторное сканирование
     * для каждого блока поля).
     */
    public static float calculatePointTemp(Level level, BlockPos pos,
                                           List<HeatSourceHelper.HeatSource> precomputedSources) {
        float shelterLevel = ShelterHelper.calculateShelterLevel(level, pos);
        return calculateTargetEnvTempCore(null, level, pos, shelterLevel, precomputedSources);
    }

    /**
     * Общее ядро расчёта температуры среды в точке.
     *
     * @param player игрок (может быть null для приборов); от него зависит только
     *               учёт воды — факел и броня применяются снаружи
     */
    private static float calculateTargetEnvTempCore(@Nullable Player player, Level level, BlockPos pos, float shelterLevel) {
        return calculateTargetEnvTempCore(player, level, pos, shelterLevel, null);
    }

    private static float calculateTargetEnvTempCore(@Nullable Player player, Level level, BlockPos pos,
                                                    float shelterLevel,
                                                    @Nullable List<HeatSourceHelper.HeatSource> precomputedSources) {
        // Вода в точке влияет и на игрока, и на прибор: промокший термометр меряет воду
        boolean inWater = (player != null && player.isInWater())
                || level.getFluidState(pos).is(FluidTags.WATER);

        // 0. Проверяем, находится ли точка в изолированном помещении (Tier 1 или Tier 2)
        SealedRoom room = SealedRoomManager.findRoomAt(level, pos);
        if (room != null && room.isStructureValid()) {
            float envTemp = room.getRoomTemperature();

            if (!room.isSealed()) {
                // Если дверь открыта или есть пробоина:
                // Сквозняк! Игрок сразу ощущает уличный холод (35% примесь уличного воздуха)
                float outdoorAmbient = calculateOutdoorAmbient(level, pos);
                envTemp = Mth.lerp(0.35f, envTemp, outdoorAmbient);
            }

            // Влияние воды (если внутри комнаты есть бассейн)
            if (inWater) {
                envTemp += getWaterEffect(envTemp);
            }

            // Локальный лучистый жар в упор к источникам внутри комнаты
            // (воздух помещения частично поглощает излучение — множитель 0.65)
            return applyRadiant(level, pos, envTemp, 1.0f, 0.65f, precomputedSources);
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
        if (inWater) {
            envTemp += getWaterEffect(envTemp);
        }

        // 7. Лучистое тепло от источников (костры, печи, лава, приборы мода)
        return applyRadiant(level, pos, envTemp, shelterLevel, 1.0f, precomputedSources);
    }

    /**
     * Применяет лучистое тепло к базовой температуре с учётом «комфортных потолков»
     * каждого источника (костёр греет до +26, лава — до +60, лёд охлаждает до +8).
     */
    private static float applyRadiant(Level level, BlockPos pos, float envTemp, float shelterLevel,
                                      float scale, @Nullable List<HeatSourceHelper.HeatSource> precomputedSources) {
        List<HeatSourceHelper.HeatSource> sources = precomputedSources != null
                ? precomputedSources
                : HeatSourceHelper.findNearbyHeatSources(level, pos);
        if (sources.isEmpty()) {
            return envTemp;
        }
        return HeatSourceHelper.applyRadiantHeat(level, pos, envTemp, shelterLevel, scale, sources);
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
    private static float getBiomeTemperature(Level level, BlockPos pos) {
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
    private static float getWeatherEffect(Level level, BlockPos pos) {
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
