package com.trd.explosion.logic;

import com.trd.block.basic.CraterBasaltBlock;
import com.trd.block.basic.ModBlocks;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Водородный взрыв гранаты — радиальные зоны поражения:
 * до {@link #ZONE_1_RADIUS} блоки «сгорают» (брёвна — в обугленное {@code waste_log},
 * трава — в выжженную {@code waste_grass}, легковоспламеняющееся удаляется), до
 * {@link #ZONE_2_RADIUS} сполько хрупкое (прочность ниже {@link #WEAK_BLOCK_HARDNESS})
 * сносит ударной волной. Урон мобам и время горения линейно падают с расстоянием.
 *
 * <p>В центре (ядро {@link #CRATER_RADIUS}) плюс к этому выдавливается классическая
 * базальтовая воронка: луч-достижимый объём выжигается в воздух, а его края запекáются
 * в мягкий базальт градиентом. Радиус ядра вложен в первую зону, поэтому на практике
 * центр — это кратер, периферия — выжженные остатки, внешняя часть — ударная волна.
 *
 * <p>Возможность разрушить/заменить блок или нанести полный урон мобу проверяется лучом
 * от эпицентра до цели: блок с сопротивлением взрыву больше {@link #ARMOR_BLOCK_RESISTANCE}
 * до цели гасит эффект. Для урона каждые {@link #RESIST_PER_STEP} суммарного сопротивления
 * пройденных блоков стоят {@link #STEP_DAMAGE_DROP} урона.
 *
 * <p>Вся работа размазана по тикам с адаптивным бюджетом времени (работа на быстрых машинах
 * растёт, на отстающих — ужимается), невыгруженные чанки не подгружаются принудительно.
 * Выпавший дроп и XP в зоне поражения зачищаются.
 */
public class ExplosionHydrogen {

    public static final ResourceKey<DamageType> CREMATION =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("trd", "cremated"));

    public static float ZONE_1_RADIUS = 30.0f;
    public static float ZONE_2_RADIUS = 40.0f;
    public static float MAX_DAMAGE = 500.0f;
    public static float MIN_DAMAGE = 10.0f;
    public static int MAX_FIRE_SECONDS = 30;
    public static float WEAK_BLOCK_HARDNESS = 0.4f;
    public static float ARMOR_BLOCK_RESISTANCE = 10.0f;
    public static float RESIST_PER_STEP = 5.0f;
    public static float STEP_DAMAGE_DROP = 0.10f;

    public static float CRATER_RADIUS = 16.0f;
    public static float CRATER_UP_STRETCH = 1.3f;
    public static float CRATER_DOWN_SQUASH = 0.33f;
    public static float CRATER_JITTER = 0.9f;
    public static float CRATER_NOISE_SCALE = 0.22f;
    public static float CRATER_BUDGET = 70.0f;
    public static int CRATER_EDGE_AIR_RANGE = 5;
    public static float CRATER_GRADIENT_RADIUS = 24.0f;
    public static float CRATER_SOFT_CORE_RADIUS = 6.0f;
    public static float CRATER_RIM_BAND = 5.0f;
    public static int CRATER_MAX_JOBS = 200_000;

    public static float GLASS_DESTROY_PROB_ZONE_1 = 0.8f;
    public static float GLASS_DESTROY_PROB_ZONE_2 = 0.4f;
    public static float FIRE_START_RADIUS = 15.0f;
    public static float FIRE_FADE_RADIUS = ZONE_1_RADIUS * 0.75f;
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

    private static final ArrayDeque<State> QUEUE = new ArrayDeque<>();
    private static boolean DRAIN_PENDING = false;

    private static long tickBudgetNanos = DEFAULT_TICK_BUDGET_NANOS;
    private static long lastDrainNanos = System.nanoTime();

    private enum Phase { SCAN, APPLY, CARVE, CARVE_APPLY, BASALT, FIRE, DAMAGE, FINISH }

    private static final class FloatRef {
        float value;
        boolean blocked;
    }

    private record BasaltJob(BlockPos pos, boolean destroy) {}

    private record Flood(BlockPos pos, int depth) {}

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

        final double zone1Sq;
        final double zone2Sq;
        final float zone1Radius;
        final float zone2Radius;

        final int minX, maxX, minY, maxY, minZ, maxZ;
        int scanX, scanY, scanZ;

        Phase phase = Phase.SCAN;

        final LongArrayList replaceLog = new LongArrayList();
        final LongArrayList replaceGrass = new LongArrayList();
        final LongArrayList replacePlanks = new LongArrayList();
        final LongArrayList replaceStairs = new LongArrayList();
        final LongArrayList replaceSlabs = new LongArrayList();
        final LongArrayList destroy = new LongArrayList();
        final List<EntityTarget> entities = new ArrayList<>();

        int applyLog;
        int applyGrass;
        int applyPlanks;
        int applyStairs;
        int applySlabs;
        int applyDestroy;
        int applyEntity;

        final float fireStart;
        final float fireEnd;
        final double fireInnerSq;
        final double fireOuterSq;
        final int fireMinX, fireMaxX, fireMinY, fireMaxY, fireMinZ, fireMaxZ;
        int fireX, fireY, fireZ;

        final FloatRef rayResist = new FloatRef();

        final long seed;
        final BlockPos gradientAnchor;
        final int crMinX, crMaxX, crMinY, crMaxY, crMinZ, crMaxZ;
        int crX, crY, crZ;
        final LongArrayList carve = new LongArrayList();
        int applyCarve;
        boolean bfsDone;
        final Deque<Flood> floodQueue = new ArrayDeque<>();
        final LongOpenHashSet floodVisited = new LongOpenHashSet(CRATER_MAX_JOBS / 4);
        final ArrayDeque<BasaltJob> basaltJobs = new ArrayDeque<>();

        State(ServerLevel level, Vec3 center, Entity source, DamageSource damageSource) {
            this.level = level;
            this.center = center;
            this.damageSource = damageSource;
            this.sourceEntity = source;
            this.zone1Radius = ZONE_1_RADIUS;
            this.zone2Radius = ZONE_2_RADIUS;
            this.zone1Sq = (double) zone1Radius * zone1Radius;
            this.zone2Sq = (double) zone2Radius * zone2Radius;
            this.fireStart = FIRE_START_RADIUS;
            this.fireEnd = FIRE_FADE_RADIUS;
            this.fireInnerSq = (double) fireStart * fireStart;
            this.fireOuterSq = (double) fireEnd * fireEnd;
            this.seed = level.getSeed();
            this.gradientAnchor = findCraterFloor(level, center);

            int cx = (int) Math.floor(center.x);
            int cy = (int) Math.floor(center.y);
            int cz = (int) Math.floor(center.z);
            int r = (int) Math.ceil(zone2Radius);

            minX = cx - r;
            maxX = cx + r;
            minY = Math.max(level.getMinBuildHeight(), cy - r);
            maxY = Math.min(level.getMaxBuildHeight() - 1, cy + r);
            minZ = cz - r;
            maxZ = cz + r;
            scanX = minX;
            scanY = minY;
            scanZ = minZ;
            int fR = (int) Math.ceil(fireEnd);
            fireMinX = cx - fR;
            fireMaxX = cx + fR;
            fireMinY = Math.max(level.getMinBuildHeight(), cy - fR);
            fireMaxY = Math.min(level.getMaxBuildHeight() - 1, cy + fR);
            fireMinZ = cz - fR;
            fireMaxZ = cz + fR;
            fireX = fireMinX;
            fireY = fireMinY;
            fireZ = fireMinZ;

            int rXZ = (int) Math.ceil(CRATER_RADIUS);
            int rYUp = (int) Math.ceil(CRATER_RADIUS * CRATER_UP_STRETCH);
            int rYDown = (int) Math.ceil(CRATER_RADIUS * CRATER_DOWN_SQUASH);
            crMinX = cx - rXZ;
            crMaxX = cx + rXZ;
            crMinY = Math.max(level.getMinBuildHeight(), cy - rYDown);
            crMaxY = Math.min(level.getMaxBuildHeight() - 1, cy + rYUp);
            crMinZ = cz - rXZ;
            crMaxZ = cz + rXZ;
            crX = crMinX;
            crY = crMinY;
            crZ = crMinZ;
        }

        boolean work(long deadline) {
            while (true) {
                switch (phase) {
                    case SCAN -> {
                        if (!scan(deadline)) return false;
                        phase = Phase.APPLY;
                    }
                    case APPLY -> {
                        if (!apply(deadline)) return false;
                        phase = Phase.CARVE;
                    }
                    case CARVE -> {
                        if (!carveScan(deadline)) return false;
                        phase = Phase.CARVE_APPLY;
                    }
                    case CARVE_APPLY -> {
                        if (!carveApply(deadline)) return false;
                        phase = Phase.BASALT;
                    }
                    case BASALT -> {
                        if (!basaltPhase(deadline)) return false;
                        phase = Phase.FIRE;
                    }
                    case FIRE -> {
                        if (!fireScan(deadline)) return false;
                        phase = Phase.DAMAGE;
                    }
                    case DAMAGE -> {
                        if (!damage(deadline)) return false;
                        phase = Phase.FINISH;
                    }
                    case FINISH -> {
                        discardItemsNearby(level, center, zone2Radius + 2.0f);
                        return true;
                    }
                }
            }
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
            if (d2 > zone2Sq) return;
            if (!level.hasChunk(x >> 4, z >> 4)) return;

            BlockPos pos = new BlockPos(x, y, z);
            BlockState s = level.getBlockState(pos);
            if (s.isAir()) return;
            if (!s.getFluidState().isEmpty()) return;
            if (isWaste(s)) return;

            float hardness = s.getDestroySpeed(level, pos);
            if (hardness < 0) return;

            double dist = Math.sqrt(d2);

            if (isGlass(s)) {
                float prob = dist <= zone1Radius ? GLASS_DESTROY_PROB_ZONE_1 : GLASS_DESTROY_PROB_ZONE_2;
                if (hash01(seed, pos.asLong()) < prob
                        && !rayBlocked(x + 0.5, y + 0.5, z + 0.5)) {
                    destroy.add(pos.asLong());
                }
                return;
            }

            if (dist <= zone1Radius) {
                if (isLog(s, level, pos)) {
                    if (!rayBlocked(x + 0.5, y + 0.5, z + 0.5)) replaceLog.add(pos.asLong());
                } else if (s.is(Blocks.GRASS_BLOCK)) {
                    if (!rayBlocked(x + 0.5, y + 0.5, z + 0.5)) replaceGrass.add(pos.asLong());
                } else if (isWoodenStairs(s, level, pos)) {
                    if (!rayBlocked(x + 0.5, y + 0.5, z + 0.5)) replaceStairs.add(pos.asLong());
                } else if (isWoodenSlab(s, level, pos)) {
                    if (!rayBlocked(x + 0.5, y + 0.5, z + 0.5)) replaceSlabs.add(pos.asLong());
                } else if (isWoodPlanks(s, level, pos)) {
                    if (!rayBlocked(x + 0.5, y + 0.5, z + 0.5)) replacePlanks.add(pos.asLong());
                } else if (isZone1Burnable(s, level, pos, hardness)) {
                    if (!rayBlocked(x + 0.5, y + 0.5, z + 0.5)) destroy.add(pos.asLong());
                }
            } else if (hardness < WEAK_BLOCK_HARDNESS) {
                if (!rayBlocked(x + 0.5, y + 0.5, z + 0.5)) destroy.add(pos.asLong());
            }
        }

        private boolean rayBlocked(double tx, double ty, double tz) {
            ray(tx, ty, tz);
            return rayResist.blocked;
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
                if (!cur.is(Blocks.GRASS_BLOCK)) continue;
                int dark = Math.max(grassDarkness(pos), rimDarkness(pos));
                level.setBlock(pos, ModBlocks.WASTE_GRASS.get().defaultBlockState()
                        .setValue(CraterBasaltBlock.DARKNESS, dark), 3);
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
                if (!shouldDestroy(cur, level, pos)) continue;
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            return true;
        }

        private boolean damage(long deadline) {
            for (; applyEntity < entities.size(); applyEntity++) {
                if (System.nanoTime() > deadline) return false;
                EntityTarget t = entities.get(applyEntity);
                LivingEntity e = t.entity;
                if (!e.isAlive()) continue;

                Vec3 c = e.getBoundingBox().getCenter();
                ray(c.x, c.y, c.z);

                double steps = rayResist.value / RESIST_PER_STEP;
                float mult = Math.max(0.0f, 1.0f - (float) (STEP_DAMAGE_DROP * Math.floor(steps)));
                float dmg = t.baseDamage * mult;
                if (dmg > 0) e.hurt(damageSource, dmg);
                if (t.baseFire > 0) e.setSecondsOnFire(t.baseFire);
            }
            return true;
        }

        /** 3D-DDA от эпицентра до точки: стартовая и целевая ячейки не учитываются. */
        private void ray(double tx, double ty, double tz) {
            double cx = center.x;
            double cy = center.y;
            double cz = center.z;
            double dx = tx - cx;
            double dy = ty - cy;
            double dz = tz - cz;

            int px = (int) Math.floor(cx);
            int py = (int) Math.floor(cy);
            int pz = (int) Math.floor(cz);
            int startX = px, startY = py, startZ = pz;
            int ex = (int) Math.floor(tx);
            int ey = (int) Math.floor(ty);
            int ez = (int) Math.floor(tz);

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

            boolean blocked = false;
            float resist = 0.0f;

            for (int guard = 0; guard < 512; guard++) {
                if (px == ex && py == ey && pz == ez) break;
                if (px != startX || py != startY || pz != startZ) {
                    if (level.hasChunk(px >> 4, pz >> 4)) {
                        BlockState s = level.getBlockState(new BlockPos(px, py, pz));
                        if (!s.isAir() && s.getFluidState().isEmpty()
                                && !(s.getBlock() instanceof LiquidBlock)) {
                            float r = s.getBlock().getExplosionResistance();
                            resist += r;
                            if (r > ARMOR_BLOCK_RESISTANCE) blocked = true;
                        }
                    }
                }
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

            rayResist.blocked = blocked;
            rayResist.value = resist;
        }

        // ==================== ОГОНЬ ====================

        private boolean fireScan(long deadline) {
            int x = fireX, y = fireY, z = fireZ;
            for (; x <= fireMaxX; x++) {
                for (; y <= fireMaxY; y++) {
                    for (; z <= fireMaxZ; z++) {
                        if (System.nanoTime() > deadline) {
                            fireX = x;
                            fireY = y;
                            fireZ = z;
                            return false;
                        }
                        tryFire(x, y, z);
                    }
                    z = fireMinZ;
                }
                y = fireMinY;
            }
            return true;
        }

        private void tryFire(int x, int y, int z) {
            double dx = x + 0.5 - center.x;
            double dy = y + 0.5 - center.y;
            double dz = z + 0.5 - center.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < fireInnerSq || d2 > fireOuterSq) return;
            if (!level.hasChunk(x >> 4, z >> 4)) return;

            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()) return;

            BlockPos belowPos = pos.below();
            if (!level.hasChunk(belowPos.getX() >> 4, belowPos.getZ() >> 4)) return;
            BlockState below = level.getBlockState(belowPos);
            if (below.isAir() || !below.getFluidState().isEmpty()) return;

            boolean burnable = below.isFlammable(level, belowPos, Direction.UP);
            if (!burnable && !Blocks.FIRE.defaultBlockState().canSurvive(level, pos)) return;

            double t = Math.min(1.0, (Math.sqrt(d2) - fireStart) / (fireEnd - fireStart));
            double base = FIRE_BASE_DENSITY * (1.0 - t);
            double noise = FIRE_NOISE_CENTER + (FIRE_NOISE_EDGE - FIRE_NOISE_CENTER) * t;
            double p = base + (hash01(seed, pos.asLong()) - 0.5) * 2.0 * noise;
            if (p < FIRE_THRESHOLD) return;
            if (rayBlocked(x + 0.5, y + 0.5, z + 0.5)) return;

            level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 3);
        }

        private int grassDarkness(BlockPos pos) {
            double dx = pos.getX() + 0.5 - center.x;
            double dy = pos.getY() + 0.5 - center.y;
            double dz = pos.getZ() + 0.5 - center.z;
            double t = Math.min(1.0, Math.sqrt(dx * dx + dy * dy + dz * dz) / zone1Radius);
            return (int) Math.round((1.0 - t) * CraterBasaltBlock.MAX_DARK);
        }

        /** Затемнение кольца вокруг края воронки: максимум на самом ободе, 0 дальше {@link #CRATER_RIM_BAND}. */
        private int rimDarkness(BlockPos pos) {
            double dx = pos.getX() + 0.5 - center.x;
            double dy = pos.getY() + 0.5 - center.y;
            double dz = pos.getZ() + 0.5 - center.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double delta = Math.abs(dist - CRATER_RADIUS);
            if (delta > CRATER_RIM_BAND) return 0;
            double t = 1.0 - delta / CRATER_RIM_BAND;
            return (int) Math.round(t * CraterBasaltBlock.MAX_DARK);
        }

        // ==================== БАЗАЛЬТОВАЯ ВОРОНКА ====================

        private boolean carveScan(long deadline) {
            int x = crX, y = crY, z = crZ;
            for (; x <= crMaxX; x++) {
                for (; y <= crMaxY; y++) {
                    for (; z <= crMaxZ; z++) {
                        if (System.nanoTime() > deadline) {
                            crX = x;
                            crY = y;
                            crZ = z;
                            return false;
                        }
                        if (isInCraterRegion(x, y, z) && rayReaches(x, y, z)) {
                            carve.add(BlockPos.asLong(x, y, z));
                        }
                    }
                    z = crMinZ;
                }
                y = crMinY;
            }
            return true;
        }

        private boolean isInCraterRegion(int x, int y, int z) {
            double dx = (x + 0.5) - center.x;
            double dy = (y + 0.5) - center.y;
            double dz = (z + 0.5) - center.z;

            double vRadius = dy >= 0
                    ? CRATER_RADIUS * CRATER_UP_STRETCH
                    : CRATER_RADIUS * CRATER_DOWN_SQUASH;

            double horizontal = (dx * dx + dz * dz) / (CRATER_RADIUS * CRATER_RADIUS);
            double vertical = (dy * dy) / (vRadius * vRadius);
            double jitter = (blastJitter(seed, x, y, z) * CRATER_JITTER) / CRATER_RADIUS;
            return horizontal + vertical + jitter <= 1.0;
        }

        private boolean rayReaches(int tx, int ty, int tz) {
            double cx = center.x;
            double cy = center.y;
            double cz = center.z;

            int px = (int) Math.floor(cx);
            int py = (int) Math.floor(cy);
            int pz = (int) Math.floor(cz);

            double dx = (tx + 0.5) - cx;
            double dy = (ty + 0.5) - cy;
            double dz = (tz + 0.5) - cz;

            int stepX = (int) Math.signum(dx);
            int stepY = (int) Math.signum(dy);
            int stepZ = (int) Math.signum(dz);

            double tDeltaX = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
            double tDeltaY = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
            double tDeltaZ = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);

            double tMaxX = dx == 0 ? Double.POSITIVE_INFINITY : (dx > 0 ? (px + 1 - cx) : (cx - px)) * tDeltaX;
            double tMaxY = dy == 0 ? Double.POSITIVE_INFINITY : (dy > 0 ? (py + 1 - cy) : (cy - py)) * tDeltaY;
            double tMaxZ = dz == 0 ? Double.POSITIVE_INFINITY : (dz > 0 ? (pz + 1 - cz) : (cz - pz)) * tDeltaZ;

            float spent = 0.0f;

            for (int guard = 0; guard < 1024; guard++) {
                if (!level.hasChunk(px >> 4, pz >> 4)) return false;
                BlockPos pos = new BlockPos(px, py, pz);
                BlockState s = level.getBlockState(pos);
                if (isBarrier(level, s, pos)) return false;

                float cost = blockCost(s, level, pos);
                if (!Float.isFinite(cost)) return false;
                spent += cost;
                if (spent > CRATER_BUDGET) return false;

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

        private boolean carveApply(long deadline) {
            for (; applyCarve < carve.size(); applyCarve++) {
                if (System.nanoTime() > deadline) return false;
                long l = carve.getLong(applyCarve);
                BlockPos pos = BlockPos.of(l);
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                BlockState s = level.getBlockState(pos);
                if (s.isAir()) continue;
                if (!s.getFluidState().isEmpty()) continue;
                if (s.getDestroySpeed(level, pos) < 0) continue;
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            return true;
        }

        private boolean basaltPhase(long deadline) {
            if (!bfsDone) {
                for (; !floodQueueFull() && floodQueueSeeded < carve.size(); floodQueueSeeded++) {
                    if (System.nanoTime() > deadline) return false;
                    long l = carve.getLong(floodQueueSeeded);
                    if (floodVisited.add(l)) floodQueue.addLast(new Flood(BlockPos.of(l), 0));
                }
                while (!floodQueue.isEmpty()) {
                    if (System.nanoTime() > deadline) return false;
                    if (floodVisited.size() >= CRATER_MAX_JOBS || basaltJobs.size() >= CRATER_MAX_JOBS) {
                        floodQueue.clear();
                        break;
                    }
                    Flood f = floodQueue.pollFirst();
                    for (Direction d : Direction.values()) {
                        BlockPos nb = f.pos().relative(d);
                        if (!level.hasChunk(nb.getX() >> 4, nb.getZ() >> 4)) continue;
                        BlockState ns = level.getBlockState(nb);
                        if (ns.isAir()) {
                            if (f.depth() + 1 < CRATER_EDGE_AIR_RANGE && floodVisited.add(nb.asLong())) {
                                floodQueue.addLast(new Flood(nb, f.depth() + 1));
                            }
                        } else if (floodVisited.add(nb.asLong())) {
                            enqueueEdgeTarget(nb, ns);
                        }
                    }
                }
                bfsDone = true;
            }

            while (!basaltJobs.isEmpty()) {
                if (System.nanoTime() > deadline) return false;
                BasaltJob job = basaltJobs.pollFirst();
                BlockPos pos = job.pos();
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                BlockState s = level.getBlockState(pos);
                if (s.isAir()) continue;
                if (!s.getFluidState().isEmpty()) continue;
                if (isBakedBasalt(s)) continue;

                if (job.destroy()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                } else {
                    int dark = Math.max(darknessLevel(pos), rimDarkness(pos));
                    level.setBlock(pos,
                            pickSoftBasalt(pos).defaultBlockState()
                                    .setValue(CraterBasaltBlock.DARKNESS, dark), 3);
                }
            }
            return true;
        }

        int floodQueueSeeded;

        private boolean floodQueueFull() {
            return basaltJobs.size() >= CRATER_MAX_JOBS || floodVisited.size() >= CRATER_MAX_JOBS;
        }

        private void enqueueEdgeTarget(BlockPos pos, BlockState state) {
            if (basaltJobs.size() >= CRATER_MAX_JOBS) return;
            if (!state.getFluidState().isEmpty()) return;
            if (isBakedBasalt(state)) return;

            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0) return;

            boolean weak = hardness <= WEAK_BLOCK_HARDNESS;
            if (!weak && !state.isCollisionShapeFullBlock(level, pos)) return;
            basaltJobs.addLast(new BasaltJob(pos, weak));
        }

        private Block pickSoftBasalt(BlockPos pos) {
            BlockPos anchor = gradientAnchor;
            if (anchor == null) anchor = pos;
            double dx = pos.getX() - anchor.getX();
            double dz = pos.getZ() - anchor.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist <= CRATER_SOFT_CORE_RADIUS) return ModBlocks.BASALT_SOFT.get();
            return softBasaltNoise(pos) ? ModBlocks.BASALT_SOFT_2.get() : ModBlocks.BASALT_SOFT_3.get();
        }

        private int darknessLevel(BlockPos pos) {
            BlockPos anchor = gradientAnchor;
            if (anchor == null) anchor = pos;
            double dx = pos.getX() - anchor.getX();
            double dy = pos.getY() - anchor.getY();
            double dz = pos.getZ() - anchor.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double t = Math.min(1.0, dist / CRATER_GRADIENT_RADIUS);
            return (int) Math.round((1.0 - t) * CraterBasaltBlock.MAX_DARK);
        }
    }

    // ==================== ТОЧКА ВХОДА ====================

    public static void explode(ServerLevel level, Vec3 center, Entity source) {
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 6.0F, 0.4F);

        discardItemsNearby(level, center, ZONE_2_RADIUS + 2.0f);

        if (QUEUE.size() >= MAX_QUEUED_EXPLOSIONS) return;
        State state = new State(level, center, source, cremationSource(level, source));
        collectEntities(state);

        QUEUE.addLast(state);
        if (!DRAIN_PENDING) scheduleDrain(level.getServer());

        scheduleLateSweep(level, center);
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

    private static boolean isWaste(BlockState s) {
        return s.is(ModBlocks.WASTE_LOG.get())
                || s.is(ModBlocks.WASTE_GRASS.get())
                || s.is(ModBlocks.WASTE_PLANKS.get())
                || s.is(ModBlocks.WASTE_PLANKS_STAIRS.get())
                || s.is(ModBlocks.WASTE_PLANKS_SLAB.get());
    }

    private static boolean isGlass(BlockState s) {
        Block b = s.getBlock();
        if (!(b instanceof AbstractGlassBlock) && !(b instanceof IronBarsBlock)) {
            return false;
        }
        return !s.is(ModBlocks.ARMORED_GLASS.get()) && !s.is(ModBlocks.CONCRETE_ARMED_GLASS.get());
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

    private static boolean isZone1Burnable(BlockState s, ServerLevel level, BlockPos pos, float hardness) {
        if (hardness < WEAK_BLOCK_HARDNESS) return true;
        if (s.is(Blocks.COBWEB)) return true;
        SoundType sound = s.getSoundType(level, pos, null);
        return sound == SoundType.WOOD || sound == SoundType.WOOL;
    }

    private static boolean shouldDestroy(BlockState s, ServerLevel level, BlockPos pos) {
        if (s.isAir()) return false;
        if (!s.getFluidState().isEmpty()) return false;
        float hardness = s.getDestroySpeed(level, pos);
        if (hardness < 0) return false;
        return hardness < WEAK_BLOCK_HARDNESS || isZone1Burnable(s, level, pos, hardness);
    }

    // ==================== ВОРОНКА: УТИЛИТЫ ====================

    private static float blockCost(BlockState state, ServerLevel level, BlockPos pos) {
        if (state.isAir()) return 1.0f;
        if (state.getFluidState().isSource() || state.getBlock() instanceof LiquidBlock) return 0.6f;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return Float.POSITIVE_INFINITY;
        return Math.max(1.0f, hardness);
    }

    private static boolean isBarrier(ServerLevel level, BlockState state, BlockPos pos) {
        if (isBakedBasalt(state)) return true;
        return state.getDestroySpeed(level, pos) < 0;
    }

    private static boolean isBakedBasalt(BlockState state) {
        return state.is(ModBlocks.BASALT_SCORCHED.get())
                || state.is(ModBlocks.BASALT_ROUGH.get())
                || state.is(ModBlocks.BASALT_SOFT.get())
                || state.is(ModBlocks.BASALT_SOFT_2.get())
                || state.is(ModBlocks.BASALT_SOFT_3.get());
    }

    private static boolean softBasaltNoise(BlockPos pos) {
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h & 1) == 0;
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

    private static double blastJitter(long seed, int x, int y, int z) {
        double sx = x * CRATER_NOISE_SCALE;
        double sy = y * CRATER_NOISE_SCALE;
        double sz = z * CRATER_NOISE_SCALE;
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

    // ==================== УРОН МОБАМ ====================

    private static void collectEntities(State state) {
        ServerLevel level = state.level;
        Vec3 center = state.center;
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(state.zone2Radius + 3.0));

        for (LivingEntity e : found) {
            if (e == state.sourceEntity || !e.isAlive()) continue;
            double d = e.distanceToSqr(center);
            if (d > state.zone2Sq) continue;

            double dist = Math.sqrt(d);
            float t = (float) (dist / state.zone2Radius);
            t = Math.max(0.0f, Math.min(1.0f, t));
            float baseDamage = MAX_DAMAGE + (MIN_DAMAGE - MAX_DAMAGE) * t;
            int baseFire = Math.round(MAX_FIRE_SECONDS * (1.0f - t));
            state.entities.add(new EntityTarget(e, baseDamage, baseFire));
        }
    }

    private static DamageSource cremationSource(ServerLevel level, Entity source) {
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolder(CREMATION).orElse(null);
        if (holder != null) {
            return new DamageSource(holder, source, source);
        }
        return level.damageSources().explosion(source, source);
    }

    // ==================== УТИЛИТЫ ====================

    private static void discardItemsNearby(ServerLevel level, Vec3 center, float radius) {
        AABB box = new AABB(center, center).inflate(radius);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
            item.discard();
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box)) {
            orb.discard();
        }
    }

    private static void scheduleLateSweep(ServerLevel level, Vec3 center) {
        MinecraftServer server = level.getServer();
        Runnable task = new Runnable() {
            int ticks = 25;

            @Override
            public void run() {
                if (--ticks <= 0) {
                    discardItemsNearby(level, center, ZONE_2_RADIUS + 2.0f);
                    return;
                }
                server.tell(new TickTask(1, this));
            }
        };
        server.tell(new TickTask(1, task));
    }
}