package com.trd.climate.calculation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Помощник для расчета уровня укрытия игрока от погоды и ветра.
 * Проверяет пещеры и навесы по 8 направлениям (роза ветров).
 */
public class ShelterHelper {

    // 8 направлений розы ветров: N, NE, E, SE, S, SW, W, NW
    private static final int[][] WIND_DIRECTIONS = {
        {0, -1},   // N (North)
        {1, -1},   // NE (North-East)
        {1, 0},    // E (East)
        {1, 1},    // SE (South-East)
        {0, 1},    // S (South)
        {-1, 1},   // SW (South-West)
        {-1, 0},   // W (West)
        {-1, -1}   // NW (North-West)
    };

    private static final int SCAN_RADIUS = 5; // Радиус сканирования для навесов
    private static final int CAVE_DEPTH_THRESHOLD = 8; // Минимальная глубина для пещеры

    /**
     * Рассчитывает уровень укрытия игрока (0.0 - 1.0).
     * 0.0 - полностью на открытом воздухе
     * 1.0 - полное укрытие
     */
    public static float calculateShelterLevel(ServerLevel level, BlockPos playerPos) {
        // 1. Проверка прямого дождя/снега
        if (level.isRainingAt(playerPos)) {
            return 0.0f; // Прямые осадки - нет укрытия
        }

        // 2. Проверка пещеры (толща породы сверху)
        if (isInCave(level, playerPos)) {
            return 1.0f; // В пещере - полное укрытие
        }

        // 3. Проверка навеса (роза ветров)
        return calculateShelterFromWalls(level, playerPos);
    }

    /**
     * Проверяет, находится ли игрок в пещере.
     * Пещера определяется как достаточная толща породы над головой.
     */
    private static boolean isInCave(ServerLevel level, BlockPos pos) {
        // Проверяем, видит ли игрок небо
        if (level.canSeeSky(pos)) {
            return false; // Видит небо - не в пещере
        }

        // Проверяем глубину (расстояние до поверхности)
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        int depth = surfaceY - pos.getY();

        // Если толща породы больше порога - это пещера
        return depth > CAVE_DEPTH_THRESHOLD;
    }

    /**
     * Рассчитывает уровень укрытия от стен по 8 направлениям (роза ветров).
     * Возвращает долю заблокированных направлений (0.0 - 1.0).
     */
    private static float calculateShelterFromWalls(ServerLevel level, BlockPos playerPos) {
        int blockedDirections = 0;
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();

        // Проверяем каждое из 8 направлений
        for (int[] direction : WIND_DIRECTIONS) {
            if (isDirectionBlocked(level, playerPos, direction, checkPos)) {
                blockedDirections++;
            }
        }

        // Возвращаем долю защищенных направлений
        return blockedDirections / 8.0f;
    }

    /**
     * Проверяет, заблокировано ли направление стенами/блоками.
     */
    private static boolean isDirectionBlocked(ServerLevel level, BlockPos startPos, int[] direction, BlockPos.MutableBlockPos checkPos) {
        int eyeLevel = startPos.getY() + 1; // Уровень глаз игрока

        // Сканируем в направлении на расстояние SCAN_RADIUS блоков
        for (int distance = 1; distance <= SCAN_RADIUS; distance++) {
            checkPos.set(
                startPos.getX() + direction[0] * distance,
                eyeLevel,
                startPos.getZ() + direction[1] * distance
            );

            // Если встретили твердый блок - направление заблокировано
            if (level.getBlockState(checkPos).isSolidRender(level, checkPos)) {
                return true;
            }
        }

        return false; // Направление открыто
    }

    /**
     * Применяет влияние укрытия на температуру.
     * Укрытие защищает от экстремальных погодных условий.
     * 
     * @param baseTemp Базовая температура среды
     * @param shelterLevel Уровень укрытия (0.0 - 1.0)
     * @param weatherEffect Эффект погоды (может быть отрицательным)
     * @return Скорректированная температура
     */
    public static float applyShelterModifier(float baseTemp, float shelterLevel, float weatherEffect) {
        // Укрытие смягчает негативные погодные эффекты
        if (weatherEffect < 0) {
            // Чем выше уровень укрытия, тем меньше влияние холодной погоды
            float shelterProtection = weatherEffect * (1.0f - shelterLevel);
            return baseTemp - Math.abs(weatherEffect) + Math.abs(shelterProtection);
        }
        
        return baseTemp;
    }
}
