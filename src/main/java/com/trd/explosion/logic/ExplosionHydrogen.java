package com.trd.explosion.logic;

import com.trd.block.basic.CraterBasaltBlock;
import com.trd.block.basic.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Водородный взрыв гранаты: «яйцевидная» ударная волна вокруг точки детонации.
 * В бока волна уходит на {@code BLAST_RADIUS}, ВВЕРХ вытянута на {@code BLAST_UP_STRETCH}
 * (+30%), ВНИЗ приплюснута на {@code BLAST_DOWN_SQUASH} (≈1/3 глубины). Так воронка
 * остаётся знаковой и заметной, но волна не «закапывается» вниз и захватывает рельеф
 * со всех сторон: склон, гору, низину, потолок.
 *
 * <p>Алгоритм:
 * <ol>
 *     <li>Целевая область — вытянутый по вертикали объём (верх +30%, низ до 1/3) вокруг
 *         эпицентра с лёгким детерминированным «дрожанием» границы (3D-шум), поэтому край
 *         кратера живой, а не идеально гладкая математическая фигура.</li>
 *     <li>Для КАЖДОЙ целевой ячейки пускается луч от эпицентра к её центру (3D-DDA).
 *         Луч глушится барьером (бедрок) либо исчерпанием энергии (учитывается прочность
 *         пробиваемых блоков). Ячейка выжигается только если луч до неё «долетел»:
 *         укрытия из прочных блоков работают, но волна честно расширяется во все стороны.</li>
*     <li>Ячейки выжигаются батчами по тикам. В мягкий базальт с градиентом затемнения
     *         запекáются ИСКЛЮЧИТЕЛЬНО твёрдые цельные блоки; слабые (листва, трава) сносятся;
     *         жидкости не трогаются вообще. Твердь за пределами воронки печётся сквозь воздух
     *         на глубину до {@link #BASALT_EDGE_AIR_RANGE} пустых клеток (пол пещеры под дном
     *         тоже покрывается базальтом). Урон мобам — только по достигнутой лучами зоне,
     *         тип урона {@code trd:cremated}.</li>
 * </ol>
 */
public class ExplosionHydrogen {

    // ========== СФЕРА ВЗРЫВА ==========
    /** Горизонтальный радиус ударной волны (вбок). */
    public static float BLAST_RADIUS = 16.0f;
    /** Во сколько раз волна вытянута ВВЕРХ (1.3 = на 30% выше эпицентра). */
    public static float BLAST_UP_STRETCH = 1.3f;
    /** Во сколько раз волна приплюснута ВНИЗ (0.33 ≈ 1/3 глубины). */
    public static float BLAST_DOWN_SQUASH = 0.33f;
    /** Амплитуда неровности границы сферы (м): чтобы край был слегка «живым». */
    public static float BLAST_JITTER = 0.9f;
    /** Частота шума границы: меньше — крупные «волны», больше — мелкий крап. */
    public static float BLAST_NOISE_SCALE = 0.22f;

    // ========== ЭНЕРГИЯ ЛУЧА ==========
    /**
     * Энергия луча: расходуется на каждый пройденный блок (воздух = 1, блок = прочность).
     * Когда кончается — луч гаснет, дальше блоки не выжигаются (защита укрытиями).
     */
    public static float BLAST_BUDGET = 70.0f;

    // ========== УРОН ==========
    /** Максимальный урон в эпицентре (крепкий моб ~12 хп * 10 = 60 хп). */
    public static float MAX_ENTITY_DAMAGE = 120f;
    /** Минимальный урон на краю зоны поражения. */
    public static float MIN_ENTITY_DAMAGE = 25f;
    /** Секунды поджога после взрыва. */
    public static int FIRE_SECONDS = 8;

    // ========== БАЗАЛЬТОВОЕ ПРИПЕКАНИЕ ==========
    public static int BASALT_JOBS_PER_TICK = 12000;
    public static int BASALT_QUEUE_CAP = 200000;
    /** Блоки с прочностью не выше этой не пекутся в базальт, а просто уничтожаются. */
    public static float WEAK_BLOCK_HARDNESS = 0.4f;
    /** Максимум пустых клеток между кратером и твёрдым блоком, который тоже запекётся в базальт. */
    public static int BASALT_EDGE_AIR_RANGE = 5;
    /** Радиус, на котором базальт достигает максимального осветления (граница кратера). */
    public static float GRADIENT_RADIUS = 24.0f;
    /** Радиус эпицентра с чистым basalt_soft (увеличен на 4 блока относительно исходного). */
    public static float SOFT_CORE_RADIUS = 6.0f;

    /** Сколько блоков очищать за тик (безопасно для TPS на больших взрывах). */
    public static int BLOCKS_PER_TICK = 6000;

    /** Кастомный тип урона — кремация. Сообщение о смерти из death.attack.cremated. */
    public static final ResourceKey<DamageType> CREMATION =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("trd", "cremated"));

    private record BasaltJob(BlockPos pos, Vec3 center, boolean destroy) {}

    /** Шаг обхода {@link #enqueueScorchedEdges}: пустая ячейка и сколько воздуха она от воронки. */
    private record Flood(BlockPos pos, int depth) {}

    private static final Deque<BasaltJob> BASALT_QUEUE = new ArrayDeque<>();

    /** Опорная точка градиента осветления: центральный нижний блок дна кратера. */
    private static BlockPos GRADIENT_ANCHOR;

    /** Результат построения кратера: ячейки на очистку и зона, достигнутая лучами. */
    private record CarveData(List<BlockPos> carve, Set<Long> zone) {}

    /** Состояние одного взрыва, переносимое между батчами очистки блоков. */
    private static final class CarveState {
        final ServerLevel level;
        final Vec3 center;
        final List<BlockPos> clear;
        int ptr;

        CarveState(ServerLevel level, Vec3 center, List<BlockPos> clear) {
            this.level = level;
            this.center = center;
            this.clear = clear;
        }
    }

    public static void explode(ServerLevel level, Vec3 center, Entity source) {
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                6.0F, 0.4F);

        long seed = level.getSeed();
        GRADIENT_ANCHOR = findCraterFloor(level, center);

        discardItemsNearby(level, center, BLAST_RADIUS * BLAST_UP_STRETCH + 2.0f);

        // 1) Выясняем, до каких ячеек шара долетают лучи (не огибая препятствия).
        CarveData data = buildCarveData(level, center, seed);
        CarveState state = new CarveState(level, center, data.carve());

        // 2) Урон мобам наносим сразу — только по достигнутому лучами объёму.
        applyDamage(level, center, source, data.zone());

        // 3) Очистка блоков батчами по тикам.
        if (data.carve().isEmpty()) {
            finishBlast(level, center, data.carve());
        } else {
            level.getServer().tell(new net.minecraft.server.TickTask(0, () -> runCarveBatch(state)));
        }
    }

    // ==================== ПОСТРОЕНИЕ КРАТЕРА ====================

    /**
     * Перебираем все ячейки шара радиуса BLAST_RADIUS вокруг эпицентра и выжигаем только те,
     * до которых долетел прямой луч от эпицентра (3D-DDA с расходом энергии на прочность).
     */
    private static CarveData buildCarveData(ServerLevel level, Vec3 center, long seed) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int cx = (int) Math.floor(center.x);
        int cy = (int) Math.floor(center.y);
        int cz = (int) Math.floor(center.z);
        int rXZ = (int) Math.ceil(BLAST_RADIUS);
        int rYUp = (int) Math.ceil(BLAST_RADIUS * BLAST_UP_STRETCH);
        int rYDown = (int) Math.ceil(BLAST_RADIUS * BLAST_DOWN_SQUASH);

        List<BlockPos> carve = new ArrayList<>();
        Set<Long> zone = new HashSet<>();

        for (int x = cx - rXZ; x <= cx + rXZ; x++) {
            for (int y = Math.max(cy - rYDown, minY); y <= Math.min(cy + rYUp, maxY - 1); y++) {
                for (int z = cz - rXZ; z <= cz + rXZ; z++) {
                    if (!isInBlastRegion(center, seed, x, y, z)) continue;
                    if (!rayReaches(level, center, x, y, z, BLAST_BUDGET)) continue;

                    BlockPos pos = new BlockPos(x, y, z);
                    carve.add(pos);
                    zone.add(pos.asLong());
                }
            }
        }
        return new CarveData(carve, zone);
    }

    /** Целевая область взрыва: точка в «яйцевидном» объёме — верх вытянут, низ приплюснут. */
    private static boolean isInBlastRegion(Vec3 center, long seed, int x, int y, int z) {
        double dx = (x + 0.5) - center.x;
        double dy = (y + 0.5) - center.y;
        double dz = (z + 0.5) - center.z;

        double vRadius = dy >= 0
                ? BLAST_RADIUS * BLAST_UP_STRETCH
                : BLAST_RADIUS * BLAST_DOWN_SQUASH;

        double horizontal = (dx * dx + dz * dz) / (BLAST_RADIUS * BLAST_RADIUS);
        double vertical = (dy * dy) / (vRadius * vRadius);
        double jitter = (blastJitter(seed, x, y, z) * BLAST_JITTER) / BLAST_RADIUS;
        return horizontal + vertical + jitter <= 1.0;
    }

    /**
     * Трассировка луча от эпицентра к центру ячейки (3D-DDA, Аманатидес–Ву).
     * Луч глушится первым барьером (бедрок/базальт) или исчерпанием энергии.
     */
    private static boolean rayReaches(ServerLevel level, Vec3 center,
                                      int tx, int ty, int tz, float budget) {
        int px = (int) Math.floor(center.x);
        int py = (int) Math.floor(center.y);
        int pz = (int) Math.floor(center.z);

        double dx = (tx + 0.5) - center.x;
        double dy = (ty + 0.5) - center.y;
        double dz = (tz + 0.5) - center.z;

        int stepX = (int) Math.signum(dx);
        int stepY = (int) Math.signum(dy);
        int stepZ = (int) Math.signum(dz);

        double tDeltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);

        double tMaxX = dx == 0 ? Double.POSITIVE_INFINITY : (dx > 0 ? (px + 1 - center.x) : (center.x - px)) * tDeltaX;
        double tMaxY = dy == 0 ? Double.POSITIVE_INFINITY : (dy > 0 ? (py + 1 - center.y) : (center.y - py)) * tDeltaY;
        double tMaxZ = dz == 0 ? Double.POSITIVE_INFINITY : (dz > 0 ? (pz + 1 - center.z) : (center.z - pz)) * tDeltaZ;

        float spent = 0.0f;

        for (int guard = 0; guard < 1024; guard++) {
            BlockPos pos = new BlockPos(px, py, pz);
            BlockState s = level.getBlockState(pos);
            if (isBarrier(level, s, pos)) return false;

            float cost = blockCost(s, level, pos);
            if (!Float.isFinite(cost)) return false;
            spent += cost;
            if (spent > budget) return false;

            if (px == tx && py == ty && pz == tz) return true;

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    px += stepX;
                    tMaxX += tDeltaX;
                } else {
                    pz += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else if (tMaxY < tMaxZ) {
                py += stepY;
                tMaxY += tDeltaY;
            } else {
                pz += stepZ;
                tMaxZ += tDeltaZ;
            }
        }
        return false;
    }

    private static void runCarveBatch(CarveState state) {
        ServerLevel level = state.level;
        int end = Math.min(state.ptr + BLOCKS_PER_TICK, state.clear.size());
        for (int i = state.ptr; i < end; i++) {
            clearBlockAndEnqueue(level, state.clear.get(i));
        }
        state.ptr = end;

        if (state.ptr < state.clear.size()) {
            level.getServer().tell(new net.minecraft.server.TickTask(1, () -> runCarveBatch(state)));
        } else {
            finishBlast(level, state.center, state.clear);
        }
    }

    private static void clearBlockAndEnqueue(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return;
        if (!s.getFluidState().isEmpty()) return; // жидкости не выжигаем и не пекём
        float cost = blockCost(s, level, pos);
        if (!Float.isFinite(cost)) return; // непроницаемые блоки (бедрок и т.п.) не трогаем
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    /**
     * Цена прохода через ячейку: воздух — 1 (шаг луча), блок — его прочность (минимум 1).
     * Бедрок и прочие нексрушимые отмечены бесконечностью — луч через них не идёт.
     */
    private static float blockCost(BlockState state, ServerLevel level, BlockPos pos) {
        if (state.isAir()) return 1.0f;
        if (state.getFluidState().isSource() || state.getBlock() instanceof LiquidBlock) return 0.6f;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return Float.POSITIVE_INFINITY;
        return Math.max(1.0f, hardness);
    }

    /** Непроницаемые барьеры луча: запёкшийся мягкий базальт и нексрушимые блоки. */
    private static boolean isBarrier(ServerLevel level, BlockState state, BlockPos pos) {
        if (isBakedBasalt(state)) return true;
        return state.getDestroySpeed(level, pos) < 0;
    }

    // ==================== УРОН МОБАМ ====================

    /** После полной очистки блоков: обновить градиентный якорь и подчистить остатки. */
    private static void finishBlast(ServerLevel level, Vec3 center, List<BlockPos> carved) {
        GRADIENT_ANCHOR = findCraterFloor(level, center);
        enqueueScorchedEdges(level, center, carved);
        drainBasaltJobs(level);
        discardItemsNearby(level, center, BLAST_RADIUS * BLAST_UP_STRETCH + 2.0f);
    }

    private static void applyDamage(ServerLevel level, Vec3 center, Entity source, Set<Long> zone) {
        float zoneRadius = BLAST_RADIUS * BLAST_UP_STRETCH;
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(zoneRadius + 3.0));

        DamageSource damageSource = cremationSource(level, source);

        for (LivingEntity entity : entities) {
            if (entity == source || !entity.isAlive()) continue;
            if (!entityInBlast(zone, entity)) continue;

            double d = entity.distanceToSqr(center);
            double t = Math.sqrt(d) / zoneRadius;
            t = Math.max(0.0, Math.min(1.0, t));
            float damage = MAX_ENTITY_DAMAGE + (MIN_ENTITY_DAMAGE - MAX_ENTITY_DAMAGE) * (float) t;

            entity.hurt(damageSource, damage);
            entity.setSecondsOnFire(FIRE_SECONDS);
        }
    }

    /** Точки хитбокса: сущность поражена, если хотя бы одна лежит в достигнутой лучами зоне. */
    private static boolean entityInBlast(Set<Long> zone, LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        double cx = (box.minX + box.maxX) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double h = box.maxY - box.minY;

        if (pointInZone(zone, cx, box.minY + h * 0.15, cz)) return true;
        if (pointInZone(zone, cx, box.minY + h * 0.5, cz)) return true;
        if (pointInZone(zone, cx, box.maxY - 0.1, cz)) return true;
        if (pointInZone(zone, box.minX, box.minY + h * 0.05, box.minZ)) return true;
        if (pointInZone(zone, box.maxX, box.minY + h * 0.05, box.minZ)) return true;
        if (pointInZone(zone, box.minX, box.minY + h * 0.05, box.maxZ)) return true;
        return pointInZone(zone, box.maxX, box.minY + h * 0.05, box.maxZ);
    }

    /** Точка в зоне, если её блок (или блок под ней) достигнут лучом. */
    private static boolean pointInZone(Set<Long> zone, double px, double py, double pz) {
        int by = (int) Math.floor(py);
        long key = BlockPos.asLong((int) Math.floor(px), by, (int) Math.floor(pz));
        if (zone.contains(key)) return true;
        return zone.contains(BlockPos.asLong((int) Math.floor(px), by - 1, (int) Math.floor(pz)));
    }

    private static DamageSource cremationSource(ServerLevel level, Entity source) {
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolder(CREMATION).orElse(null);
        if (holder != null) {
            return new DamageSource(holder, source, source);
        }
        return level.damageSources().explosion(source, source);
    }

    // ==================== БАЗАЛЬТОВОЕ ПРИПЕКАНИЕ ====================

    /**
     * Запекание ЗА пределами выжженного объёма. BFS от всех выжженных (теперь пустых) ячеек
     * сквозь воздух на глубину до {@code BASALT_EDGE_AIR_RANGE-1}. Твёрдые блоки в зоне
     * досягаемости прохода печётся в базальт: так пол пещеры под дном воронки или уступ
     * за её краем всё равно покрываются слоем базальта.
     */
    private static void enqueueScorchedEdges(ServerLevel level, Vec3 center, List<BlockPos> carved) {
        if (BASALT_QUEUE.size() >= BASALT_QUEUE_CAP) return;

        Deque<Flood> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        for (BlockPos p : carved) {
            if (visited.add(p.asLong())) queue.addLast(new Flood(p, 0));
        }

        while (!queue.isEmpty()) {
            if (visited.size() >= BASALT_QUEUE_CAP) break;
            Flood f = queue.pollFirst();
            for (Direction d : Direction.values()) {
                BlockPos nb = f.pos().relative(d);
                BlockState ns = level.getBlockState(nb);
                if (ns.isAir()) {
                    if (f.depth() + 1 < BASALT_EDGE_AIR_RANGE && visited.add(nb.asLong())) {
                        queue.addLast(new Flood(nb, f.depth() + 1));
                    }
                } else {
                    enqueueEdgeTarget(level, center, nb, ns);
                }
            }
        }
    }

    /** Классификация найденного за краем блока:
     * - жидкости пропускаются (не уничтожаются и не пекутся);
     * - крепкие ЦЕЛЬНЫЕ твёрдые блоки запекутся в мягкий базальт;
     * - блоки с крайне низкой прочностью (листва, цветы, трава…) просто уничтожатся;
     * - крепкие, но нецельные блоки (плиты, заборы…) не трогаются. */
    private static void enqueueEdgeTarget(ServerLevel level, Vec3 center, BlockPos pos, BlockState state) {
        if (BASALT_QUEUE.size() >= BASALT_QUEUE_CAP) return;
        if (!state.getFluidState().isEmpty() || state.getBlock() instanceof LiquidBlock) return; // жидкости
        if (isBakedBasalt(state)) return;

        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return; // нексрушимые (бедрок и т.п.)

        boolean weak = hardness <= WEAK_BLOCK_HARDNESS;
        if (!weak && !state.isCollisionShapeFullBlock(level, pos)) return; // базальт — ЛИШЬ цельные
        BASALT_QUEUE.addLast(new BasaltJob(pos, center, weak));
    }

    /** Применяет накопленные базальтовые заготовки батчами по BASALT_JOBS_PER_TICK за тик. */
    private static void drainBasaltJobs(ServerLevel level) {
        int processed = 0;
        while (!BASALT_QUEUE.isEmpty() && processed < BASALT_JOBS_PER_TICK) {
            BasaltJob job = BASALT_QUEUE.pollFirst();
            if (job == null) break;
            processed++;

            BlockState s = level.getBlockState(job.pos());
            if (s.isAir()) continue;
            if (isBakedBasalt(s)) continue;

            if (job.destroy()) {
                level.setBlock(job.pos(), Blocks.AIR.defaultBlockState(), 3);
            } else {
                int dark = darknessLevel(job.pos());
                level.setBlock(job.pos(),
                        pickSoftBasalt(job.pos()).defaultBlockState()
                                .setValue(CraterBasaltBlock.DARKNESS, dark), 3);
            }
        }
        if (!BASALT_QUEUE.isEmpty()) {
            level.getServer().tell(new net.minecraft.server.TickTask(1, () -> drainBasaltJobs(level)));
        }
    }

    /**
     * Текстура мягкого базальта: эпицентр (в радиусе SOFT_CORE_RADIUS) — чистый basalt_soft
     * (он выделяется по цвету, поэтому только в эпицентре), остальное — «крап» из
     * basalt_soft_2 и basalt_soft_3.
     */
    private static Block pickSoftBasalt(BlockPos pos) {
        BlockPos anchor = GRADIENT_ANCHOR;
        if (anchor == null) anchor = pos; // запасной вариант, если якорь ещё не найден
        double dx = pos.getX() - anchor.getX();
        double dz = pos.getZ() - anchor.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= SOFT_CORE_RADIUS) return ModBlocks.BASALT_SOFT.get();
        return softBasaltNoise(pos) ? ModBlocks.BASALT_SOFT_2.get() : ModBlocks.BASALT_SOFT_3.get();
    }

    /** Детерминированный «крап» basalt_soft_2 / basalt_soft_3 по хешу ячейки (не полосатый). */
    private static boolean softBasaltNoise(BlockPos pos) {
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h & 1) == 0;
    }

    /**
     * Ступень затемнения мягкого базальта по расстоянию от центрального нижнего блока
     * дна кратера: у эпицентра максимум (MAX_DARK, +50% темноты), к ободу — 0 (обычная
     * текстура). Окрашивание делается цветовым тинтом в коде, без дубликатов текстур.
     */
    private static int darknessLevel(BlockPos pos) {
        BlockPos anchor = GRADIENT_ANCHOR;
        if (anchor == null) {
            anchor = pos; // запасной вариант, если якорь ещё не найден
        }
        double dx = pos.getX() - anchor.getX();
        double dy = pos.getY() - anchor.getY();
        double dz = pos.getZ() - anchor.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double t = Math.min(1.0, dist / GRADIENT_RADIUS);
        return (int) Math.round((1.0 - t) * CraterBasaltBlock.MAX_DARK);
    }

    /** Уже запёкшиеся базальтовые блоки (их вторично не обрабатываем). */
    private static boolean isBakedBasalt(BlockState state) {
        return state.is(ModBlocks.BASALT_SCORCHED.get())
                || state.is(ModBlocks.BASALT_ROUGH.get())
                || state.is(ModBlocks.BASALT_SOFT.get())
                || state.is(ModBlocks.BASALT_SOFT_2.get())
                || state.is(ModBlocks.BASALT_SOFT_3.get());
    }

    // ==================== УТИЛИТЫ ====================

    private static void discardItemsNearby(ServerLevel level, Vec3 center, float radius) {
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(center, center).inflate(radius));
        for (ItemEntity item : items) {
            item.discard();
        }
    }

    /** Ищет дно кратера: первый цельный непустой блок в колонне под эпицентром. */
    private static BlockPos findCraterFloor(ServerLevel level, Vec3 center) {
        int x = (int) Math.floor(center.x);
        int z = (int) Math.floor(center.z);
        for (int y = (int) Math.floor(center.y); y >= level.getMinBuildHeight(); y--) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            if (s.getFluidState().isSource()) continue;
            return p;
        }
        return new BlockPos(x, (int) Math.floor(center.y), z);
    }

    /**
     * Плавный value-noise в 3D ([-1..1]): лёгкая неровность границы сферы,
     * делает край кратера «живым», а не идеально гладким.
     */
    private static double blastJitter(long seed, int x, int y, int z) {
        double sx = x * BLAST_NOISE_SCALE;
        double sy = y * BLAST_NOISE_SCALE;
        double sz = z * BLAST_NOISE_SCALE;
        int x0 = (int) Math.floor(sx);
        int y0 = (int) Math.floor(sy);
        int z0 = (int) Math.floor(sz);
        double fx = sx - x0;
        double fy = sy - y0;
        double fz = sz - z0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);

        double n000 = hashNoise3(seed, x0, y0, z0);
        double n100 = hashNoise3(seed, x0 + 1, y0, z0);
        double n010 = hashNoise3(seed, x0, y0 + 1, z0);
        double n110 = hashNoise3(seed, x0 + 1, y0 + 1, z0);
        double n001 = hashNoise3(seed, x0, y0, z0 + 1);
        double n101 = hashNoise3(seed, x0 + 1, y0, z0 + 1);
        double n011 = hashNoise3(seed, x0, y0 + 1, z0 + 1);
        double n111 = hashNoise3(seed, x0 + 1, y0 + 1, z0 + 1);

        double x00 = n000 + (n100 - n000) * fx;
        double x10 = n010 + (n110 - n010) * fx;
        double x01 = n001 + (n101 - n001) * fx;
        double x11 = n011 + (n111 - n011) * fx;
        double y0v = x00 + (x10 - x00) * fy;
        double y1v = x01 + (x11 - x01) * fy;
        return y0v + (y1v - y0v) * fz;
    }

    private static double hashNoise3(long seed, int x, int y, int z) {
        long h = x * 374761393L + y * 668265263L + z * 1442695040888963407L + seed * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h = h ^ (h >>> 16);
        return ((h & 0xFFFF) / 65535.0) * 2.0 - 1.0;
    }
}