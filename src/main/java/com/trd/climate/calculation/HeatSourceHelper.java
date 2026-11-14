package com.trd.climate.calculation;

import com.trd.api.climate.HeatSourceDef;
import com.trd.api.climate.HeatSourceRegistry;
import com.trd.api.climate.IHeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Единый расчёт лучистого (инфракрасного) тепла.
 * <p>
 * Класс работает и на сервере, и на клиенте ({@link Level}, а не {@code ServerLevel}),
 * поэтому клиентский дебаг-оверлей и серверные расчёты (игрок, термометры, сканер)
 * используют <b>одну и ту же</b> математику и одну и ту же таблицу источников
 * ({@link HeatSourceRegistry}).
 * <p>
 * Важное отличие от старой реализации: видимость тепла проверяется <b>от источника
 * к точке</b>, а не «от глаз наблюдателя к источнику». Старый вариант ломался всякий раз,
 * когда луч начинался внутри блока (например настенный термометр: луч стартовал в стене
 * над ним, и <b>всё</b> тепло отбрасывалось как «перекрытое стеной»).
 */
public final class HeatSourceHelper {

    /** Экспонента спада интенсивности излучения с расстоянием. */
    public static final float FALLOFF_EXPONENT = 1.3f;

    /** Доля тепла, дошедшего на открытом воздухе — остальное сдувает ветер. */
    private static final float OUTDOOR_EFFICIENCY = 0.45f;

    /** Прирост доли тепла при полном укрытии. */
    private static final float SHELTER_EFFICIENCY = 0.55f;

    /**
     * Найденный источник тепла.
     *
     * @param pos   позиция блока-источника
     * @param def   описание источника из реестра
     * @param power мощность источника (0..1) с учётом его текущей температуры
     */
    public record HeatSource(BlockPos pos, HeatSourceDef def, float power) {

        /** Эффективная мощность излучения источника, °C. */
        public float effectiveHeat() {
            return def.maxHeat() * power;
        }

        /** Радиус излучения источника, блоков. */
        public float radius() {
            return def.radius();
        }
    }

    private HeatSourceHelper() {
    }

    // ═══════════════════════════════════════════════════════════
    // ПОИСК ИСТОЧНИКОВ
    // ═══════════════════════════════════════════════════════════

    /**
     * Находит все активные источники тепла в окрестностях точки.
     * <p>
     * Сканируется куб со стороной 2·R+1, где R — максимальный радиус излучения
     * среди зарегистрированных источников.
     */
    public static List<HeatSource> findNearbyHeatSources(Level level, BlockPos center) {
        List<HeatSource> sources = new ArrayList<>();

        float maxRadius = HeatSourceRegistry.maxRadius();
        if (maxRadius <= 0.0f) {
            return sources;
        }

        int r = (int) Math.ceil(maxRadius);
        int rSq = r * r;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    // Сканируем шар, а не куб: экономит до 60% проверок блоков
                    if (dx * dx + dy * dy + dz * dz > rSq) {
                        continue;
                    }
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    HeatSource source = resolveAt(level, cursor);
                    if (source != null) {
                        sources.add(source);
                    }
                }
            }
        }
        return sources;
    }

    /**
     * Разрешает источник тепла в конкретном блоке.
     *
     * @return источник или {@code null}, если блок не является активным источником
     */
    public static HeatSource resolveAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        // 1) Приборы мода: активность и температура берутся у самого прибора
        if (HeatSourceRegistry.usesBlockEntity(state.getBlock())
                && level.getBlockEntity(pos) instanceof IHeatSource heatSource) {
            HeatSourceDef def = HeatSourceRegistry.byId(heatSource.getHeatSourceId());
            if (def != null) {
                float power = heatSource.isHeatActive()
                        ? def.powerAt(heatSource.getSourceTemperature())
                        : 0.0f;
                if (power > 0.0f) {
                    return new HeatSource(pos.immutable(), def, power);
                }
            }
        }

        // 2) Ванильные источники: активность задана состоянием блока
        HeatSourceDef def = HeatSourceRegistry.resolveState(state);
        if (def != null) {
            float power = def.powerAt(1.0f);
            if (power > 0.0f) {
                return new HeatSource(pos.immutable(), def, power);
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    // РАСЧЁТ ТЕПЛА
    // ═══════════════════════════════════════════════════════════

    /**
     * Точка измерения температуры в блоке — центр блока.
     * <p>
     * Используется и для игрока, и для приборов, поэтому игрок и термометр,
     * стоящие рядом, показывают одну и ту же температуру.
     */
    public static Vec3 probePoint(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /**
     * Луч от источника к точке: от блока-источника к центру целевого блока.
     */
    private static Vec3 sourcePoint(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /**
     * Тепло от одного источника в конкретной точке, °C.
     *
     * @param level        мир
     * @param source       источник
     * @param targetPos    блок цели
     * @param targetPoint  точка измерения в целевом блоке
     * @param shelterLevel уровень укрытия (0 — открытое небо, 1 — полное укрытие)
     */
    public static float heatFromSource(Level level, HeatSource source, BlockPos targetPos,
                                       Vec3 targetPoint, float shelterLevel) {
        float effective = source.effectiveHeat();
        if (effective <= 0.0f) {
            return 0.0f;
        }

        HeatSourceDef def = source.def();
        double radius = def.radius();
        if (radius <= 0.0) {
            return 0.0f;
        }

        Vec3 from = sourcePoint(source.pos());
        double distance = from.distanceTo(targetPoint);
        if (distance > radius) {
            return 0.0f;
        }

        // Сплошные блоки между источником и целью гасят тепло
        if (isOccluded(level, source.pos(), from, targetPoint, targetPos)) {
            return 0.0f;
        }

        float factor = (float) (1.0 - (distance / radius));
        float falloff = (float) Math.pow(Math.max(0.0f, factor), FALLOFF_EXPONENT);

        float heat = effective * falloff;

        // На открытом воздухе ветер сдувает большую часть тепла,
        // в укрытии (нора, пещера, комната) тепло задерживается полностью
        heat *= OUTDOOR_EFFICIENCY + SHELTER_EFFICIENCY * Mth.clamp(shelterLevel, 0.0f, 1.0f);

        return heat;
    }

    /**
     * Суммарное лучистое тепло в точке, °C (прибавка к базовой температуре среды).
     */
    public static float calculateTotalHeat(Level level, BlockPos pos, float shelterLevel) {
        List<HeatSource> sources = findNearbyHeatSources(level, pos);
        if (sources.isEmpty()) {
            return 0.0f;
        }
        Vec3 probe = probePoint(pos);
        float total = 0.0f;
        for (HeatSource source : sources) {
            total += heatFromSource(level, source, pos, probe, shelterLevel);
        }
        return total;
    }

    /**
     * Применяет лучистое тепло источников к температуре среды.
     * <p>
     * Каждый источник ограничен собственным «комфортным потолком»
     * ({@link HeatSourceDef#comfortCap()}): у костра это 26 °C, у доменной печи 40 °C,
     * у лавы 60 °C, у льда — нижняя граница (8 / 0 / −10 °C).
     * <p>
     * Источник может двигать температуру <b>только в сторону своего потолка</b>:
     * костёр не выстудит жаркую пустыню, а лёд не нагреет холодную тундру.
     *
     * @param scale множитель вклада (например 0.65 для помещения, где воздух
     *              частично поглощает излучение)
     */
    public static float applyRadiantHeat(Level level, BlockPos pos, float baseTemp,
                                         float shelterLevel, float scale) {
        List<HeatSource> sources = findNearbyHeatSources(level, pos);
        return applyRadiantHeat(level, pos, baseTemp, shelterLevel, scale, sources);
    }

    /**
     * Вариант с заранее найденными источниками — для массовых расчётов
     * (например отладочное поле), чтобы не сканировать окрестности для каждого блока.
     */
    public static float applyRadiantHeat(Level level, BlockPos pos, float baseTemp,
                                         float shelterLevel, float scale, List<HeatSource> sources) {
        if (sources.isEmpty()) {
            return baseTemp;
        }

        Vec3 probe = probePoint(pos);
        float result = baseTemp;

        for (HeatSource source : sources) {
            float contribution = heatFromSource(level, source, pos, probe, shelterLevel) * scale;
            if (contribution <= 0.0f) {
                continue;
            }
            HeatSourceDef def = source.def();
            float cap = def.comfortCap();

            if (def.cooling()) {
                // Охладитель: температура не опускается ниже его порога
                float limited = Math.max(cap, result - contribution);
                result = Math.min(result, limited);
            } else {
                // Нагреватель: температура не поднимается выше его потолка
                float limited = Math.min(cap, result + contribution);
                result = Math.max(result, limited);
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    // ПРОВЕРКА ПЕРЕКРЫТИЯ
    // ═══════════════════════════════════════════════════════════

    /**
     * Проверяет, перекрыт ли путь от источника к цели сплошными блоками.
     * <p>
     * Собственный обход воксела (DDA) вместо {@code Level#clip}: у {@code clip()} нет
     * возможности исключить крайние блоки, поэтому луч, начинающийся или заканчивающийся
     * внутри блока, всегда «упирается» в этот блок. Именно из-за этого настенный
     * термометр (луч начинался в стене над ним) вообще не видел тепла от костра,
     * а игрок в проёме высотой в блок — не видел тепла вообще.
     * <p>
     * Блоком-препятствием считается сплошной блок ({@code isSolidRender}): стекло,
     * листва, плиты и факелы тепло пропускают.
     *
     * @param sourcePos блок-излучатель (исключается из проверки)
     * @param from      точка излучения
     * @param to        точка приёма
     * @param targetPos блок цели (исключается из проверки)
     */
    public static boolean isOccluded(Level level, BlockPos sourcePos, Vec3 from, Vec3 to, BlockPos targetPos) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        int x = Mth.floor(from.x);
        int y = Mth.floor(from.y);
        int z = Mth.floor(from.z);

        final int endX = Mth.floor(to.x);
        final int endY = Mth.floor(to.y);
        final int endZ = Mth.floor(to.z);

        final double stepX = Math.signum(dx);
        final double stepY = Math.signum(dy);
        final double stepZ = Math.signum(dz);

        final double tDeltaX = stepX != 0.0 ? Math.abs(1.0 / dx) : Double.MAX_VALUE;
        final double tDeltaY = stepY != 0.0 ? Math.abs(1.0 / dy) : Double.MAX_VALUE;
        final double tDeltaZ = stepZ != 0.0 ? Math.abs(1.0 / dz) : Double.MAX_VALUE;

        double tMaxX = stepX != 0.0 ? (stepX > 0.0 ? (x + 1 - from.x) : (from.x - x)) * tDeltaX : Double.MAX_VALUE;
        double tMaxY = stepY != 0.0 ? (stepY > 0.0 ? (y + 1 - from.y) : (from.y - y)) * tDeltaY : Double.MAX_VALUE;
        double tMaxZ = stepZ != 0.0 ? (stepZ > 0.0 ? (z + 1 - from.z) : (from.z - z)) * tDeltaZ : Double.MAX_VALUE;

        final int srcX = sourcePos.getX();
        final int srcY = sourcePos.getY();
        final int srcZ = sourcePos.getZ();
        final int tgtX = targetPos.getX();
        final int tgtY = targetPos.getY();
        final int tgtZ = targetPos.getZ();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        // Страховка от зацикливания: DDA проходит не больше |dx|+|dy|+|dz| вокселов
        int guard = (int) (Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) * 3 + 16;

        while (guard-- > 0) {
            if (x == endX && y == endY && z == endZ) {
                return false;   // дошли до цели — преград нет
            }

            // Излучатель и цель преградой не считаются
            boolean isSource = (x == srcX && y == srcY && z == srcZ);
            boolean isTarget = (x == tgtX && y == tgtY && z == tgtZ);
            if (!isSource && !isTarget) {
                cursor.set(x, y, z);
                BlockState state = level.getBlockState(cursor);
                if (state.isSolidRender(level, cursor)) {
                    return true;
                }
            }

            // Шаг к следующему вокселу
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += (int) stepX;
                    tMaxX += tDeltaX;
                } else {
                    z += (int) stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    y += (int) stepY;
                    tMaxY += tDeltaY;
                } else {
                    z += (int) stepZ;
                    tMaxZ += tDeltaZ;
                }
            }
        }
        return false;
    }
}
