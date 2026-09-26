package com.trd.explosion.logic;

import com.trd.block.basic.CraterBasaltBlock;
import com.trd.block.basic.ModBlocks;
import com.trd.block.basic.WasteGrassBlock;
import com.trd.explosion.data.CraterTintData;
import com.trd.explosion.data.CraterTintSync;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Огненный взрыв (зажигательная граната, огненная осколочная, огненная ракета) — работает
 * по той же схеме распространения, что и водородный: от эпицентра к каждой цели бьётся
 * 3D-DDA луч, и эффект есть только там, куда луч дотянулся в пределах бюджета пробития.
 *
 * <p>Правила прохождения луча (строго):
 * <ul>
 *     <li><b>несгораемый блок</b> (в т.ч. любые жидкости) — <b>полностью обрывает</b> луч: за ним
 *         ни урона, ни поджога, ни огня; блок не трогается;</li>
 *     <li><b>движение вперёд</b> по воздуху — {@link #AIR_COST} пробития на клетку;</li>
 *     <li><b>горящий блок на пути</b> — «сгорает» насквозь и тратит
 *         {@code 1 + прочность·{@link #BURN_COST_K}} пробития;</li>
 *     <li><b>замена самого блока</b> — дороже прохода насквозь:
 *         {@code 1 + прочность·{@link #REPLACE_COST_K}}, и она тоже должна влезть в
 *         оставшийся бюджет — иначе блок остаётся нетронутым.</li>
 * </ul>
 *
 * <p>Действия с блоками копируют водородный взрыв: доски → {@code waste_planks},
 * брёвна → {@code waste_log} (с сохранением оси), дёрн/подзол/мицелий → {@code waste_grass},
 * деревянные ступени/полублоки → обугленные аналоги. Уничтожается лишь то, что не
 * заменяется, но держит прочность не выше {@link #DESTROY_HARDNESS} — всё остальное
 * горючее просто поджигается ванильным огнём и догорает штатно. Водой/каменной стеной
 * волна не проходит.
 *
 * <p>В эпицентре поверх блоков накладывается копоть-тинт того же формата, что у воронки
 * водородного взрыва (сфера {@link #TINT_RADIUS}), персистентный в {@link CraterTintData}.
 *
 * <p>Вся работа размазана по тикам адаптивным бюджетом времени, невыгруженные чанки
 * не подгружаются принудительно.
 */
public class ExplosionFire {

    /** Максимальная дальность огненной волны от эпицентра. */
    public static float WAVE_RANGE = 20.0f;

    /** Бюджет пробития луча в единицах стоимости. */
    public static float RAY_BUDGET = 15.0f;
    /** Стоимость шага лучом по воздуху. */
    public static float AIR_COST = 1.0f;
    /** Стоимость прохода насквозь горящего блока: {@code 1 + прочность·k}. */
    public static float BURN_COST_K = 0.5f;
    /** Стоимость замены/уничтожения самого блока: {@code 1 + прочность·k} (дороже прохода). */
    public static float REPLACE_COST_K = 1.5f;
    /** Прочность блока, до которой включительно он уничтожается, а не заменяется. */
    public static float DESTROY_HARDNESS = 0.4f;

    public static float MAX_DAMAGE = 30.0f;
    public static float MIN_DAMAGE = 6.0f;
    public static int MAX_FIRE_SECONDS = 15;
    /** Сколько пробития тратится на один «шаг» падения урона. */
    public static float RESIST_PER_STEP = 5.0f;
    /** Сколько урона теряется за один шаг. */
    public static float STEP_DAMAGE_DROP = 0.10f;

    /** Радиус сферы копоть-тинта в эпицентре. */
    public static float TINT_RADIUS = 10.0f;
    /** Тинт писать только для блоков с открытой гранью (видимая поверхность), а не всего объёма. */
    public static boolean TINT_ONLY_EXPOSED = true;
    public static int TINT_PER_TICK = 1500;
    public static int TINT_PACKET_CHUNK = 1200;

    /** До этого радиуса плотность огня максимальная, дальше линейно падает до нуля. */
    public static float FIRE_FADE_START = 1.0f;
    public static float FIRE_BASE_DENSITY = 0.9f;
    public static float FIRE_NOISE_CENTER = 0.0f;
    public static float FIRE_NOISE_EDGE = 1.0f;
    public static float FIRE_THRESHOLD = 0.5f;

    public static long DEFAULT_TICK_BUDGET_NANOS = 3_000_000L;
    public static long MIN_TICK_BUDGET_NANOS = 400_000L;
    public static long MAX_TICK_BUDGET_NANOS = 8_000_000L;
    public static long SLOW_GAP_NANOS = 58_000_000L;
    public static long FAST_GAP_NANOS = 40_000_000L;
    public static long IDLE_GAP_NANOS = 250_000_000L;
    public static int MAX_QUEUED_EXPLOSIONS = 32;

    private static final int MAX_RAY_STEPS = 1024;

    private static final ArrayDeque<State> QUEUE = new ArrayDeque<>();
    private static boolean DRAIN_PENDING = false;

    private static long tickBudgetNanos = DEFAULT_TICK_BUDGET_NANOS;
    private static long lastDrainNanos = System.nanoTime();

    private enum Phase { PRELOAD, SCAN, DAMAGE, APPLY, TINT, FINISH }

    private static final class EntityTarget {
        final LivingEntity entity;
        final float baseDamage;
        final int baseFire;

        EntityTarget(LivingEntity entity, float baseDamage, int baseFire) {
            this.entity = entity;
            this.baseDamage = baseDamage;
            this.baseFire = baseFire;
        }
    }

    private static final class State {
        final ServerLevel level;
        final Vec3 center;
        final DamageSource damageSource;
        final Entity sourceEntity;

        final float waveRadius;
        final float coreRadius;
        final double waveSq;

        final int minX, maxX, minY, maxY, minZ, maxZ;
        int scanX, scanY, scanZ;
        int plX, plZ;

        final int tMinX, tMaxX, tMinY, tMaxY, tMinZ, tMaxZ;
        final double tintSq;

        Phase phase = Phase.SCAN;

        final LongArrayList fire = new LongArrayList();
        final LongArrayList replaceLog = new LongArrayList();
        final LongArrayList replaceGrass = new LongArrayList();
        final LongArrayList replacePlanks = new LongArrayList();
        final LongArrayList replaceStairs = new LongArrayList();
        final LongArrayList replaceSlabs = new LongArrayList();
        final LongArrayList destroy = new LongArrayList();
        final List<EntityTarget> entities = new ArrayList<>();

        int applyFire;
        int applyLog;
        int applyGrass;
        int applyPlanks;
        int applyStairs;
        int applySlabs;
        int applyDestroy;
        int applyEntity;

        /** Кеш DDA-лучей по целевой ячейке: бит достижимости + пробитие (без клетки цели). */
        final Long2LongOpenHashMap rayCache = new Long2LongOpenHashMap();

        private static final long RAY_REACHED = 1L;
        private static final int RAY_COST_SHIFT = 1;

        final Long2IntOpenHashMap tintMap = new Long2IntOpenHashMap();
        boolean tintPrepared;
        List<LongArrayList> tintRings = new ArrayList<>();
        int tintSendRing;
        int tintSendOff;

        final long seed;

        State(ServerLevel level, Vec3 center, Entity source, DamageSource damageSource, float coreRadius) {
            this.level = level;
            this.center = center;
            this.damageSource = damageSource;
            this.sourceEntity = source;
            this.waveRadius = WAVE_RANGE;
            this.waveSq = (double) waveRadius * waveRadius;
            this.coreRadius = Math.max(0.5f, coreRadius);
            this.seed = level.getSeed();

            int cx = (int) Math.floor(center.x);
            int cy = (int) Math.floor(center.y);
            int cz = (int) Math.floor(center.z);
            int r = (int) Math.ceil(waveRadius);

            minX = cx - r;
            maxX = cx + r;
            minY = Math.max(level.getMinBuildHeight(), cy - r);
            maxY = Math.min(level.getMaxBuildHeight() - 1, cy + r);
            minZ = cz - r;
            maxZ = cz + r;
            scanX = minX;
            scanY = minY;
            scanZ = minZ;
            plX = minX >> 4;
            plZ = minZ >> 4;

            // Отдельный бокс тинта: общий с зоной волны стёр бы копоть водородного взрыва рядом.
            int tr = (int) Math.ceil(TINT_RADIUS);
            tMinX = cx - tr;
            tMaxX = cx + tr;
            tMinY = Math.max(level.getMinBuildHeight(), cy - tr);
            tMaxY = Math.min(level.getMaxBuildHeight() - 1, cy + tr);
            tMinZ = cz - tr;
            tMaxZ = cz + tr;
            tintSq = (double) TINT_RADIUS * TINT_RADIUS;
        }

        boolean work(long deadline) {
            while (true) {
                switch (phase) {
                    case PRELOAD -> {
                        if (!preload(deadline)) return false;
                        phase = Phase.SCAN;
                    }
                    case SCAN -> {
                        if (!scan(deadline)) return false;
                        phase = Phase.DAMAGE;
                    }
                    case DAMAGE -> {
                        if (!damage(deadline)) return false;
                        rayCache.clear();
                        phase = Phase.APPLY;
                    }
                    case APPLY -> {
                        if (!apply(deadline)) return false;
                        phase = Phase.TINT;
                    }
                    case TINT -> {
                        if (!sendTint(deadline)) return false;
                        phase = Phase.FINISH;
                    }
                    case FINISH -> {
                        return true;
                    }
                }
            }
        }

        /**
         * Предзагрузка чанков, пересекающих зону волны. Без неё {@code processCell} пропускал
         * ячейки незагруженных чанков целиком, вместе с записью тинта, — в месте взрыва оставалась
         * дыра размером в чанк, которая уже не заполнялась (в {@code CraterTintData} эти позиции
         * тоже не сохранялись). Загрузка идёт бюджетными шагами и размазывается по тикам.
         */
        private boolean preload(long deadline) {
            int cx0 = minX >> 4, cz0 = minZ >> 4;
            int cx1 = maxX >> 4, cz1 = maxZ >> 4;
            while (plX <= cx1) {
                while (plZ <= cz1) {
                    if (System.nanoTime() > deadline) return false;
                    int cx = plX, cz = plZ++;
                    if (!level.hasChunk(cx, cz)) {
                        level.getChunk(cx, cz, ChunkStatus.FULL, true);
                    }
                }
                plX++;
                plZ = cz0;
            }
            return true;
        }

        private boolean scan(long deadline) {
            int x = scanX, y = scanY, z = scanZ;
            for (; x <= maxX; x++) {
                for (; y <= maxY; y++) {
                    for (; z <= maxZ; z++) {
                        if (System.nanoTime() > deadline) {
                            scanX = x;
                            scanY = y;
                            scanZ = z;
                            return false;
                        }
                        processCell(x, y, z);
                    }
                    z = minZ;
                }
                y = minY;
            }
            return true;
        }

        private void processCell(int x, int y, int z) {
            double dx = x + 0.5 - center.x;
            double dy = y + 0.5 - center.y;
            double dz = z + 0.5 - center.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > waveSq) return;

            BlockPos pos = new BlockPos(x, y, z);
            BlockState s = level.getBlockState(pos);

            if (s.isAir()) {
                tryFire(pos, d2);
                return;
            }
            if (s.is(Blocks.FIRE)) return;
            if (!s.getFluidState().isEmpty() || s.getBlock() instanceof LiquidBlock) return;

            float hardness = s.getDestroySpeed(level, pos);
            if (hardness < 0) return;

            // Позиционный копоть-тинт: виден только в эпицентре (сфера TINT_RADIUS).
            if (d2 <= tintSq
                    && !(s.getBlock() instanceof CraterBasaltBlock)
                    && !(s.getBlock() instanceof WasteGrassBlock)) {
                if (!TINT_ONLY_EXPOSED || isSurfaceExposed(x, y, z)) {
                    tintMap.put(pos.asLong(), tintDarkness(Math.sqrt(d2)));
                }
            }

            // Дальше по блокам волна работает только с горючим: несгораемое луч уже обрубил.
            if (!s.isFlammable(level, pos, Direction.UP)) return;
            if (isWaste(s)) return;

            long packed = queryRay(x + 0.5, y + 0.5, z + 0.5);
            if ((packed & RAY_REACHED) == 0) return;
            if (rayCost(packed) + replaceCost(hardness) > RAY_BUDGET) return;

            long lp = pos.asLong();
            if (isLog(s, level, pos)) {
                replaceLog.add(lp);
            } else if (isNaturalSoil(s)) {
                replaceGrass.add(lp);
            } else if (isWoodenStairs(s, level, pos)) {
                replaceStairs.add(lp);
            } else if (isWoodenSlab(s, level, pos)) {
                replaceSlabs.add(lp);
            } else if (isWoodPlanks(s, level, pos)) {
                replacePlanks.add(lp);
            } else if (hardness <= DESTROY_HARDNESS) {
                destroy.add(lp);
            }
        }

        private void tryFire(BlockPos pos, double d2) {
            BlockPos belowPos = pos.below();
            if (!level.hasChunk(belowPos.getX() >> 4, belowPos.getZ() >> 4)) return;
            BlockState below = level.getBlockState(belowPos);
            if (below.isAir() || !below.getFluidState().isEmpty()) return;

            boolean burnable = below.isFlammable(level, belowPos, Direction.UP);
            if (!burnable && !Blocks.FIRE.defaultBlockState().canSurvive(level, pos)) return;

            double t = fireFade(Math.sqrt(d2));
            double base = FIRE_BASE_DENSITY * (1.0 - t);
            double noise = FIRE_NOISE_CENTER + (FIRE_NOISE_EDGE - FIRE_NOISE_CENTER) * t;
            double p = base + (hash01(seed, pos.asLong()) - 0.5) * 2.0 * noise;
            if (p < FIRE_THRESHOLD) return;

            long packed = queryRay(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if ((packed & RAY_REACHED) == 0) return;
            if (rayCost(packed) + AIR_COST > RAY_BUDGET) return;

            fire.add(pos.asLong());
        }

        /** Линейное затухание плотности огня: 0 у эпицентра, 1 на краю зоны волны. */
        private double fireFade(double dist) {
            double fadeSpan = waveRadius - FIRE_FADE_START;
            if (fadeSpan <= 0.0) return 1.0;
            double t = (dist - FIRE_FADE_START) / fadeSpan;
            return Math.max(0.0, Math.min(1.0, t));
        }

        /**
         * Единый 3D-DDA от эпицентра к целевой ячейке. Несгораемый блок (в т.ч. жидкость)
         * обрывает луч, горячий блок и воздух тратят пробитие. Стартовая и целевая ячейки
         * из стоимости исключены — их цену платит вызывающий ({@link #replaceCost(float)}/
         * {@link #AIR_COST}). Результат кешируется по целевой ячейке в пределах одной фазы.
         */
        private long queryRay(double tx, double ty, double tz) {
            int ex = (int) Math.floor(tx);
            int ey = (int) Math.floor(ty);
            int ez = (int) Math.floor(tz);

            long target = BlockPos.asLong(ex, ey, ez);
            long cached = rayCache.get(target);
            if (cached != 0L || rayCache.containsKey(target)) return cached;

            double cx = center.x;
            double cy = center.y;
            double cz = center.z;

            int px = (int) Math.floor(cx);
            int py = (int) Math.floor(cy);
            int pz = (int) Math.floor(cz);

            double dx = tx - cx;
            double dy = ty - cy;
            double dz = tz - cz;

            int stepX = (int) Math.signum(dx);
            int stepY = (int) Math.signum(dy);
            int stepZ = (int) Math.signum(dz);

            double tDeltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
            double tDeltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
            double tDeltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);

            double tMaxX = dx == 0 ? Double.POSITIVE_INFINITY
                    : (stepX > 0 ? (px + 1 - cx) : (cx - px)) * tDeltaX;
            double tMaxY = dy == 0 ? Double.POSITIVE_INFINITY
                    : (stepY > 0 ? (py + 1 - cy) : (cy - py)) * tDeltaY;
            double tMaxZ = dz == 0 ? Double.POSITIVE_INFINITY
                    : (stepZ > 0 ? (pz + 1 - cz) : (cz - pz)) * tDeltaZ;

            boolean reached = true;
            float cost = 0.0f;
            boolean first = true;

            for (int guard = 0; guard < MAX_RAY_STEPS; guard++) {
                boolean isTarget = px == ex && py == ey && pz == ez;

                if (!first && !isTarget && level.hasChunk(px >> 4, pz >> 4)) {
                    BlockPos pos = new BlockPos(px, py, pz);
                    BlockState s = level.getBlockState(pos);
                    if (isWaveBarrier(s, level, pos)) {
                        reached = false;
                    } else {
                        cost += passCost(s, level, pos);
                        if (cost > RAY_BUDGET) reached = false;
                    }
                }

                if (!reached) break;
                if (isTarget) break;
                first = false;

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

            long packed = (reached ? RAY_REACHED : 0L)
                    | ((long) Float.floatToIntBits(cost) & 0xFFFFFFFFL) << RAY_COST_SHIFT;
            rayCache.put(target, packed);
            return packed;
        }

        private float rayCost(long packed) {
            return Float.intBitsToFloat((int) (packed >>> RAY_COST_SHIFT));
        }

        private boolean damage(long deadline) {
            for (; applyEntity < entities.size(); applyEntity++) {
                if (System.nanoTime() > deadline) return false;
                EntityTarget t = entities.get(applyEntity);
                LivingEntity e = t.entity;
                if (!e.isAlive()) continue;

                Vec3 c = e.getBoundingBox().getCenter();
                long packed = queryRay(c.x, c.y, c.z);
                if ((packed & RAY_REACHED) == 0) continue;

                double steps = rayCost(packed) / RESIST_PER_STEP;
                float mult = Math.max(0.0f, 1.0f - (float) (STEP_DAMAGE_DROP * Math.floor(steps)));
                float dmg = t.baseDamage * mult;
                if (dmg > 0) e.hurt(damageSource, dmg);
                if (t.baseFire > 0) e.setSecondsOnFire(t.baseFire);
            }
            return true;
        }

        private boolean apply(long deadline) {
            for (; applyLog < replaceLog.size(); applyLog++) {
                if (System.nanoTime() > deadline) return false;
                long l = replaceLog.getLong(applyLog);
                BlockPos pos = BlockPos.of(l);
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                BlockState cur = level.getBlockState(pos);
                if (!isLog(cur, level, pos)) continue;
                Direction.Axis axis = cur.hasProperty(RotatedPillarBlock.AXIS)
                        ? cur.getValue(RotatedPillarBlock.AXIS) : Direction.Axis.Y;
                level.setBlock(pos,
                        ModBlocks.WASTE_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis), 3);
            }
            for (; applyGrass < replaceGrass.size(); applyGrass++) {
                if (System.nanoTime() > deadline) return false;
                long l = replaceGrass.getLong(applyGrass);
                BlockPos pos = BlockPos.of(l);
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                BlockState cur = level.getBlockState(pos);
                if (!isNaturalSoil(cur)) continue;
                level.setBlock(pos, ModBlocks.WASTE_GRASS.get().defaultBlockState()
                        .setValue(CraterBasaltBlock.DARKNESS, tintDarkness(pos)), 3);
            }
            for (; applyPlanks < replacePlanks.size(); applyPlanks++) {
                if (System.nanoTime() > deadline) return false;
                long l = replacePlanks.getLong(applyPlanks);
                BlockPos pos = BlockPos.of(l);
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                BlockState cur = level.getBlockState(pos);
                if (!isWoodPlanks(cur, level, pos)) continue;
                level.setBlock(pos, ModBlocks.WASTE_PLANKS.get().defaultBlockState(), 3);
            }
            for (; applyStairs < replaceStairs.size(); applyStairs++) {
                if (System.nanoTime() > deadline) return false;
                long l = replaceStairs.getLong(applyStairs);
                BlockPos pos = BlockPos.of(l);
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                BlockState cur = level.getBlockState(pos);
                if (!isWoodenStairs(cur, level, pos)) continue;
                level.setBlock(pos, ModBlocks.WASTE_PLANKS_STAIRS.get().defaultBlockState()
                        .setValue(StairBlock.FACING, cur.getValue(StairBlock.FACING))
                        .setValue(StairBlock.HALF, cur.getValue(StairBlock.HALF))
                        .setValue(StairBlock.SHAPE, cur.getValue(StairBlock.SHAPE))
                        .setValue(StairBlock.WATERLOGGED, cur.getValue(StairBlock.WATERLOGGED)), 3);
            }
            for (; applySlabs < replaceSlabs.size(); applySlabs++) {
                if (System.nanoTime() > deadline) return false;
                long l = replaceSlabs.getLong(applySlabs);
                BlockPos pos = BlockPos.of(l);
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                BlockState cur = level.getBlockState(pos);
                if (!isWoodenSlab(cur, level, pos)) continue;
                level.setBlock(pos, ModBlocks.WASTE_PLANKS_SLAB.get().defaultBlockState()
                        .setValue(SlabBlock.TYPE, cur.getValue(SlabBlock.TYPE))
                        .setValue(SlabBlock.WATERLOGGED, cur.getValue(SlabBlock.WATERLOGGED)), 3);
            }
            for (; applyDestroy < destroy.size(); applyDestroy++) {
                if (System.nanoTime() > deadline) return false;
                long l = destroy.getLong(applyDestroy);
                BlockPos pos = BlockPos.of(l);
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                BlockState cur = level.getBlockState(pos);
                if (cur.isAir()) continue;
                float hardness = cur.getDestroySpeed(level, pos);
                if (hardness < 0 || hardness > DESTROY_HARDNESS) continue;
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            // Огонь в последнюю очередь: к этому моменту горючее уже обуглено/убрано.
            for (; applyFire < fire.size(); applyFire++) {
                if (System.nanoTime() > deadline) return false;
                long l = fire.getLong(applyFire);
                BlockPos pos = BlockPos.of(l);
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                if (!level.getBlockState(pos).isAir()) continue;
                level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 3);
            }
            return true;
        }

        // ==================== ТИНТ ====================

        private int tintDarkness(BlockPos pos) {
            return tintDarkness(distToCenter(pos.getX(), pos.getY(), pos.getZ()));
        }

        private int tintDarkness(double dist) {
            double t = 1.0 - dist / TINT_RADIUS;
            t = Math.max(0.0, Math.min(1.0, t));
            return (int) Math.round(t * CraterBasaltBlock.MAX_DARK);
        }

        private void sendTintInit() {
            if (tintMap.isEmpty()) return;
            int maxRing = (int) Math.ceil(TINT_RADIUS);
            List<LongArrayList> rings = new ArrayList<>(maxRing + 1);
            for (int i = 0; i <= maxRing; i++) rings.add(new LongArrayList());

            LongIterator it = tintMap.keySet().iterator();
            while (it.hasNext()) {
                long lp = it.nextLong();
                BlockPos pos = BlockPos.of(lp);
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                    it.remove();
                    continue;
                }
                if (isInvalidTintTarget(level.getBlockState(pos), pos)) {
                    it.remove();
                    continue;
                }
                int ring = (int) Math.min(maxRing, Math.floor(distToCenter(pos.getX(), pos.getY(), pos.getZ())));
                rings.get(ring).add(lp);
            }
            tintRings = rings;

            CraterTintData data = CraterTintData.get(level);
            long[] keys = data.entries().keySet().toLongArray();
            LongArrayList removals = new LongArrayList();
            for (long lp : keys) {
                BlockPos pos = BlockPos.of(lp);
                if (!withinTintBox(pos)) continue;
                if (!tintMap.containsKey(lp)) {
                    if (data.remove(lp)) removals.add(lp);
                }
            }
            if (!removals.isEmpty()) CraterTintSync.sendRemoves(level, removals.toLongArray());
            if (!tintMap.isEmpty()) data.putAll(tintMap);
        }

        private boolean sendTint(long deadline) {
            if (!tintPrepared) {
                sendTintInit();
                tintPrepared = true;
            }
            if (tintRings.isEmpty() || tintSendRing >= tintRings.size()) return true;

            int sent = 0;
            // ВНИМАНИЕ: бюджет тика не должен завершать фазу. Раньше цикл выходил по
            // `sent < TINT_PER_TICK`, после чего возвращал true — и кольца, не поместившиеся в
            // один тик, терялись безвозвратно: тинт у воронки успевал уйти сразу, а от края до
            // конца зоны появлялся только после перезахода (сервер отдавал полную базу при логине).
            // Теперь выход из цикла возможен только по дедлайну/бюджету, и это означает «продолжить
            // в следующем тике», а не «готово».
            while (tintSendRing < tintRings.size()) {
                if (sent >= TINT_PER_TICK) return false;
                if (System.nanoTime() > deadline) return false;
                LongArrayList ring = tintRings.get(tintSendRing);
                if (ring.isEmpty()) {
                    tintSendRing++;
                    tintSendOff = 0;
                    continue;
                }
                int from = tintSendOff;
                int step = Math.min(TINT_PACKET_CHUNK, TINT_PER_TICK - sent);
                int end = Math.min(ring.size(), from + step);
                long[] pos = new long[end - from];
                int[] dark = new int[end - from];
                for (int i = 0; i < pos.length; i++) {
                    long lp = ring.getLong(from + i);
                    pos[i] = lp;
                    dark[i] = tintMap.get(lp);
                }
                CraterTintSync.sendAdds(level, pos, dark);
                sent += pos.length;
                tintSendOff = end;
                if (end >= ring.size()) {
                    tintSendRing++;
                    tintSendOff = 0;
                }
            }
            return true;
        }

        private boolean isInvalidTintTarget(BlockState s, BlockPos pos) {
            if (s.isAir()) return true;
            if (!s.getFluidState().isEmpty() || s.getBlock() instanceof LiquidBlock) return true;
            if (s.getDestroySpeed(level, pos) < 0) return true;
            if (s.getBlock() instanceof CraterBasaltBlock || s.getBlock() instanceof WasteGrassBlock) return true;
            return false;
        }

        private boolean withinTintBox(BlockPos pos) {
            return pos.getX() >= tMinX && pos.getX() <= tMaxX
                    && pos.getY() >= tMinY && pos.getY() <= tMaxY
                    && pos.getZ() >= tMinZ && pos.getZ() <= tMaxZ;
        }

        private double distToCenter(int x, int y, int z) {
            double dx = x + 0.5 - center.x;
            double dy = y + 0.5 - center.y;
            double dz = z + 0.5 - center.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        private double distToCenter(BlockPos pos) {
            return distToCenter(pos.getX(), pos.getY(), pos.getZ());
        }

        private boolean isSurfaceExposed(int x, int y, int z) {
            return exposesFace(x - 1, y, z)
                    || exposesFace(x + 1, y, z)
                    || exposesFace(x, y + 1, z)
                    || exposesFace(x, y - 1, z)
                    || exposesFace(x, y, z - 1)
                    || exposesFace(x, y, z + 1);
        }

        private boolean exposesFace(int nx, int ny, int nz) {
            if (!level.hasChunk(nx >> 4, nz >> 4)) return false;
            BlockPos np = new BlockPos(nx, ny, nz);
            BlockState n = level.getBlockState(np);
            if (n.isAir()) return true;
            return !n.isCollisionShapeFullBlock(level, np);
        }
    }

    // ==================== ТОЧКА ВХОДА ====================

    public static void explode(ServerLevel level, Vec3 center, Entity source, float radius) {
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS,
                2.0F, (1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F);

        if (QUEUE.size() >= MAX_QUEUED_EXPLOSIONS) return;
        State state = new State(level, center, source, level.damageSources().explosion(source, source), radius);
        collectEntities(state);

        QUEUE.addLast(state);
        if (!DRAIN_PENDING) scheduleDrain(level.getServer());
    }

    // ==================== ПЛАНИРОВЩИК ====================

    private static void scheduleDrain(MinecraftServer server) {
        DRAIN_PENDING = true;
        server.tell(new TickTask(1, () -> drainScheduled(server)));
    }

    private static void drainScheduled(MinecraftServer server) {
        DRAIN_PENDING = false;

        long now = System.nanoTime();
        adaptBudget(now);
        long deadline = now + tickBudgetNanos;

        while (!QUEUE.isEmpty()) {
            State st = QUEUE.peek();
            if (!st.work(deadline)) break;
            QUEUE.poll();
        }

        if (!QUEUE.isEmpty() && !DRAIN_PENDING) {
            scheduleDrain(server);
        }
    }

    private static void adaptBudget(long now) {
        long gap = now - lastDrainNanos;
        lastDrainNanos = now;
        if (gap > IDLE_GAP_NANOS) return;
        if (gap > SLOW_GAP_NANOS) {
            tickBudgetNanos = Math.max(MIN_TICK_BUDGET_NANOS, tickBudgetNanos - tickBudgetNanos / 4);
        } else if (gap > 0) {
            tickBudgetNanos = Math.min(MAX_TICK_BUDGET_NANOS, tickBudgetNanos + Math.max(1, tickBudgetNanos / 8));
        }
    }

    // ==================== КЛАССИФИКАЦИЯ БЛОКОВ ====================

    /** Несгораемый блок (в т.ч. жидкость) полностью останавливает огненную волну. */
    private static boolean isWaveBarrier(BlockState s, ServerLevel level, BlockPos pos) {
        if (s.isAir()) return false;
        if (!s.getFluidState().isEmpty() || s.getBlock() instanceof LiquidBlock) return true;
        return !s.isFlammable(level, pos, Direction.UP);
    }

    /** Пробитие за шаг лучом: воздух — {@link #AIR_COST}, горящий блок насквозь — 1+прочность·k. */
    private static float passCost(BlockState s, ServerLevel level, BlockPos pos) {
        if (s.isAir()) return AIR_COST;
        float hardness = s.getDestroySpeed(level, pos);
        return 1.0f + Math.max(0.0f, hardness) * BURN_COST_K;
    }

    /** Пробитие за саму замену/уничтожение блока: дороже прохода насквозь. */
    private static float replaceCost(float hardness) {
        return 1.0f + Math.max(0.0f, hardness) * REPLACE_COST_K;
    }

    private static boolean isWaste(BlockState s) {
        return s.is(ModBlocks.WASTE_LOG.get())
                || s.is(ModBlocks.WASTE_GRASS.get())
                || s.is(ModBlocks.WASTE_PLANKS.get())
                || s.is(ModBlocks.WASTE_PLANKS_STAIRS.get())
                || s.is(ModBlocks.WASTE_PLANKS_SLAB.get());
    }

    private static boolean isNaturalSoil(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM);
    }

    private static boolean isWoodenStairs(BlockState s, ServerLevel level, BlockPos pos) {
        return s.getBlock() instanceof StairBlock && s.getSoundType(level, pos, null) == SoundType.WOOD;
    }

    private static boolean isWoodenSlab(BlockState s, ServerLevel level, BlockPos pos) {
        return s.getBlock() instanceof SlabBlock && s.getSoundType(level, pos, null) == SoundType.WOOD;
    }

    private static boolean isWoodPlanks(BlockState s, ServerLevel level, BlockPos pos) {
        if (s.is(BlockTags.PLANKS)) return true;
        if (s.getSoundType(level, pos, null) != SoundType.WOOD) return false;
        if (s.hasProperty(RotatedPillarBlock.AXIS)) return false;
        return s.isCollisionShapeFullBlock(level, pos);
    }

    private static boolean isLog(BlockState s, ServerLevel level, BlockPos pos) {
        if (s.is(BlockTags.LOGS)) return true;
        return s.hasProperty(RotatedPillarBlock.AXIS)
                && s.getSoundType(level, pos, null) == SoundType.WOOD;
    }

    private static double hash01(long seed, long pos) {
        long h = pos * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = (h ^ (h >>> 31)) ^ (seed * 0x9E3779B97F4A7C15L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (h & 0xFFFFFFFFL) / 4294967296.0;
    }

    // ==================== УРОН МОБАМ ====================

    private static void collectEntities(State state) {
        ServerLevel level = state.level;
        Vec3 center = state.center;
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(state.waveRadius + 3.0));

        for (LivingEntity e : found) {
            if (e == state.sourceEntity || !e.isAlive()) continue;
            double d = e.distanceToSqr(center);
            if (d > state.waveSq) continue;

            double dist = Math.sqrt(d);
            // Полный урон в радиусе-ядре (тот самый «Radius» в тултипе гранаты),
            // линейное падение до минимума на краю зоны волны.
            float t = falloff(dist, state.coreRadius, state.waveRadius);
            float baseDamage = MAX_DAMAGE + (MIN_DAMAGE - MAX_DAMAGE) * t;
            int baseFire = Math.round(MAX_FIRE_SECONDS * (1.0f - t));
            state.entities.add(new EntityTarget(e, baseDamage, baseFire));
        }
    }

    private static float falloff(double dist, float core, float wave) {
        float t = core >= wave ? 1.0f : (float) (dist - core) / (wave - core);
        return Math.max(0.0f, Math.min(1.0f, t));
    }
}
