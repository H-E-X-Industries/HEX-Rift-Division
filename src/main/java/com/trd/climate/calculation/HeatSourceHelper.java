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

    /**
     * Кривая спада излучения с расстоянием.
     * <p>
     * Форма: {@code f(t) = (1 − t)^p · (1 + p·t)}, где {@code t = d / R} —
     * доля пройденного радиуса, а {@code p} — показатель формы, который
     * задаётся теплопроводностью источника ({@link HeatSourceDef#falloffExponent()}).
     * <p>
     * Почему так:
     * <ul>
     *   <li>у края радиуса (t → 1) значение <b>и наклон</b> кривой стремятся
     *       к нулю — влияние источника мягко уходит в ноль, без «обрыва»,
     *       из-за которого температура падала на сотни градусов за пару блоков;</li>
     *   <li>при {@code p = 1} спад почти линейный (хороший проводник: лава
     *       разносит тепло далеко и равномерно);</li>
     *   <li>при {@code p = 3} тепло держится у самой поверхности (плохой
     *       проводник: нагреватель из доломитовых кирпичей греет вплотную).</li>
     * </ul>
     */
    public static float falloffFactor(float normalizedDistance, float exponent) {
        if (normalizedDistance >= 1.0f) {
            return 0.0f;
        }
        float t = Math.max(0.0f, normalizedDistance);
        float u = 1.0f - t;
        return (float) (Math.pow(u, exponent) * (1.0f + exponent * t));
    }

    /**
     * Коэффициент убывающей отдачи для перекрывающихся источников:
     * ближний источник учитывается полностью, следующий — 35% от него, и так далее.
     */
    public static final float OVERLAP_DECAY = 0.35f;

    /** Доля тепла, дошедшего на открытом воздухе — остальное сдувает ветер. */
    private static final float OUTDOOR_EFFICIENCY = 0.45f;

    /**
     * Мощность «воздушной кожи» источника — доля от основного источника.
     * <p>
     * Блоки воздуха, примыкающие к источнику вплотную, сами становятся
     * фантомными источниками тепла с такой мощностью: они заполняют
     * «слепые зоны» (карманы у склонов, углы), куда луч от самого блока
     * источника не проходит. Это чисто расчётные сущности: в мире таких
     * блоков нет, их нельзя сломать, добыть или измерить.
     * <p>
     * Воздушные источники не цепляются друг за друга: воздух рядом с
     * воздушным источником источником не становится.
     */
    public static final float AIR_HALO_POWER = 0.5f;

    /**
     * Насколько сильно сплошное вещество гасит излучение, на 1 блок пути.
     * <p>
     * Больше — стена пропускает меньше тепла; меньше — тепло «просачивается»
     * сквозь камни сильнее.
     */
    private static final double OCCLUSION_ATTENUATION = 4.0;

    /** Максимум воздушных фантомов у одного источника (для отладки). */
    private static final int MAX_AIR_HALO_CELLS = 64;

    /** Прирост доли тепла при полном укрытии. */
    private static final float SHELTER_EFFICIENCY = 0.55f;

    /**
     * Найденный источник тепла.
     * <p>
     * Для мультиблоков источник — это <b>вся конструкция</b> разом: {@link #cells()}
     * содержит контроллер и все блоки-парты, а расстояние считается до ближайшей
     * части. Поэтому станок греет окружение как единый источник (а не как десяток
     * отдельных), и касание любой его грани ощущается одинаково.
     *
     * @param pos       позиция блока-источника (для мультиблока — контроллер)
     * @param def       описание источника из реестра
     * @param power     мощность источника (0..1) с учётом его текущей температуры
     * @param sourceTemp текущая температура источника, °C
     * @param cells     блоки, из которых состоит источник
     */
    public record HeatSource(BlockPos pos, HeatSourceDef def, float power, float sourceTemp,
                             List<BlockPos> cells) {

        public HeatSource {
            cells = (cells == null || cells.isEmpty()) ? List.of(pos) : List.copyOf(cells);
        }

        /** Обычный одиночный блок-источник. */
        public HeatSource(BlockPos pos, HeatSourceDef def, float power, float sourceTemp) {
            this(pos, def, power, sourceTemp, List.of(pos));
        }

        /**
         * Выход источника у поверхности, °C.
         * <p>
         * Для нагревателя — доля его собственной температуры (по теплопроводности),
         * ограниченная потолком {@link HeatSourceDef#maxHeat()}. Мощность здесь
         * не учитывается отдельно: у приборов мода текущая температура уже
         * отражает мощность (холодный нагреватель и так отдаёт мало тепла).
         */
        public float effectiveHeat() {
            return def.radiantOutput(sourceTemp);
        }

        /**
         * Радиус излучения источника, блоков.
         * Зависит от текущей мощности (слабый прибор греет только вплотную)
         * и от тепловыхода: чем горячее источник, тем дальше чувствуется
         * его излучение ({@link HeatSourceDef#heatRadiusScale(float)}).
         */
        public float radius() {
            return def.radiusAt(power)
                    * def.heatRadiusScale(effectiveHeat())
                    * def.spreadByConductivity();
        }

        /**
         * Ближайший блок источника к точке — от него считается расстояние
         * и строится луч излучения.
         */
        public BlockPos nearestCell(Vec3 point) {
            BlockPos best = pos;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos cell : cells) {
                double dx = cell.getX() + 0.5 - point.x;
                double dy = cell.getY() + 0.5 - point.y;
                double dz = cell.getZ() + 0.5 - point.z;
                double d = dx * dx + dy * dy + dz * dz;
                if (d < bestDist) {
                    bestDist = d;
                    best = cell;
                }
            }
            return best;
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
    /** Максимум блоков в одном кластере (лавовое озеро, ледник). */
    private static final int MAX_CLUSTER_CELLS = 96;

    public static List<HeatSource> findNearbyHeatSources(Level level, BlockPos center) {
        List<HeatSource> sources = new ArrayList<>();
        // Мультиблок находится сразу по нескольким блокам (контроллер + парты) —
        // оставляем только один источник на конструкцию
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        // Блоки, уже вошедшие в кластер (лавовое озеро): повторно не разрешаем
        java.util.Set<BlockPos> claimed = new java.util.HashSet<>();

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
                    if (claimed.contains(cursor)) {
                        continue; // блок уже вошёл в кластер соседнего источника
                    }
                    HeatSource source = resolveAt(level, cursor);
                    if (source == null || !seen.add(source.pos())) {
                        continue;
                    }
                    // Простые ванильные источники (лава, лёд, магма) часто лежат
                    // огромными скоплениями: сотни одинаковых блоков = сотни
                    // источников, и для каждого строится луч перекрытия. Склеиваем
                    // соседние блоки с тем же описанием в ОДИН источник-кластер:
                    // озеро греет как единое тело (расстояние — до ближайшей
                    // кромки), а расчёт дешевеет в десятки раз.
                    if (source.cells().size() == 1
                            && HeatSourceRegistry.resolveState(level.getBlockState(cursor)) == source.def()) {
                        source = expandCluster(level, source, claimed, center, rSq);
                    }
                    sources.add(source);
                }
            }
        }
        return sources;
    }

    /**
     * Склеивает готовый список источников в кластеры.
     * <p>
     * Вариант для кода, который собрал источники сам (по одному на блок —
     * например, отладочный сканер чанков) и хочет ту же экономию, что даёт
     * кластеризация в {@link #findNearbyHeatSources}: смежные одиночные
     * источники с одинаковым описанием (лава, лёд, магма) объединяются в
     * один источник со множеством блоков. Мультиблоки и приборы мода
     * (источник по блок-сущности) не трогаем — у каждого прибора своя
     * температура и мощность.
     */
    public static List<HeatSource> clusterSources(Level level, List<HeatSource> sources) {
        List<HeatSource> result = new ArrayList<>();
        java.util.Map<BlockPos, HeatSource> singles = new java.util.HashMap<>();
        for (HeatSource s : sources) {
            if (s.cells().size() > 1
                    || HeatSourceRegistry.resolveState(level.getBlockState(s.pos())) != s.def()) {
                result.add(s);
            } else {
                singles.put(s.pos(), s);
            }
        }
        if (singles.isEmpty()) {
            return result;
        }

        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (HeatSource seed : singles.values()) {
            if (!visited.add(seed.pos())) {
                continue;
            }
            List<BlockPos> cells = new ArrayList<>();
            java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
            cells.add(seed.pos());
            queue.add(seed.pos());
            while (!queue.isEmpty() && cells.size() < MAX_CLUSTER_CELLS) {
                BlockPos cur = queue.poll();
                for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                    cursor.set(cur.getX() + dir.getStepX(),
                            cur.getY() + dir.getStepY(),
                            cur.getZ() + dir.getStepZ());
                    HeatSource neighbour = singles.get(cursor);
                    if (neighbour == null || neighbour.def() != seed.def()) {
                        continue;
                    }
                    if (visited.add(neighbour.pos())) {
                        cells.add(neighbour.pos());
                        queue.add(neighbour.pos());
                    }
                }
            }
            // Якорь кластера — детерминированная минимальная позиция:
            // произвольный seed менял якорь каждый тик, подпись fieldSources
            // считалась изменённой, и поле пересобиралось раньше, чем успевало
            // посчитаться (симптом «лёд вешает рендер»)
            BlockPos anchor = cells.stream()
                    .min(java.util.Comparator.comparingLong(BlockPos::asLong))
                    .orElse(seed.pos());
            result.add(new HeatSource(anchor, seed.def(), seed.power(), seed.sourceTemp(), cells));
        }
        return result;
    }

    /**
     * Склеивает смежные блоки с тем же описанием источника в один кластер.
     * <p>
     * Обход в ширину по шести соседям: лавовое озеро или ледяная пещера
     * становятся одним источником со множеством блоков (как мультиблок),
     * поэтому внутренние блоки скопления вообще не участвуют в расчёте.
     * Размер кластера ограничен ({@value #MAX_CLUSTER_CELLS}), чтобы поиск
     * ближайшего блока оставался дешёвым.
     */
    private static HeatSource expandCluster(Level level, HeatSource first,
                                            java.util.Set<BlockPos> claimed,
                                            BlockPos center, int rSq) {
        HeatSourceDef def = first.def();
        List<BlockPos> cells = new ArrayList<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        BlockPos start = first.pos();
        claimed.add(start);
        cells.add(start);
        queue.add(start);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && cells.size() < MAX_CLUSTER_CELLS) {
            BlockPos cur = queue.poll();
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                cursor.set(cur.getX() + dir.getStepX(),
                        cur.getY() + dir.getStepY(),
                        cur.getZ() + dir.getStepZ());
                long dx = cursor.getX() - center.getX();
                long dy = cursor.getY() - center.getY();
                long dz = cursor.getZ() - center.getZ();
                if (dx * dx + dy * dy + dz * dz > rSq) {
                    continue; // не выползаем за пределы области сканирования
                }
                if (claimed.contains(cursor)) {
                    continue;
                }
                if (HeatSourceRegistry.resolveState(level.getBlockState(cursor)) != def) {
                    continue;
                }
                BlockPos immutable = cursor.immutable();
                claimed.add(immutable);
                cells.add(immutable);
                queue.add(immutable);
            }
        }
        return new HeatSource(start, def, first.power(), first.sourceTemp(), cells);
    }

    /**
     * Разрешает источник тепла в конкретном блоке.
     * <p>
     * Блок-парт мультиблока разрешается в источник его контроллера:
     * вся конструкция считается одним источником тепла.
     *
     * @return источник или {@code null}, если блок не является активным источником
     */
    public static HeatSource resolveAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        // 1) Приборы мода и блоки-парты мультиблоков: активность и температура
        //    берутся у самого прибора (у мультиблока — у контроллера)
        //    Блок-сущность читаем только там, где она точно нужна: приборы мода
        //    и части мультиблоков. Иначе на каждый блок в области сканирования
        //    пришлось бы лезть в чанк за сущностью.
        boolean mayHaveSource = HeatSourceRegistry.usesBlockEntity(state.getBlock())
                || state.getBlock() instanceof com.trd.multiblock.system.MultiblockPartBlock;
        if (mayHaveSource) {
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof IHeatSource heatSource) {
                HeatSourceDef def = HeatSourceRegistry.byId(heatSource.getHeatSourceId());
                if (def != null) {
                    float power = heatSource.isHeatActive()
                            ? def.powerAt(heatSource.getSourceTemperature())
                            : 0.0f;
                    if (power > 0.0f) {
                        // Для контроллера мультиблока источником является вся конструкция
                        List<BlockPos> cells = MultiblockHeatHelper.structureCells(level, pos);
                        return new HeatSource(pos.immutable(), def, power,
                                heatSource.getSourceTemperature(), cells);
                    }
                }
                return null;
            }

            if (be instanceof com.trd.multiblock.system.roles.IMultiblockPart part
                    && part.getControllerPos() != null) {
                return resolveMultiblockSource(level, part.getControllerPos());
            }
        }

        // 2) Контроллер мультиблока (сам блок, а не его блок-сущность)
        BlockPos controllerPos = MultiblockHeatHelper.resolveControllerPos(level, pos);
        if (controllerPos != null && !controllerPos.equals(pos)) {
            return resolveMultiblockSource(level, controllerPos);
        }

        // 3) Ванильные источники: активность задана состоянием блока
        HeatSourceDef def = HeatSourceRegistry.resolveState(state);
        if (def != null) {
            float power = def.powerAt(1.0f);
            if (power > 0.0f) {
                return new HeatSource(pos.immutable(), def, power, def.sourceTemp());
            }
        }
        return null;
    }

    /**
     * Источник тепла мультиблока: контроллер + все его части как единый источник.
     */
    private static HeatSource resolveMultiblockSource(Level level, BlockPos controllerPos) {
        if (level.getBlockEntity(controllerPos) instanceof IHeatSource heatSource) {
            HeatSourceDef def = HeatSourceRegistry.byId(heatSource.getHeatSourceId());
            if (def != null) {
                float power = heatSource.isHeatActive()
                        ? def.powerAt(heatSource.getSourceTemperature())
                        : 0.0f;
                if (power > 0.0f) {
                    List<BlockPos> cells = MultiblockHeatHelper.structureCells(level, controllerPos);
                    return new HeatSource(controllerPos.immutable(), def, power,
                            heatSource.getSourceTemperature(), cells);
                }
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
     * <p>
     * Учитываются две составляющие:
     * <ol>
     *   <li><b>Прямое излучение</b> от блока источника — ослабляется тем,
     *       сколько сплошного вещества луч прошёл насквозь
     *       ({@link #occlusionLength}): вместо «перекрыт / не перекрыт» получается
     *       плавное затухание, без ступенек на склонах;</li>
     *   <li><b>Воздушная кожа</b> источника — блоки воздуха, примыкающие
     *       к источнику вплотную, сами становятся фантомным источником тепла
     *       вдвое меньшей мощности ({@value #AIR_HALO_POWER}). Они заполняют
     *       «слепые зоны» (карманы у склонов, углы), куда луч от самого
     *       источника не проходит. Воздушные источники не цепляются друг за
     *       друга: воздух рядом с воздухом источником не становится.</li>
     * </ol>
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
        double radius = source.radius();
        if (radius <= 0.0) {
            return 0.0f;
        }

        Vec3 from = sourcePoint(source.nearestCell(targetPoint));
        double distance = from.distanceTo(targetPoint);
        if (distance > radius) {
            return 0.0f;
        }

        float factor = (float) (distance / radius);
        float falloff = falloffFactor(factor, def.falloffExponent());

        // Прямое излучение, ослабленное сплошными блоками на пути
        double blocked = occlusionLength(level, source.cells(), from, targetPoint, targetPos);
        float heat = effective * falloff * attenuation(blocked);

        // Воздушная кожа: если прямой луч что-то перекрыл, тепло может прийти
        // через примыкающий к источнику воздух (половинной мощности)
        if (blocked > 0.001) {
            float halo = airHaloHeat(level, source, targetPos, targetPoint, effective, radius);
            heat = Math.max(heat, halo);
        }

        // На открытом воздухе ветер сдувает большую часть тепла,
        // в укрытии (нора, пещера, комната) тепло задерживается полностью
        heat *= OUTDOOR_EFFICIENCY + SHELTER_EFFICIENCY * Mth.clamp(shelterLevel, 0.0f, 1.0f);

        return heat;
    }

    /**
     * Ослабление излучения по длине сплошного вещества на пути луча.
     * <p>
     * Сплошной блок на пути не обрывает тепло совсем, а гасит его
     * пропорционально тому, сколько вещества луч пересёк: так рядом с
     * источником нет ступенек «жарко — сразу холодно».
     *
     * @param blockedLength длина участка луча внутри сплошных блоков, блоков
     */
    private static float attenuation(double blockedLength) {
        if (blockedLength <= 0.0) {
            return 1.0f;
        }
        return (float) Math.exp(-OCCLUSION_ATTENUATION * blockedLength);
    }

    /**
     * Тепло от «воздушной кожи» источника — фантомных источников половинной
     * мощности в блоках воздуха, примыкающих к источнику вплотную.
     * <p>
     * Берётся лучший из кандидатов (соседей ближайшего блока источника по
     * грани). Кандидаты — только настоящий воздух, и только вплотную к
     * источнику: воздушные источники не распространяются дальше на
     * соседний воздух (никакой цепной реакции).
     */
    private static float airHaloHeat(Level level, HeatSource source, BlockPos targetPos,
                                      Vec3 targetPoint, float effective, double radius) {
        BlockPos nearest = source.nearestCell(targetPoint);
        float best = 0.0f;
        float exponent = source.def().falloffExponent();

        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            BlockPos air = nearest.relative(dir);
            BlockState state = level.getBlockState(air);
            if (!state.isAir()) {
                continue; // не воздух — воздушным источником не станет
            }
            Vec3 airPoint = sourcePoint(air);
            double distance = airPoint.distanceTo(targetPoint);
            if (distance > radius) {
                continue;
            }
            float falloff = falloffFactor((float) (distance / radius), exponent);
            double blocked = occlusionLength(level, source.cells(), airPoint, targetPoint, targetPos);
            float halo = effective * AIR_HALO_POWER * falloff * attenuation(blocked);
            best = Math.max(best, halo);
        }
        return best;
    }

    /**
     * Блоки воздуха, примыкающие к источнику вплотную — «воздушная кожа».
     * <p>
     * Нужна для отладки: показать, какие именно воздушные фантомы-источники
     * участвуют в расчёте. В мире они не существуют — их нельзя сломать,
     * добыть или измерить, это чисто расчётные сущности.
     */
    public static List<BlockPos> airHaloCells(Level level, HeatSource source) {
        List<BlockPos> cells = new ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (BlockPos cell : source.cells()) {
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                cursor.set(cell.getX() + dir.getStepX(),
                        cell.getY() + dir.getStepY(),
                        cell.getZ() + dir.getStepZ());
                if (!seen.add(cursor.immutable())) {
                    continue;
                }
                if (!level.getBlockState(cursor).isAir()) {
                    continue;
                }
                cells.add(cursor.immutable());
                if (cells.size() >= MAX_AIR_HALO_CELLS) {
                    return cells;
                }
            }
        }
        return cells;
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
     * Жёстких потолков на температуру среды нет, но есть два ограничителя,
     * не дающих нескольким источникам сложить тепло в космос:
     * <ol>
     *   <li><b>Убывающая отдача</b>: вклад перекрывающихся источников суммируется
     *       с убывающим коэффициентом — ближний источник считается полностью,
     *       каждый следующий даёт всё меньше ({@value #OVERLAP_DECAY} от предыдущего).</li>
     *   <li><b>Потолок температуры источника</b>: точка не может нагреться выше
     *       температуры самого горячего источника ({@link HeatSourceDef#sourceTemp()}).
     *       Лавовое озеро из сотен блоков греет соседей как один мощный источник,
     *       а не как сумма сотен.</li>
     * </ol>
     * Охладители работают симметрично: точка не опускается ниже температуры
     * самого холодного источника.
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

        List<Float> heats = new ArrayList<>();
        List<Float> cools = new ArrayList<>();
        float hottest = Float.NEGATIVE_INFINITY;
        float coldest = Float.POSITIVE_INFINITY;

        for (HeatSource source : sources) {
            float contribution = heatFromSource(level, source, pos, probe, shelterLevel) * scale;
            if (contribution <= 0.0f) {
                continue;
            }
            if (source.def().cooling()) {
                cools.add(contribution);
                coldest = Math.min(coldest, source.sourceTemp());
            } else {
                heats.add(contribution);
                hottest = Math.max(hottest, source.sourceTemp());
            }
        }

        // Нагреватели: убывающая отдача для перекрывающихся источников
        result += diminishingSum(heats);
        // Охладители: симметрично
        result -= diminishingSum(cools);

        // Потолок/пол температуры источника: точка не может быть горячее
        // самого горячего источника и холоднее самого холодного
        if (hottest > Float.NEGATIVE_INFINITY) {
            result = Math.min(result, Math.max(baseTemp, hottest));
        }
        if (coldest < Float.POSITIVE_INFINITY) {
            result = Math.max(result, Math.min(baseTemp, coldest));
        }
        return result;
    }

    /**
     * Сумма вкладов с убывающей отдачей: самый сильный источник учитывается
     * полностью, каждый следующий — с коэффициентом {@value #OVERLAP_DECAY}
     * от предыдущего (0.35, 0.12, 0.04 ...).
     * <p>
     * Именно это не даёт лавовому озеру сложить сотни источников в 6000 °C.
     */
    private static float diminishingSum(List<Float> contributions) {
        if (contributions.isEmpty()) {
            return 0.0f;
        }
        contributions.sort((a, b) -> Float.compare(b, a));
        float sum = 0.0f;
        float factor = 1.0f;
        for (float c : contributions) {
            sum += c * factor;
            factor *= OVERLAP_DECAY;
        }
        return sum;
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
        return isOccluded(level, List.of(sourcePos), from, to, targetPos);
    }

    /**
     * Проверяет, перекрыт ли путь от источника к цели сплошными блоками.
     * <p>
     * Вариант для источников из нескольких блоков (мультиблок): собственные блоки
     * источника не считаются препятствием.
     *
     * @param sourceCells блоки, из которых состоит источник (исключаются из проверки)
     */
    public static boolean isOccluded(Level level, List<BlockPos> sourceCells, Vec3 from, Vec3 to, BlockPos targetPos) {
        return occlusionLength(level, sourceCells, from, to, targetPos) > 0.01;
    }

    /**
     * Длина участка луча, прошедшего сквозь сплошные блоки, блоков.
     * <p>
     * Считается не «есть ли препятствие», а «сколько вещества луч пересёк»:
     * угловой graze (луч едва задевает ребро блока) даёт почти нулевую длину,
     * а стена даёт целый блок. Это и убирает ступеньки в поле температуры:
     * раньше любой задетый угол обрывал тепло полностью.
     * <p>
     * Собственный обход воксела (DDA) вместо {@code Level#clip}: у {@code clip()}
     * нет возможности исключить крайние блоки, поэтому луч, начинающийся или
     * заканчивающийся внутри блока, всегда «упирается» в этот блок.
     * <p>
     * Блоком-препятствием считается сплошной блок ({@code isSolidRender}): стекло,
     * листва, плиты и факелы тепло пропускают.
     *
     * @param sourceCells блоки, из которых состоит источник (исключаются из проверки)
     */
    public static double occlusionLength(Level level, List<BlockPos> sourceCells, Vec3 from, Vec3 to, BlockPos targetPos) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        double rayLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (rayLength < 1.0e-6) {
            return 0.0;
        }

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

        final int tgtX = targetPos.getX();
        final int tgtY = targetPos.getY();
        final int tgtZ = targetPos.getZ();

        // Блоки источника (например, части мультиблока) препятствием не считаются.
        // Для одиночного блока сравнение дешевле, поэтому множество создаётся
        // только когда источник действительно состоит из нескольких блоков.
        final boolean multiCell = sourceCells.size() > 1;
        final java.util.Set<BlockPos> sourceSet = multiCell
                ? new java.util.HashSet<>(sourceCells)
                : java.util.Collections.emptySet();
        final int srcX = sourceCells.isEmpty() ? Integer.MIN_VALUE : sourceCells.get(0).getX();
        final int srcY = sourceCells.isEmpty() ? Integer.MIN_VALUE : sourceCells.get(0).getY();
        final int srcZ = sourceCells.isEmpty() ? Integer.MIN_VALUE : sourceCells.get(0).getZ();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        // Страховка от зацикливания: DDA проходит не больше |dx|+|dy|+|dz| вокселов
        int guard = (int) (Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) * 3 + 16;

        double blocked = 0.0;
        double tPrev = 0.0;

        while (guard-- > 0) {
            if (x == endX && y == endY && z == endZ) {
                break;   // дошли до цели
            }

            // Параметр t, при котором луч покидает текущий воксел
            double tCur = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));

            // Излучатель и цель преградой не считаются
            boolean isSource = multiCell
                    ? sourceSet.contains(new BlockPos(x, y, z))
                    : (x == srcX && y == srcY && z == srcZ);
            boolean isTarget = (x == tgtX && y == tgtY && z == tgtZ);
            if (!isSource && !isTarget) {
                cursor.set(x, y, z);
                BlockState state = level.getBlockState(cursor);
                if (state.isSolidRender(level, cursor)) {
                    // Луч внутри этого воксела шёл от tPrev до tCur
                    blocked += (tCur - tPrev) * rayLength;
                }
            }
            tPrev = tCur;

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
        return blocked;
    }
}
