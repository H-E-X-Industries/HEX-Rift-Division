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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сбор данных для отладочного оверлея тепла.
 * <p>
 * Вся работа с миром выполняется <b>в клиентском тике</b>, а не в потоке
 * рендера. Это принципиально: обращение к миру (блок-сущности, структуры
 * мультиблоков) из потока рендера гоняется с тиковым потоком клиента и
 * приводит к крашам при установке/разборке мультиблоков.
 * <p>
 * Работа разбита на шаги, каждый из которых укладывается в бюджет времени:
 * <ol>
 *   <li><b>Источники</b> кэшируются по чанкам: чанк сканируется один раз,
 *       потом по кругу пересканируется (чтобы ловить установленные/сломанные блоки).</li>
 *   <li><b>Кандидаты</b> — блоки вокруг источников с открытой гранью.</li>
 *   <li><b>Температуры</b> считаются постепенно, по бюджету тика.</li>
 * </ol>
 * Поле не очищается целиком при изменениях — старые значения просто
 * перезаписываются, поэтому картинка не мерцает.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class HeatDebugController {

    /** Бюджет времени на пересчёт за один клиентский тик, мс. */
    private static final double TICK_BUDGET_MS = 3.0;
    /** Период обновления списка источников, тиков. */
    private static final int REFRESH_INTERVAL_TICKS = 5;
    /** Период полного пересканирования чанков вокруг игрока, тиков. */
    private static final int RESCAN_INTERVAL_TICKS = 20;
    /** Период полной пересборки поля, тиков (подхватывает сломанные/поставленные блоки). */
    private static final int REBUILD_FIELD_INTERVAL_TICKS = 100;

    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();
    /** Счётчик ошибок сбора данных (после 5 — отладка отключается). */
    private static int errorCount = 0;
    /** Максимум источников в кэше чанков. */
    private static final int MAX_SOURCE_CACHE = 2048;

    // ── Данные для рендера (потокобезопасные) ──────────────────────────
    /** Температура в точке, °C. */
    private static final Map<BlockPos, Float> field = new ConcurrentHashMap<>();
    /** Битовая маска открытых граней блока (для отрисовки без обращений к миру). */
    private static final Map<BlockPos, Integer> faceMasks = new ConcurrentHashMap<>();
    /** Снимок активных источников для рендера ( неизменяемый ). */
    private static volatile List<HeatSourceHelper.HeatSource> publishedSources = List.of();

    // ── Внутреннее состояние (только поток клиента) ─────────────────────
    private static final Map<ChunkPos, List<HeatSourceHelper.HeatSource>> chunkSources = new ConcurrentHashMap<>();
    private static final ArrayDeque<ChunkPos> scanQueue = new ArrayDeque<>();
    private static final List<HeatSourceHelper.HeatSource> activeSources = new ArrayList<>();
    private static final ArrayDeque<BlockPos> pendingCells = new ArrayDeque<>();
    private static final ArrayDeque<BlockPos> pendingPoints = new ArrayDeque<>();
    private static final Set<BlockPos> candidates = new HashSet<>();
    private static final Set<BlockPos> expandedCells = new HashSet<>();
    /** Точки, которые были в поле, но исчезли из нового набора кандидатов. */
    private static final Set<BlockPos> stalePoints = new HashSet<>();

    private static ClientLevel cachedLevel = null;
    private static int lastRefreshTick = Integer.MIN_VALUE;
    private static int lastRescanTick = Integer.MIN_VALUE;
    private static int lastRebuildTick = Integer.MIN_VALUE;
    private static int sourcesSignature = 0;

    private HeatDebugController() {
    }

    // ═══════════════════════════════════════════════════════════
    // ПУБЛИЧНЫЙ ДОСТУП ДЛЯ РЕНДЕРЕРА
    // ═══════════════════════════════════════════════════════════

    /** Поле температур (потокобезопасно для чтения). */
    public static Map<BlockPos, Float> field() {
        return field;
    }

    /** Маски открытых граней блоков поля. */
    public static Map<BlockPos, Integer> faceMasks() {
        return faceMasks;
    }

    /** Снимок активных источников. */
    public static List<HeatSourceHelper.HeatSource> sources() {
        return publishedSources;
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
            // Отладочный режим не должен ронять игру: ловим всё, включая
            // ошибки в структурах мультиблоков (они могут быть в промежуточном
            // состоянии, пока конструкция разбирается)
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
        long deadline = System.nanoTime() + (long) (TICK_BUDGET_MS * 1_000_000.0);

        // 1. Обновление списка источников
        if (tick - lastRefreshTick >= REFRESH_INTERVAL_TICKS || lastRefreshTick == Integer.MIN_VALUE) {
            lastRefreshTick = tick;
            refreshSources(level, playerPos);
        }

        // 2. Периодическое пересканирование чанков — чтобы подхватывать
        //    установленные и сломанные блоки (кэш чанков стареет)
        if (tick - lastRescanTick >= RESCAN_INTERVAL_TICKS || lastRescanTick == Integer.MIN_VALUE) {
            lastRescanTick = tick;
            queueChunksAround(level, playerPos, true);
        }

        // 2б. Полная пересборка поля раз в 5 секунд: блоки могли снести или
        //     поставить рядом с источником, а набор источников не изменился
        if (tick - lastRebuildTick >= REBUILD_FIELD_INTERVAL_TICKS || lastRebuildTick == Integer.MIN_VALUE) {
            lastRebuildTick = tick;
            rebuildCandidates();
        }

        // 3. Сканирование чанков
        scanQueuedChunks(level, playerPos, deadline);

        // 4. Отбор кандидатов (блоки с открытой гранью вокруг источников)
        long half = System.nanoTime() + (long) (TICK_BUDGET_MS * 500_000.0);
        while (!pendingCells.isEmpty()
                && candidates.size() < ModClientConfig.heatDebugMaxPoints()
                && System.nanoTime() < half) {
            expandCell(level);
        }
        if (candidates.size() >= ModClientConfig.heatDebugMaxPoints()) {
            // Лимит достигнут — доходим остальные клетки, чтобы уборка старых
            // точек не откладывалась бесконечно
            pendingCells.clear();
        }

        // 5. Расчёт температур
        computePendingPoints(level, deadline);

        // 6. Уборка устаревших точек (когда пересборка завершена)
        if (pendingCells.isEmpty() && pendingPoints.isEmpty()) {
            for (BlockPos pos : stalePoints) {
                if (!candidates.contains(pos)) {
                    field.remove(pos);
                    faceMasks.remove(pos);
                }
            }
            stalePoints.clear();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ИСТОЧНИКИ
    // ═══════════════════════════════════════════════════════════

    private static void refreshSources(ClientLevel level, BlockPos playerPos) {
        int radius = ModClientConfig.heatDebugRadius();
        float maxSourceRadius = HeatSourceRegistry.maxRadius();
        double rangeSq = (radius + maxSourceRadius) * (radius + maxSourceRadius);

        // Забываем чанки, которые вышли за радиус или выгружены
        chunkSources.entrySet().removeIf(entry -> {
            ChunkPos cp = entry.getKey();
            long dx = (cp.x << 4) + 8 - playerPos.getX();
            long dz = (cp.z << 4) + 8 - playerPos.getZ();
            return dx * dx + dz * dz > rangeSq || !level.hasChunk(cp.x, cp.z);
        });

        queueChunksAround(level, playerPos, false);

        activeSources.clear();
        int signature = 1;
        double displaySq = (double) radius * radius;

        for (Map.Entry<ChunkPos, List<HeatSourceHelper.HeatSource>> entry : chunkSources.entrySet()) {
            List<HeatSourceHelper.HeatSource> cached = entry.getValue();
            if (cached.isEmpty()) {
                continue;
            }
            // Перепроверяем источники: блок могли сломать или потушить
            List<HeatSourceHelper.HeatSource> valid = new ArrayList<>(cached.size());
            for (HeatSourceHelper.HeatSource old : cached) {
                HeatSourceHelper.HeatSource fresh = HeatSourceHelper.resolveAt(level, old.pos());
                if (fresh == null) {
                    continue;
                }
                long dx = fresh.pos().getX() - playerPos.getX();
                long dy = fresh.pos().getY() - playerPos.getY();
                long dz = fresh.pos().getZ() - playerPos.getZ();
                if (dx * dx + dy * dy + dz * dz > rangeSq) {
                    continue;
                }
                valid.add(fresh);
                activeSources.add(fresh);
                // В подпись входят только источники в пределах отображения
                if (dx * dx + dy * dy + dz * dz <= displaySq) {
                    signature = signature * 31 + fresh.pos().hashCode();
                }
            }
            entry.setValue(valid);
        }

        // Публикуем неизменяемый снимок для потока рендера
        publishedSources = List.copyOf(activeSources);

        if (signature != sourcesSignature) {
            sourcesSignature = signature;
            rebuildCandidates();
        }
    }

    /**
     * Пересобирает набор точек поля.
     * <p>
     * Поле НЕ очищается: старые значения просто перезаписываются (нет мерцания),
     * а исчезнувшие точки убираются в конце пересборки.
     */
    private static void rebuildCandidates() {
        stalePoints.addAll(candidates);
        candidates.clear();
        expandedCells.clear();
        pendingPoints.clear();
        pendingCells.clear();
        for (HeatSourceHelper.HeatSource source : activeSources) {
            pendingCells.addAll(source.cells());
        }
    }

    /**
     * Ставит в очередь сканирование чанков вокруг игрока.
     *
     * @param force пересканировать даже те чанки, что уже есть в кэше
     */
    private static void queueChunksAround(ClientLevel level, BlockPos playerPos, boolean force) {
        int chunkRadius = (ModClientConfig.heatDebugRadius() + 16) >> 4;
        int cx = playerPos.getX() >> 4;
        int cz = playerPos.getZ() >> 4;
        for (int x = cx - chunkRadius; x <= cx + chunkRadius; x++) {
            for (int z = cz - chunkRadius; z <= cz + chunkRadius; z++) {
                ChunkPos pos = new ChunkPos(x, z);
                if (force) {
                    chunkSources.remove(pos);
                }
                if (!chunkSources.containsKey(pos) && !scanQueue.contains(pos)) {
                    scanQueue.add(pos);
                }
            }
        }
    }

    /**
     * Сканирует очередь чанков, укладываясь в бюджет тика.
     */
    private static void scanQueuedChunks(ClientLevel level, BlockPos playerPos, long deadline) {
        if (scanQueue.isEmpty()) {
            return;
        }
        do {
            ChunkPos cp = scanQueue.poll();
            if (cp == null) {
                break;
            }
            if (!level.hasChunk(cp.x, cp.z)) {
                continue;
            }
            List<HeatSourceHelper.HeatSource> found = scanChunk(level, cp, playerPos);
            chunkSources.put(cp, found);

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

    /**
     * Сканирует один чанк на источники тепла.
     * <p>
     * Сканируется только полоса по высоте вокруг игрока — полная высота чанка
     * (384 блока) не нужна и стоила бы в 10 раз дороже.
     */
    private static List<HeatSourceHelper.HeatSource> scanChunk(ClientLevel level, ChunkPos cp, BlockPos playerPos) {
        List<HeatSourceHelper.HeatSource> found = new ArrayList<>();
        // Только уже загруженные чанки: принудительная загрузка в тике может
        // дорого стоить и иногда даже ронять клиент
        ChunkAccess chunk = level.getChunkSource()
                .getChunk(cp.x, cp.z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
        if (chunk == null) {
            return found;
        }

        int band = Math.max(16, (int) Math.ceil(HeatSourceRegistry.maxRadius()) + 12);
        int minY = Math.max(chunk.getMinBuildHeight(), playerPos.getY() - band);
        int maxY = Math.min(chunk.getMaxBuildHeight(), playerPos.getY() + band);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        // Мультиблок находится по каждой своей части — в кэш он попадает один раз
        Set<BlockPos> seen = new HashSet<>();
        for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) {
            for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    BlockState state = chunk.getBlockState(cursor);
                    // Быстрая отсевка: интересны только зарегистрированные блоки
                    // и блоки-парты мультиблоков
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

    /**
     * Отбирает блоки вокруг одной клетки источника: у них есть открытая грань.
     * <p>
     * Клетки рядом друг с другом (лавовое озеро, стена печи) не разворачиваются
     * по отдельности — их зоны перекрываются, и хватит одной.
     */
    private static void expandCell(ClientLevel level) {
        BlockPos cell = pendingCells.poll();
        if (cell == null) {
            return;
        }
        if (isExpandedNear(cell)) {
            return;
        }
        expandedCells.add(cell);

        int rad = (int) Math.ceil(HeatSourceRegistry.maxRadius());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (dx * dx + dy * dy + dz * dz > rad * rad) {
                        continue;
                    }
                    cursor.set(cell.getX() + dx, cell.getY() + dy, cell.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || !state.isSolidRender(level, cursor)) {
                        continue;
                    }
                    int mask = computeFaceMask(level, cursor);
                    if (mask == 0) {
                        continue; // блок полностью закрыт — его не видно
                    }
                    BlockPos point = cursor.immutable();
                    if (candidates.size() >= ModClientConfig.heatDebugMaxPoints()) {
                        return;
                    }
                    if (candidates.add(point)) {
                        pendingPoints.add(point);
                    }
                    faceMasks.put(point, mask);
                }
            }
        }
    }

    /**
     * Битовая маска открытых граней блока:
     * 1 — верх (+Y), 2 — низ (−Y), 4 — +X, 8 — −X, 16 — +Z, 32 — −Z.
     */
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
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
        BlockState state = level.getBlockState(p);
        return !state.isAir() && state.isSolidRender(level, p);
    }

    /**
     * Считает температуры точек, укладываясь в бюджет тика.
     * <p>
     * Температура меряется в воздухе над блоком (там, где стоит игрок),
     * а не внутри самого блока — иначе оверлей показывал бы на 3–5 °C
     * меньше, чем термометр (внутри блока не видно неба и нет солнечного
     * прогрева).
     */
    private static void computePendingPoints(ClientLevel level, long deadline) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        List<HeatSourceHelper.HeatSource> sources = activeSources;
        while (!pendingPoints.isEmpty() && System.nanoTime() < deadline) {
            BlockPos pos = pendingPoints.poll();
            cursor.set(pos.getX(), pos.getY(), pos.getZ());
            // Температура воздуха рядом с блоком (над полом) — так же, как
            // показывает термометр, а не «внутри» блока
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(cursor);
            boolean solid = !state.isAir() && state.isSolidRender(level, cursor);
            cursor.set(pos.getX(), pos.getY() + (solid ? 1 : 0), pos.getZ());
            float temp = TemperatureCalculator.calculatePointTemp(level, cursor, sources);
            field.put(pos, temp);
        }
    }

    /**
     * Рядом ли с этой клеткой уже развёрнутая (её зона уже посчитана).
     */
    private static boolean isExpandedNear(BlockPos cell) {
        return expandedCells.contains(cell)
                || expandedCells.contains(cell.above())
                || expandedCells.contains(cell.below())
                || expandedCells.contains(cell.north())
                || expandedCells.contains(cell.south())
                || expandedCells.contains(cell.east())
                || expandedCells.contains(cell.west());
    }

    /**
     * Полный сброс состояния (выключение отладки, смена мира).
     */
    private static void reset() {
        field.clear();
        faceMasks.clear();
        chunkSources.clear();
        scanQueue.clear();
        activeSources.clear();
        pendingCells.clear();
        pendingPoints.clear();
        candidates.clear();
        expandedCells.clear();
        stalePoints.clear();
        publishedSources = List.of();
        sourcesSignature = 0;
        errorCount = 0;
        lastRefreshTick = Integer.MIN_VALUE;
        lastRescanTick = Integer.MIN_VALUE;
        lastRebuildTick = Integer.MIN_VALUE;
        cachedLevel = null;
    }
}
