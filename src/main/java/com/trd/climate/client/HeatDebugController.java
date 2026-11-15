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
    private static final List<HeatSourceHelper.HeatSource> activeSources = new ArrayList<>();
    private static final List<HeatSourceHelper.HeatSource> calculationSources = new ArrayList<>();
    /**
     * Кластеры (лавовые озёра, ледники) считаются один раз на чанк, а не
     * заново по всем источникам каждые 5 тиков: на леднике это были тысячи
     * обходов в ширину за тик, и сбор данных не успевал никогда.
     */
    private static final Map<ChunkPos, List<HeatSourceHelper.HeatSource>> chunkClusters = new HashMap<>();
    /** Чанки, чьи кластеры устарели (пересканированы или выгружены). */
    private static final Set<ChunkPos> dirtyChunks = new HashSet<>();
    private static final ArrayDeque<CellTask> pendingCells = new ArrayDeque<>();
    private static final ArrayDeque<BlockPos> pendingPoints = new ArrayDeque<>();
    private static final Set<BlockPos> queuedPoints = new HashSet<>();
    private static final List<BlockPos> refreshList = new ArrayList<>();
    private static int refreshIndex = 0;
    private static int lastTempRefreshTick = Integer.MIN_VALUE;
    private static final Set<BlockPos> seenCandidates = new HashSet<>();
    private static final Set<BlockPos> expandedCells = new HashSet<>();
    private static final List<HeatSourceHelper.HeatSource> fieldSources = new ArrayList<>();
    private static final Set<BlockPos> stalePoints = new HashSet<>();

    /** Рабочие копии карт — публикуются в конце тика. */
    private static final Map<BlockPos, Float> workField = new HashMap<>();
    private static final Map<BlockPos, Integer> workFaceMasks = new HashMap<>();
    private static final Map<BlockPos, Float> workHaloCells = new HashMap<>();
    private static HeatSourceHelper.HeatSource workHoveredMultiblock = null;

    private record CellTask(BlockPos pos, float radius, boolean paintSelf) {}

    private static ClientLevel cachedLevel = null;
    private static int lastRefreshTick = Integer.MIN_VALUE;
    private static int lastRescanTick = Integer.MIN_VALUE;
    private static int lastRebuildTick = Integer.MIN_VALUE;
    private static int sourcesSignature = 0;
    private static BlockPos lastPlayerPos = BlockPos.ZERO;
    private static BlockPos lastRebuildPos = null;

    /** Reusable cursors — только в тиковом потоке. */
    private static final BlockPos.MutableBlockPos solidNeighbourCursor = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos expandedNearCursor = new BlockPos.MutableBlockPos();

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
        while (!pendingCells.isEmpty()
                && seenCandidates.size() < ModClientConfig.heatDebugMaxPoints()
                && System.nanoTime() < expandDeadline) {
            expandCell(level, pendingCells.poll());
        }
        if (seenCandidates.size() >= ModClientConfig.heatDebugMaxPoints()) {
            pendingCells.clear();
        }

        // 5. Расчёт температур
        computePendingPoints(level, phaseBudget(1.0));

        // 6. Уборка устаревших точек
        if (pendingCells.isEmpty()) {
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
                fieldSources,
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

        activeSources.clear();

        for (Map.Entry<ChunkPos, List<HeatSourceHelper.HeatSource>> entry : chunkSources.entrySet()) {
            List<HeatSourceHelper.HeatSource> cached = entry.getValue();
            if (cached.isEmpty()) {
                continue;
            }
            List<HeatSourceHelper.HeatSource> valid = new ArrayList<>(cached.size());
            boolean changed = false;
            for (HeatSourceHelper.HeatSource old : cached) {
                HeatSourceHelper.HeatSource fresh = HeatSourceHelper.resolveAt(level, old.pos());
                if (fresh == null) {
                    changed = true;
                    continue;
                }
                long dx = fresh.pos().getX() - playerPos.getX();
                long dy = fresh.pos().getY() - playerPos.getY();
                long dz = fresh.pos().getZ() - playerPos.getZ();
                if (dx * dx + dy * dy + dz * dz > rangeSq) {
                    changed = true;
                    continue;
                }
                if (fresh.sourceTemp() != old.sourceTemp() || fresh.power() != old.power()) {
                    changed = true;
                }
                valid.add(fresh);
                activeSources.add(fresh);
            }
            if (changed || valid.size() != cached.size()) {
                entry.setValue(valid);
                dirtyChunks.add(entry.getKey());
            }
        }

        rebuildDirtyClusters(level);

        int oldSignature = sourcesSignature;
        pickFieldSources(playerPos);

        workHaloCells.clear();
        for (HeatSourceHelper.HeatSource source : fieldSources) {
            for (BlockPos air : HeatSourceHelper.airHaloCells(level, source)) {
                workHaloCells.putIfAbsent(air, source.sourceTemp());
            }
        }

        int signature = 1;
        for (HeatSourceHelper.HeatSource source : fieldSources) {
            signature = signature * 31 + source.pos().hashCode();
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
        for (List<HeatSourceHelper.HeatSource> clusters : chunkClusters.values()) {
            calculationSources.addAll(clusters);
        }
    }

    private static void pickFieldSources(BlockPos playerPos) {
        Map<BlockPos, HeatSourceHelper.HeatSource> fresh = new HashMap<>();
        for (HeatSourceHelper.HeatSource source : activeSources) {
            fresh.put(source.pos(), source);
        }

        List<HeatSourceHelper.HeatSource> kept = new ArrayList<>();
        for (HeatSourceHelper.HeatSource old : fieldSources) {
            HeatSourceHelper.HeatSource updated = fresh.get(old.pos());
            if (updated != null) {
                kept.add(updated);
            }
        }

        if (kept.size() < MAX_FIELD_SOURCES && !activeSources.isEmpty()) {
            Set<BlockPos> keptPositions = new HashSet<>();
            for (HeatSourceHelper.HeatSource source : kept) {
                keptPositions.add(source.pos());
            }
            List<HeatSourceHelper.HeatSource> sorted = new ArrayList<>(activeSources);
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
        expandedCells.clear();
        pendingCells.clear();
        lastRebuildPos = lastPlayerPos;
        for (HeatSourceHelper.HeatSource source : fieldSources) {
            float radius = source.radius();
            boolean paintSelf = source.cells().size() == 1;
            List<BlockPos> cells = source.cells();
            if (cells.size() == 1) {
                pendingCells.add(new CellTask(cells.get(0), radius, paintSelf));
                continue;
            }
            Set<BlockPos> cellSet = new HashSet<>(cells);
            for (BlockPos cell : cells) {
                if (isBoundaryCell(cell, cellSet)) {
                    pendingCells.add(new CellTask(cell, radius, paintSelf));
                }
            }
        }
    }

    private static boolean isBoundaryCell(BlockPos cell, Set<BlockPos> cellSet) {
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            if (!cellSet.contains(cell.relative(dir))) {
                return true;
            }
        }
        return false;
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

    private static void expandCell(ClientLevel level, CellTask task) {
        BlockPos cell = task.pos();
        if (isExpandedNear(cell)) {
            return;
        }
        expandedCells.add(cell);

        if (task.paintSelf()) {
            addCandidate(cell, level, seenCandidates);
        }

        int rad = (int) Math.ceil(task.radius());
        double maxDist = ModClientConfig.heatDebugRadius() + 2.0;
        double maxDistSq = maxDist * maxDist;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dy * dy + dz * dz > rad * rad) {
                        continue;
                    }
                    int bx = cell.getX() + dx;
                    int by = cell.getY() + dy;
                    int bz = cell.getZ() + dz;
                    double pdx = bx - lastPlayerPos.getX();
                    double pdy = by - lastPlayerPos.getY();
                    double pdz = bz - lastPlayerPos.getZ();
                    if (pdx * pdx + pdy * pdy + pdz * pdz > maxDistSq) {
                        continue;
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
                }
            }
        }
    }

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
        List<HeatSourceHelper.HeatSource> sources = calculationSources;
        while (!pendingPoints.isEmpty() && System.nanoTime() < deadline) {
            BlockPos pos = pendingPoints.poll();
            queuedPoints.remove(pos);
            if (!seenCandidates.contains(pos)) {
                continue;
            }
            cursor.set(pos.getX(), pos.getY(), pos.getZ());
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(cursor);
            boolean solid = !state.isAir() && state.isSolidRender(level, cursor);
            cursor.set(pos.getX(), pos.getY() + (solid ? 1 : 0), pos.getZ());
            float temp = TemperatureCalculator.calculatePointTemp(level, cursor, sources);
            workField.put(pos, temp);
        }
    }

    private static boolean isExpandedNear(BlockPos cell) {
        if (expandedCells.contains(cell)) {
            return true;
        }
        int x = cell.getX(), y = cell.getY(), z = cell.getZ();
        expandedNearCursor.set(x, y + 1, z); if (expandedCells.contains(expandedNearCursor)) return true;
        expandedNearCursor.set(x, y - 1, z); if (expandedCells.contains(expandedNearCursor)) return true;
        expandedNearCursor.set(x, y, z + 1); if (expandedCells.contains(expandedNearCursor)) return true;
        expandedNearCursor.set(x, y, z - 1); if (expandedCells.contains(expandedNearCursor)) return true;
        expandedNearCursor.set(x + 1, y, z); if (expandedCells.contains(expandedNearCursor)) return true;
        expandedNearCursor.set(x - 1, y, z); return expandedCells.contains(expandedNearCursor);
    }

    private static void reset() {
        workField.clear();
        workFaceMasks.clear();
        workHaloCells.clear();
        workHoveredMultiblock = null;
        chunkSources.clear();
        chunkClusters.clear();
        dirtyChunks.clear();
        scanQueue.clear();
        activeSources.clear();
        calculationSources.clear();
        pendingCells.clear();
        pendingPoints.clear();
        queuedPoints.clear();
        refreshList.clear();
        refreshIndex = 0;
        lastTempRefreshTick = Integer.MIN_VALUE;
        seenCandidates.clear();
        expandedCells.clear();
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
