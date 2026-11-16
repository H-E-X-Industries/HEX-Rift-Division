package com.trd.climate.client;

import com.trd.api.climate.HeatSourceRegistry;
import com.trd.climate.calculation.HeatSourceHelper;
import com.trd.climate.calculation.TemperatureCalculator;
import com.trd.client.config.ModClientConfig;
import com.trd.client.config.ModDebugMode;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.*;

/**
 * Сбор данных для отладочного оверлея тепла.
 * <p>
 * <b>Архитектура:</b> тиковый поток собирает данные в рабочую копию, потом
 * атомарно публикует один неизменяемый снимок для рендера. Никаких
 * {@code ConcurrentHashMap} — только чистые снимки.
 * <p>
 * Работа разбита на шаги, каждый укладывается в бюджет времени:
 * <ol>
 *   <li><b>Источники</b> кэшируются по чанкам.</li>
 *   <li><b>Кандидаты</b> — блоки вокруг источников с открытой гранью.</li>
 *   <li><b>Температуры</b> считаются постепенно, по бюджету тика.</li>
 *   <li><b>Снимок</b> публикуется атомарно в конце тика.</li>
 * </ol>
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class HeatDebugController {

    private static final double TICK_BUDGET_MS = 3.0;
    private static final int REFRESH_INTERVAL_TICKS = 5;
    private static final int RESCAN_INTERVAL_TICKS = 60;
    private static final int REBUILD_FIELD_INTERVAL_TICKS = 100;
    private static final int TEMP_REFRESH_INTERVAL_TICKS = 40;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static int errorCount = 0;
    private static final int MAX_SOURCE_CACHE = 2048;
    private static final int MAX_FIELD_SOURCES = 16;
    /** Порог изменения температуры для отображения блока в дебаге, °C. */
    private static final float MIN_TEMP_DELTA = 3.0f;

    // ═══════════════════════════════════════════════════════════
    // НЕИЗМЕНЯЕМЫЙ СНИМОК ДЛЯ РЕНДЕРА (атомарная публикация)
    // ═══════════════════════════════════════════════════════════

    /**
     * Опубликованный снимок для рендера. Атомарно заменяется в конце каждого тика.
     * Все коллекции внутри — неизменяемые.
     */
    public static class Snapshot {
        public final Map<BlockPos, Float> field;
        public final Map<BlockPos, Integer> faceMasks;
        public final List<HeatSourceHelper.HeatSource> sources;
        public final Map<BlockPos, Float> haloCells;
        public final HeatSourceHelper.HeatSource hoveredMultiblock;

        public Snapshot(
                Map<BlockPos, Float> field,
                Map<BlockPos, Integer> faceMasks,
                List<HeatSourceHelper.HeatSource> sources,
                Map<BlockPos, Float> haloCells,
                HeatSourceHelper.HeatSource hoveredMultiblock
        ) {
            this.field = Map.copyOf(field);
            this.faceMasks = Map.copyOf(faceMasks);
            this.sources = List.copyOf(sources);
            this.haloCells = Map.copyOf(haloCells);
            this.hoveredMultiblock = hoveredMultiblock;
        }

        private static final Snapshot EMPTY = new Snapshot(
                Map.of(), Map.of(), List.of(), Map.of(), null
        );
    }

    /** Опубликованный снимок — читается рендером, пишется только тиковым потоком. */
    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    // ═══════════════════════════════════════════════════════════
    // РАБОЧЕЕ СОСТОЯНИЕ (только тиковый поток)
    // ═══════════════════════════════════════════════════════════

    private static final Map<ChunkPos, List<HeatSourceHelper.HeatSource>> chunkSources = new HashMap<>();
    private static final LinkedHashSet<ChunkPos> scanQueue = new LinkedHashSet<>();
    private static final List<HeatSourceHelper.HeatSource> calculationSources = new ArrayList<>();
    /**
     * Габариты источников для отсечки по расстоянию. У кластера (озеро
     * лавы) крайние клетки лежат в десятках блоков от якоря, поэтому
     * расстояние считается до ящика клеток, а не до одной точки.
     */
    private static final List<SourceBox> sourceBoxes = new ArrayList<>();
    /**
     * Кластеры (лавовые озёра, ледники) считаются один раз на чанк, а не
     * заново по всем источникам каждые 5 тиков: на леднике это были тысячи
     * обходов в ширину за тик, и сбор данных не успевал никогда.
     */
    private static final Map<ChunkPos, List<HeatSourceHelper.HeatSource>> chunkClusters = new HashMap<>();
    /** Чанки, чьи кластеры устарели (пересканированы или выгружены). */
    private static final Set<ChunkPos> dirtyChunks = new HashSet<>();
    private static final ArrayDeque<SourceTask> pendingSources = new ArrayDeque<>();
    private static final ArrayDeque<BlockPos> pendingPoints = new ArrayDeque<>();
    private static final Set<BlockPos> queuedPoints = new HashSet<>();
    private static final List<BlockPos> refreshList = new ArrayList<>();
    private static int refreshIndex = 0;
    private static int lastTempRefreshTick = Integer.MIN_VALUE;
    private static final Set<BlockPos> seenCandidates = new HashSet<>();
    private static final List<HeatSourceHelper.HeatSource> fieldSources = new ArrayList<>();
    private static final Set<BlockPos> stalePoints = new HashSet<>();

    /** Рабочие копии карт — публикуются в конце тика. */
    private static final Map<BlockPos, Float> workField = new HashMap<>();
    private static final Map<BlockPos, Integer> workFaceMasks = new HashMap<>();
    private static final Map<BlockPos, Float> workHaloCells = new HashMap<>();
    /** Мультиблоки для рамок (кластеры лавы и льда сюда не входят). */
    private static final List<HeatSourceHelper.HeatSource> workOutlineSources = new ArrayList<>();
    private static HeatSourceHelper.HeatSource workHoveredMultiblock = null;

    /** Задача расширения области влияния одного источника. */
    private record SourceTask(HeatSourceHelper.HeatSource source, float radius) {}

    /**
     * Источник вместе с габаритами его клеток — для отсечки по расстоянию.
     * <p>
     * Якорь кластера — одна точка, а крайние клетки озера лежат в десятках
     * блоков от неё. Если мерить до якоря, озеро выпадало из расчёта на
     * собственном краю и влияние обрывалось на середине.
     */
    private record SourceBox(HeatSourceHelper.HeatSource source, float radius,
                             int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        /** Накрывает ли ящик с запасом точку. */
        boolean reaches(int x, int y, int z, float margin) {
            return x >= minX - margin && x <= maxX + margin
                    && y >= minY - margin && y <= maxY + margin
                    && z >= minZ - margin && z <= maxZ + margin;
        }
    }

    private static ClientLevel cachedLevel = null;
    private static int lastRefreshTick = Integer.MIN_VALUE;
    private static int lastRescanTick = Integer.MIN_VALUE;
    private static int lastRebuildTick = Integer.MIN_VALUE;
    private static int sourcesSignature = 0;
    private static BlockPos lastPlayerPos = BlockPos.ZERO;
    private static BlockPos lastRebuildPos = null;

    /** Reusable cursors — только в тиковом потоке. */
    private static final BlockPos.MutableBlockPos solidNeighbourCursor = new BlockPos.MutableBlockPos();

    private HeatDebugController() {}

    // ═══════════════════════════════════════════════════════════
    // ПУБЛИЧНЫЙ API ДЛЯ РЕНДЕРА
    // ═══════════════════════════════════════════════════════════

    public static Snapshot snapshot() {
        return snapshot;
    }

    /** @deprecated Используйте {@link #snapshot()} */
    @Deprecated
    public static Map<BlockPos, Float> field() {
        return snapshot.field;
    }

    /** @deprecated Используйте {@link #snapshot()} */
    @Deprecated
    public static Map<BlockPos, Integer> faceMasks() {
        return snapshot.faceMasks;
    }

    /** @deprecated Используйте {@link #snapshot()} */
    @Deprecated
    public static List<HeatSourceHelper.HeatSource> sources() {
        return snapshot.sources;
    }

    /** @deprecated Используйте {@link #snapshot()} */
    @Deprecated
    public static Map<BlockPos, Float> haloCells() {
        return snapshot.haloCells;
    }

    /** @deprecated Используйте {@link #snapshot()} */
    @Deprecated
    public static HeatSourceHelper.HeatSource hoveredMultiblock() {
        return snapshot.hoveredMultiblock;
    }

    // ═══════════════════════════════════════════════════════════
    // ТИК
    // ═══════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ModDebugMode.enabled) {
            reset();
            errorCount = 0;
            return;
        }
        try {
            tick(event);
        } catch (Throwable t) {
            errorCount++;
            if (errorCount == 1) {
                LOGGER.error("[TRD] Ошибка сбора данных для оверлея тепла:", t);
            }
            if (errorCount >= 5) {
                LOGGER.error("[TRD] Оверлей тепла отключён из-за повторных ошибок.");
                ModDebugMode.enabled = false;
                reset();
            }
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        if (!ModDebugMode.enabled) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        ClientLevel level = mc.level;
        if (level != cachedLevel) {
            reset();
            cachedLevel = level;
        }

        BlockPos playerPos = mc.player.blockPosition();
        int tick = mc.player.tickCount;
        lastPlayerPos = playerPos;

        // 1. Обновление списка источников
        if (tick - lastRefreshTick >= REFRESH_INTERVAL_TICKS || lastRefreshTick == Integer.MIN_VALUE) {
            lastRefreshTick = tick;
            refreshSources(level, playerPos);
        }

        // 1б. Мультиблок под прицелом
        HeatSourceHelper.HeatSource hover = null;
        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            HeatSourceHelper.HeatSource s = HeatSourceHelper.resolveAt(level, blockHit.getBlockPos());
            if (s != null && s.cells().size() > 1) {
                hover = s;
            }
        }
        workHoveredMultiblock = hover;

        // 2. Периодическое пересканирование чанков
        if (tick - lastRescanTick >= RESCAN_INTERVAL_TICKS || lastRescanTick == Integer.MIN_VALUE) {
            lastRescanTick = tick;
            queueChunksAround(level, playerPos, true);
        }

        // 2б. Полная пересборка поля
        boolean playerMoved = lastRebuildPos == null || lastRebuildPos.distSqr(playerPos) > 6 * 6;
        if (tick - lastRebuildTick >= REBUILD_FIELD_INTERVAL_TICKS
                || lastRebuildTick == Integer.MIN_VALUE
                || playerMoved) {
            lastRebuildTick = tick;
            rebuildCandidates();
        }

        // 2в. Круговое обновление температур
        if (refreshIndex >= refreshList.size()) {
            if (!seenCandidates.isEmpty()
                    && (tick - lastTempRefreshTick >= TEMP_REFRESH_INTERVAL_TICKS
                        || lastTempRefreshTick == Integer.MIN_VALUE)) {
                refreshList.clear();
                refreshList.addAll(seenCandidates);
                refreshIndex = 0;
                lastTempRefreshTick = tick;
            }
        }
        if (refreshIndex < refreshList.size()) {
            int remaining = refreshList.size() - refreshIndex;
            int perTick = Math.max(1, (remaining + TEMP_REFRESH_INTERVAL_TICKS - 1) / TEMP_REFRESH_INTERVAL_TICKS);
            for (int i = 0; i < perTick && refreshIndex < refreshList.size(); i++, refreshIndex++) {
                BlockPos pos = refreshList.get(refreshIndex);
                if (seenCandidates.contains(pos) && queuedPoints.add(pos)) {
                    pendingPoints.add(pos);
                }
            }
        }

        // 3. Сканирование чанков
        scanQueuedChunks(level, playerPos, phaseBudget(0.4));

        // 4. Отбор кандидатов
        long expandDeadline = phaseBudget(0.8);
        while (!pendingSources.isEmpty()
                && seenCandidates.size() < ModClientConfig.heatDebugMaxPoints()
                && System.nanoTime() < expandDeadline) {
            expandSource(level, pendingSources.poll());
        }
        if (seenCandidates.size() >= ModClientConfig.heatDebugMaxPoints()) {
            pendingSources.clear();
        }

        // 5. Расчёт температур
        computePendingPoints(level, phaseBudget(1.0));

        // 6. Уборка устаревших точек
        if (pendingSources.isEmpty()) {
            for (BlockPos pos : stalePoints) {
                if (!seenCandidates.contains(pos)) {
                    workField.remove(pos);
                    workFaceMasks.remove(pos);
                }
            }
            stalePoints.clear();
        }

        // 7. АТОМАРНАЯ ПУБЛИКАЦИЯ СНИМКА
        publishSnapshot();
    }

    private static long phaseBudget(double fraction) {
        return System.nanoTime() + (long) (TICK_BUDGET_MS * fraction * 1_000_000.0);
    }

    /**
     * Атомарно публикует неизменяемый снимок для рендера.
     * Вызывается в конце каждого тика.
     */
    private static void publishSnapshot() {
        snapshot = new Snapshot(
                workField,
                workFaceMasks,
                workOutlineSources,
                workHaloCells,
                workHoveredMultiblock
        );
    }

    // ═══════════════════════════════════════════════════════════
    // ИСТОЧНИКИ
    // ═══════════════════════════════════════════════════════════

    private static void refreshSources(ClientLevel level, BlockPos playerPos) {
        int radius = ModClientConfig.heatDebugRadius();
        float maxSourceRadius = HeatSourceRegistry.maxRadius();
        double rangeSq = (radius + maxSourceRadius) * (radius + maxSourceRadius);

        chunkSources.entrySet().removeIf(entry -> {
            ChunkPos cp = entry.getKey();
            long dx = (cp.x << 4) + 8 - playerPos.getX();
            long dz = (cp.z << 4) + 8 - playerPos.getZ();
            boolean drop = dx * dx + dz * dz > rangeSq || !level.hasChunk(cp.x, cp.z);
            if (drop) {
                dirtyChunks.add(cp);
            }
            return drop;
        });

        queueChunksAround(level, playerPos, false);

        rebuildDirtyClusters(level);

        int oldSignature = sourcesSignature;
        pickFieldSources(playerPos);

        workHaloCells.clear();
        for (HeatSourceHelper.HeatSource source : fieldSources) {
            // Воздушные источники только в радиусе отображения, как и обычные
            double sdx = source.pos().getX() - playerPos.getX();
            double sdy = source.pos().getY() - playerPos.getY();
            double sdz = source.pos().getZ() - playerPos.getZ();
            double sourceDist = Math.sqrt(sdx * sdx + sdy * sdy + sdz * sdz);
            if (sourceDist > ModClientConfig.heatDebugRadius()) {
                continue;
            }
            for (BlockPos air : HeatSourceHelper.airHaloCells(level, source)) {
                // Цвет маркера — от родительского источника
                workHaloCells.putIfAbsent(air, source.sourceTemp());
            }
        }

        // Рамки только у настоящих мультиблоков: у кластера (лава, лёд) блок
        // в якоре разрешается в то же описание, у мультиблока — нет
        workOutlineSources.clear();
        for (HeatSourceHelper.HeatSource source : fieldSources) {
            if (source.cells().size() > 1
                    && HeatSourceRegistry.resolveState(level.getBlockState(source.pos())) != source.def()) {
                workOutlineSources.add(source);
            }
        }

        int signature = 1;
        for (HeatSourceHelper.HeatSource source : fieldSources) {
            signature = signature * 31 + source.pos().hashCode();
            // Число клеток: озеро могло подрасти, а якорь остался тем же —
            // без этого поле не пересобиралось и влияние оставалось старым
            signature = signature * 31 + source.cells().size();
        }
        sourcesSignature = signature;

        if (signature != oldSignature) {
            rebuildCandidates();
        }
    }

    /**
     * Пересобирает кластеры только у изменившихся чанков и их соседей:
     * озеро может пересекать границу чанка, поэтому соседа тоже надо
     * пересчитать. Неизменные чанки не трогаем.
     */
    private static void rebuildDirtyClusters(ClientLevel level) {
        if (!dirtyChunks.isEmpty()) {
            Set<ChunkPos> affected = new HashSet<>();
            for (ChunkPos cp : dirtyChunks) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        affected.add(new ChunkPos(cp.x + dx, cp.z + dz));
                    }
                }
            }
            dirtyChunks.clear();

            List<HeatSourceHelper.HeatSource> pool = new ArrayList<>();
            for (ChunkPos cp : affected) {
                List<HeatSourceHelper.HeatSource> cached = chunkSources.get(cp);
                if (cached != null) {
                    pool.addAll(cached);
                }
            }
            List<HeatSourceHelper.HeatSource> clustered = HeatSourceHelper.clusterSources(level, pool);
            for (ChunkPos cp : affected) {
                chunkClusters.remove(cp);
            }
            for (HeatSourceHelper.HeatSource cluster : clustered) {
                chunkClusters.computeIfAbsent(new ChunkPos(cluster.pos()), k -> new ArrayList<>()).add(cluster);
            }
        }

        calculationSources.clear();
        sourceBoxes.clear();
        for (List<HeatSourceHelper.HeatSource> clusters : chunkClusters.values()) {
            for (HeatSourceHelper.HeatSource cluster : clusters) {
                calculationSources.add(cluster);
                sourceBoxes.add(boxOf(cluster));
            }
        }
    }

    /**
     * Габариты клеток источника — для отсечки по расстоянию.
     * <p>
     * У одиночного источника это его собственный блок, у кластера — ящик
     * всех клеток. Считать расстояние до якоря нельзя: у озера лавы якорь
     * стоит в углу, и противоположный край озера выпадал из расчёта.
     */
    private static SourceBox boxOf(HeatSourceHelper.HeatSource source) {
        List<BlockPos> cells = source.cells();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos c : cells) {
            minX = Math.min(minX, c.getX());
            maxX = Math.max(maxX, c.getX());
            minY = Math.min(minY, c.getY());
            maxY = Math.max(maxY, c.getY());
            minZ = Math.min(minZ, c.getZ());
            maxZ = Math.max(maxZ, c.getZ());
        }
        return new SourceBox(source, source.radius(), minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Источники для поля: кластеры, а не отдельные блоки. Иначе 16 слотов
     * занимали 16 блоков одного ледника, а печь рядом в поле не попадала.
     * Кластер занимает один слот и покрывает всё озеро.
     */
    private static void pickFieldSources(BlockPos playerPos) {
        Map<BlockPos, HeatSourceHelper.HeatSource> fresh = new HashMap<>();
        for (HeatSourceHelper.HeatSource source : calculationSources) {
            fresh.put(source.pos(), source);
        }

        List<HeatSourceHelper.HeatSource> kept = new ArrayList<>();
        for (HeatSourceHelper.HeatSource old : fieldSources) {
            HeatSourceHelper.HeatSource updated = fresh.get(old.pos());
            if (updated != null) {
                kept.add(updated);
            }
        }

        if (kept.size() < MAX_FIELD_SOURCES && !calculationSources.isEmpty()) {
            Set<BlockPos> keptPositions = new HashSet<>();
            for (HeatSourceHelper.HeatSource source : kept) {
                keptPositions.add(source.pos());
            }
            List<HeatSourceHelper.HeatSource> sorted = new ArrayList<>(calculationSources);
            sorted.sort((a, b) -> Double.compare(distanceSq(a.pos(), playerPos), distanceSq(b.pos(), playerPos)));
            for (HeatSourceHelper.HeatSource source : sorted) {
                if (kept.size() >= MAX_FIELD_SOURCES) {
                    break;
                }
                if (keptPositions.add(source.pos())) {
                    kept.add(source);
                }
            }
        }

        fieldSources.clear();
        fieldSources.addAll(kept);
    }

    private static double distanceSq(BlockPos pos, BlockPos playerPos) {
        double dx = pos.getX() - playerPos.getX();
        double dy = pos.getY() - playerPos.getY();
        double dz = pos.getZ() - playerPos.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static void rebuildCandidates() {
        stalePoints.addAll(seenCandidates);
        seenCandidates.clear();
        pendingSources.clear();
        lastRebuildPos = lastPlayerPos;
        for (HeatSourceHelper.HeatSource source : fieldSources) {
            pendingSources.add(new SourceTask(source, source.radius()));
        }
    }

    /**
     * Расширяет область влияния источника на блоки-кандидаты поля.
     * <p>
     * Одиночный источник разворачивается сферой вокруг блока. Кластер
     * (озеро лавы, куча льда) — объединением сфер вокруг <b>всех</b> его
     * клеток.
     * <p>
     * Раньше клетки кластера отбрасывались как «уже развёрнутые», если рядом
     * была развёрнута соседняя. Из-за этого у озера разворачивалась только
     * одна клетка, и влияние источника обрывалось на краю радиуса действия:
     * игрок видел горячий край и холодную пустоту в паре шагов.
     * <p>
     * Область считается по габаритному ящику кластера: для компактного
     * кластера (озеро, ледник) это точное объединение сфер, для изрезанного
     * (лава по склону) — с небольшим запасом, лишние точки отсеются по ΔT.
     */
    private static void expandSource(ClientLevel level, SourceTask task) {
        HeatSourceHelper.HeatSource source = task.source();
        List<BlockPos> cells = source.cells();

        // +1: точка замера берётся на блок выше сплошного блока, иначе
        // самый край зоны влияния не попадал бы в поле
        int rad = (int) Math.ceil(task.radius()) + 1;
        double radSq = (double) rad * rad;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos c : cells) {
            minX = Math.min(minX, c.getX());
            maxX = Math.max(maxX, c.getX());
            minY = Math.min(minY, c.getY());
            maxY = Math.max(maxY, c.getY());
            minZ = Math.min(minZ, c.getZ());
            maxZ = Math.max(maxZ, c.getZ());
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int maxPoints = ModClientConfig.heatDebugMaxPoints();

        if (cells.size() == 1) {
            // Одиночный источник показываем вместе с его собственным блоком
            addCandidate(cells.get(0), level, seenCandidates);
        }

        for (int bx = minX - rad; bx <= maxX + rad; bx++) {
            int ddx = Math.max(minX - bx, 0) + Math.max(bx - maxX, 0);
            for (int by = minY - rad; by <= maxY + rad; by++) {
                int ddy = Math.max(minY - by, 0) + Math.max(by - maxY, 0);
                for (int bz = minZ - rad; bz <= maxZ + rad; bz++) {
                    int ddz = Math.max(minZ - bz, 0) + Math.max(bz - maxZ, 0);
                    double boxDistSq = (double) ddx * ddx + (double) ddy * ddy + (double) ddz * ddz;
                    if (boxDistSq > radSq) {
                        continue;
                    }
                    // Блок внутри габаритов: расстояние считаем до ближайшей
                    // клетки кластера, иначе вогнутые участки давали лишние
                    // точки и съедали лимит кандидатов
                    if (cells.size() > 1 && ddx == 0 && ddy == 0 && ddz == 0) {
                        double cellDistSq = distanceSqToCells(bx, by, bz, cells);
                        if (cellDistSq <= 0.0) {
                            continue;   // собственные клетки источника не рисуем
                        }
                        if (cellDistSq > radSq) {
                            continue;
                        }
                    }
                    cursor.set(bx, by, bz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || !state.isSolidRender(level, cursor)) {
                        continue;
                    }
                    if (!hasOpenFace(level, cursor)) {
                        continue;
                    }
                    addCandidate(cursor, level, seenCandidates);
                    if (seenCandidates.size() >= maxPoints) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * Квадрат расстояния от точки до ближайшей клетки списка.
     * Ноль означает, что точка сама является клеткой источника.
     */
    private static double distanceSqToCells(int x, int y, int z, List<BlockPos> cells) {
        double best = Double.MAX_VALUE;
        for (BlockPos c : cells) {
            double dx = x - c.getX();
            double dy = y - c.getY();
            double dz = z - c.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < best) {
                best = d;
                if (best <= 0.0) {
                    return 0.0;
                }
            }
        }
        return best;
    }

    private static void queueChunksAround(ClientLevel level, BlockPos playerPos, boolean force) {
        int chunkRadius = (ModClientConfig.heatDebugRadius() + 16) >> 4;
        int cx = playerPos.getX() >> 4;
        int cz = playerPos.getZ() >> 4;
        for (int x = cx - chunkRadius; x <= cx + chunkRadius; x++) {
            for (int z = cz - chunkRadius; z <= cz + chunkRadius; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                if (force) {
                    scanQueue.add(pos);
                } else if (!chunkSources.containsKey(pos)) {
                    scanQueue.add(pos);
                }
            }
        }
    }

    private static void scanQueuedChunks(ClientLevel level, BlockPos playerPos, long deadline) {
        if (scanQueue.isEmpty()) {
            return;
        }
        do {
            ChunkPos cp = scanQueue.iterator().next();
            scanQueue.remove(cp);
            if (!level.hasChunk(cp.x, cp.z)) {
                continue;
            }
            List<HeatSourceHelper.HeatSource> found = scanChunk(level, cp, playerPos);
            chunkSources.put(cp, found);
            dirtyChunks.add(cp);

            if (chunkSources.size() > MAX_SOURCE_CACHE) {
                double range = ModClientConfig.heatDebugRadius() + HeatSourceRegistry.maxRadius();
                double rangeSq = range * range;
                chunkSources.entrySet().removeIf(e -> {
                    long dx = (e.getKey().x << 4) + 8 - playerPos.getX();
                    long dz = (e.getKey().z << 4) + 8 - playerPos.getZ();
                    return dx * dx + dz * dz > rangeSq;
                });
            }
        } while (System.nanoTime() < deadline && !scanQueue.isEmpty());
    }

    private static List<HeatSourceHelper.HeatSource> scanChunk(ClientLevel level, ChunkPos cp, BlockPos playerPos) {
        List<HeatSourceHelper.HeatSource> found = new ArrayList<>();
        ChunkAccess chunk = level.getChunkSource()
                .getChunk(cp.x, cp.z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
        if (chunk == null) {
            return found;
        }

        int band = Math.min(28, Math.max(16, (int) Math.ceil(HeatSourceRegistry.maxRadius()) + 12));
        int minY = Math.max(chunk.getMinBuildHeight(), playerPos.getY() - band);
        int maxY = Math.min(chunk.getMaxBuildHeight(), playerPos.getY() + band);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Set<BlockPos> seen = new HashSet<>();
        for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) {
            for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    BlockState state = chunk.getBlockState(cursor);
                    if (!HeatSourceRegistry.isPotentialSource(state.getBlock())
                            && !(state.getBlock() instanceof com.trd.multiblock.system.MultiblockPartBlock)) {
                        continue;
                    }
                    HeatSourceHelper.HeatSource source = HeatSourceHelper.resolveAt(level, cursor);
                    if (source != null && seen.add(source.pos())) {
                        found.add(source);
                    }
                }
            }
        }
        return found;
    }

    // ═══════════════════════════════════════════════════════════
    // ПОЛЕ ТЕМПЕРАТУР
    // ═══════════════════════════════════════════════════════════

    private static void addCandidate(BlockPos pos, ClientLevel level, Set<BlockPos> seen) {
        BlockPos immutable = pos.immutable();
        if (seen.size() >= ModClientConfig.heatDebugMaxPoints()) {
            return;
        }
        double maxDist = ModClientConfig.heatDebugRadius() + 2.0;
        if (immutable.distSqr(lastPlayerPos) > maxDist * maxDist) {
            return;
        }
        if (!seen.add(immutable)) {
            return;
        }
        if (queuedPoints.add(immutable)) {
            pendingPoints.add(immutable);
        }
        workFaceMasks.put(immutable, computeFaceMask(level, immutable));
    }

    private static boolean hasOpenFace(ClientLevel level, BlockPos pos) {
        return !isSolidNeighbour(level, pos.getX() + 1, pos.getY(), pos.getZ())
                || !isSolidNeighbour(level, pos.getX() - 1, pos.getY(), pos.getZ())
                || !isSolidNeighbour(level, pos.getX(), pos.getY() + 1, pos.getZ())
                || !isSolidNeighbour(level, pos.getX(), pos.getY() - 1, pos.getZ())
                || !isSolidNeighbour(level, pos.getX(), pos.getY(), pos.getZ() + 1)
                || !isSolidNeighbour(level, pos.getX(), pos.getY(), pos.getZ() - 1);
    }

    private static int computeFaceMask(ClientLevel level, BlockPos pos) {
        int mask = 0;
        if (!isSolidNeighbour(level, pos.getX(), pos.getY() + 1, pos.getZ())) mask |= 1;
        if (!isSolidNeighbour(level, pos.getX(), pos.getY() - 1, pos.getZ())) mask |= 2;
        if (!isSolidNeighbour(level, pos.getX() + 1, pos.getY(), pos.getZ())) mask |= 4;
        if (!isSolidNeighbour(level, pos.getX() - 1, pos.getY(), pos.getZ())) mask |= 8;
        if (!isSolidNeighbour(level, pos.getX(), pos.getY(), pos.getZ() + 1)) mask |= 16;
        if (!isSolidNeighbour(level, pos.getX(), pos.getY(), pos.getZ() - 1)) mask |= 32;
        return mask;
    }

    private static boolean isSolidNeighbour(ClientLevel level, int x, int y, int z) {
        solidNeighbourCursor.set(x, y, z);
        BlockState state = level.getBlockState(solidNeighbourCursor);
        return !state.isAir() && state.isSolidRender(level, solidNeighbourCursor);
    }

    private static void computePendingPoints(ClientLevel level, long deadline) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        List<HeatSourceHelper.HeatSource> near = new ArrayList<>(8);
        while (!pendingPoints.isEmpty() && System.nanoTime() < deadline) {
            BlockPos pos = pendingPoints.poll();
            queuedPoints.remove(pos);
            if (!seenCandidates.contains(pos)) {
                continue;
            }
            // В расчёт лучей входят только источники, чья зона влияния
            // накрывает точку. Для кластера расстояние считается до ящика
            // его клеток: до якоря озеро выпадало из расчёта на своём краю,
            // и влияние обрывалось, хотя блоки грелись на 40–50 °C.
            near.clear();
            for (SourceBox box : sourceBoxes) {
                if (box.reaches(pos.getX(), pos.getY(), pos.getZ(), box.radius() + 1.0f)) {
                    near.add(box.source());
                }
            }
            cursor.set(pos.getX(), pos.getY(), pos.getZ());
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(cursor);
            boolean solid = !state.isAir() && state.isSolidRender(level, cursor);
            cursor.set(pos.getX(), pos.getY() + (solid ? 1 : 0), pos.getZ());
            TemperatureCalculator.PointTemps point =
                    TemperatureCalculator.calculatePointTemps(level, cursor, near);
            float temp = point.temp();

            // Фильтр: не показываем блоки, которые источники почти не изменили.
            // Сравниваем с ФОНОМ В САМОЙ ТОЧКЕ, а не с температурой игрока:
            // в пещере под тёплым небом температура игрока на 30 °C выше, и
            // фильтр выбрасывал из влияния целые участки вокруг источника.
            float delta = Math.abs(temp - point.ambient());
            if (delta < MIN_TEMP_DELTA) {
                // Точка остаётся в seenCandidates: круговое обновление её
                // пересчитает, и она появится, когда температура изменится
                workField.remove(pos);
                continue;
            }
            
            workField.put(pos, temp);
        }
    }

    private static void reset() {
        workField.clear();
        workFaceMasks.clear();
        workHaloCells.clear();
        workOutlineSources.clear();
        workHoveredMultiblock = null;
        chunkSources.clear();
        chunkClusters.clear();
        dirtyChunks.clear();
        scanQueue.clear();
        calculationSources.clear();
        sourceBoxes.clear();
        pendingSources.clear();
        pendingPoints.clear();
        queuedPoints.clear();
        refreshList.clear();
        refreshIndex = 0;
        lastTempRefreshTick = Integer.MIN_VALUE;
        seenCandidates.clear();
        stalePoints.clear();
        fieldSources.clear();
        snapshot = Snapshot.EMPTY;
        sourcesSignature = 0;
        errorCount = 0;
        lastRefreshTick = Integer.MIN_VALUE;
        lastRescanTick = Integer.MIN_VALUE;
        lastRebuildTick = Integer.MIN_VALUE;
        lastRebuildPos = null;
        cachedLevel = null;
    }
}
