package com.trd.explosion.logic;

import com.trd.block.basic.ModBlocks;
import com.trd.block.basic.ScorchedBasaltBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class ExplosionHydrogen {

    // ========== НАСТРОЙКИ ==========
    public static int RAY_COUNT = 8000;
    public static float MAX_RANGE = 50.0f;
    public static float MAX_PENETRATION = 60.0f;
    public static float VERTICAL_PENALTY = 0.75f;
    public static float BRANCH_CHANCE = 0.40f;
    public static float BRANCH_ANGLE = (float) Math.toRadians(45);
    public static float BRANCH_RANGE_MULTIPLIER = 0.50f;
    public static float BRANCH_PENETRATION_MULTIPLIER = 0.50f;

    // Луч разрушает блоки, пока у него осталось БОЛЕЕ 60% начального пробития.
    public static float DESTROY_THRESHOLD = 0.55f;

    // Урон мобам за единицу пробития.
    public static float RAY_DAMAGE_PER_PENETRATION = 1f;

    // Отложенное припекание/зачистка базальта (батчами по тикам, чтобы не было лагов).
    // Само припекание НЕ тратит пробитие — лишь проверяет, что луч ещё «жив».
    public static int BASALT_JOBS_PER_TICK = 12000;
    public static int BASALT_QUEUE_CAP = 200000;

    // Градиент цвета базальта: MAX_LIGHT ступеней (+1), осветление +50%
    // достигается на расстоянии GRADIENT_RADIUS от центра (граница кратора).
    public static float GRADIENT_RADIUS = 20.0f;

    // Блоки с прочностью НЕ выше этой не запёкаются в базальт, а просто уничтожаются.
    // Иначе листва/цветы из «тонких» блоков превращаются в цельные кубы базальта.
    public static float WEAK_BLOCK_HARDNESS = 0.4f;

    // Шоквейв-лучи — замена центрального ванильного взрыва.
    // Повышенное пробитие, малая дальность, широкий цилиндр (сразу несколько блоков).
    // Урон наносится по всей площади цилиндра, даже если блоки не были пробиты.
    public static int SHOCKWAVE_RAY_COUNT = 120;
    public static float SHOCKWAVE_RANGE = 8.0f;
    public static float SHOCKWAVE_WIDTH = 3.0f;         // поперечный радиус «трубы» луча в блоках
    public static float SHOCKWAVE_PENETRATION = 120f;

    private static final Random RANDOM = new Random();

    private record BasaltJob(BlockPos pos, Vec3 center, boolean destroy) {}

    private static final Deque<BasaltJob> BASALT_QUEUE = new ArrayDeque<>();

    // Опорная точка градиента осветления: центральный нижний блок дна кратера.
    // Центр взрыва после подрыва — пустота, поэтому градиент считаем от дна воронки.
    private static BlockPos GRADIENT_ANCHOR;

    public static void explode(ServerLevel level, Vec3 center, Entity source) {
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
                6.0F, 0.4F);

        // Опорная точка градиента ещё до волны (для ранних партий), после волны обновится
        GRADIENT_ANCHOR = findCraterFloor(level, center);

        // Как ванильный взрыв: всё, что выпало рядом (лут сундуков, дроп мобов) — уничтожается
        discardItemsNearby(level, center, MAX_RANGE);

        // Сначала ударная волна пробивает воронку, затем обычные лучи
        // запёкают её края базальтом (scheduleShockwaveRays сам запускает scheduleRays)
        scheduleShockwaveRays(level, center, source);

        // Финальная зачистка дропа после того, как все лучи отработают
        level.getServer().tell(new net.minecraft.server.TickTask(80, () ->
                discardItemsNearby(level, center, MAX_RANGE)));
    }

    private static void discardItemsNearby(ServerLevel level, Vec3 center, float radius) {
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(center, center).inflate(radius));
        for (ItemEntity item : items) {
            item.discard();
        }
    }

    /**
     * Проверяет, что от центра взрыва до цели нет непреодолимых блоков (стен/бедрока).
     * Урон лучом наносится только тогда, когда «защита» на пути действительно пробита.
     */
    private static boolean isPathClear(ServerLevel level, Vec3 from, LivingEntity entity) {
        Vec3 to = entity.position().add(0, 0.5, 0);
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
        return level.clip(ctx).getType() == HitResult.Type.MISS;
    }

    // ==================== ШОКВЕЙВ-ЛУЧИ (широкие, короткие) ====================

    private static void scheduleShockwaveRays(ServerLevel level, Vec3 center, Entity source) {
        if (SHOCKWAVE_RAY_COUNT <= 0) return;
        List<Vec3> directions = new ArrayList<>();
        for (int i = 0; i < SHOCKWAVE_RAY_COUNT; i++) {
            directions.add(randomDirection());
        }
        processShockwaveBatch(level, center, source, directions, 0, 100);
    }

    private static void processShockwaveBatch(ServerLevel level, Vec3 center, Entity source,
                                              List<Vec3> directions, int startIdx, int batchSize) {
        int endIdx = Math.min(startIdx + batchSize, directions.size());
        List<LivingEntity> allEntities = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(MAX_RANGE));

        for (int i = startIdx; i < endIdx; i++) {
            castShockwaveRay(level, center, directions.get(i), source, allEntities);
        }

        // Ударная волна тоже запёкает выживших соседей разрушенных блоков в базальт
        drainBasaltJobs(level);

        if (endIdx < directions.size()) {
            level.getServer().tell(new net.minecraft.server.TickTask(1, () ->
                    processShockwaveBatch(level, center, source, directions, endIdx, batchSize)));
        } else {
            // Ударная волна отработала: воронка выкопана — обновляем якорь градиента
            // (дно кратера) и запускаем обычные лучи, которые пройдут по воронке
            GRADIENT_ANCHOR = findCraterFloor(level, center);
            discardItemsNearby(level, center, MAX_RANGE);
            scheduleRays(level, center, source);
        }
    }

    private static void castShockwaveRay(ServerLevel level, Vec3 origin, Vec3 direction, Entity source,
                                         List<LivingEntity> allEntities) {
        float penetration = SHOCKWAVE_PENETRATION;
        float destroyFloor = SHOCKWAVE_PENETRATION * DESTROY_THRESHOLD;
        float distance = 0;
        Set<Integer> hitIds = new HashSet<>();

        Vec3 dir = direction.normalize();

        ray:
        while (distance < SHOCKWAVE_RANGE && penetration > 0) {
            // Ударная волна сужается по мере прохождения: каждую треть дистанции теряет 1 блок радиуса
            float currentWidth = Math.max(1.0f, SHOCKWAVE_WIDTH - (int) (distance / (SHOCKWAVE_RANGE / 3.0f)));
            int r = (int) Math.ceil(currentWidth);
            Vec3 current = origin.add(dir.scale(distance));

            // Широкий цилиндр вокруг линии луча — разрушаем сразу несколько блоков за шаг
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        // Расстояние от оси цилиндра (перпендикулярно направлению луча)
                        double along = x * dir.x + y * dir.y + z * dir.z;
                        double perpSq = (x * x + y * y + z * z) - along * along;
                        if (perpSq > currentWidth * currentWidth) continue;

                        BlockPos pos = new BlockPos(
                                (int) Math.floor(current.x + x),
                                (int) Math.floor(current.y + y),
                                (int) Math.floor(current.z + z));

                        BlockState state = level.getBlockState(pos);
                        // Та же логика, что у обычного луча: базальт/бедрок — барьер
                        if (state.is(ModBlocks.BASALT_SCORCHED.get())
                                || state.is(ModBlocks.BASALT_ROUGH.get())
                                || state.getDestroySpeed(level, pos) < 0) {
                            break ray;
                        }
                        if (state.isAir()) continue;

                        if (state.getFluidState().isSource() || state.getBlock() instanceof LiquidBlock) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                            penetration -= 0.5f;
                            continue;
                        }

                        // Как у обычного луча: разрушение стоит столько, сколько прочность блока.
                        // Сильная порода (усиленный бетон и т.п.) просто не по карману — луч тухнет.
                        float cost = Math.max(1.0f, state.getDestroySpeed(level, pos));
                        if (penetration < cost) break ray;
                        if (penetration <= destroyFloor) break ray;

                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        penetration -= cost;

                        // Как у обычного луча: припекаем выживших соседей в базальт
                        // (axis = null → печём со всех сторон, цилиндр же широкий)
                        enqueueBasaltEdges(level, origin, pos, null, penetration);
                    }
                }
            }

            // Урон мобам по всей площади цилиндра (независимо от пробития блоков)
            float hitRange = currentWidth + 0.5f;
            for (LivingEntity entity : allEntities) {
                if (entity == source || hitIds.contains(entity.getId())) continue;
                double dx = entity.getX() - current.x;
                double dy = entity.getY() - current.y;
                double dz = entity.getZ() - current.z;
                double along = dx * dir.x + dy * dir.y + dz * dir.z;
                double perpSq = (dx * dx + dy * dy + dz * dz) - along * along;
                if (perpSq <= hitRange * hitRange) {
                    // Урон только если защита на пути (бедрок и т.п.) действительно пробита,
                    // т.е. обзор на цель чистый — иначе стены экранируют
                    if (!isPathClear(level, origin, entity)) continue;
                    float damage = penetration * RAY_DAMAGE_PER_PENETRATION;
                    if (damage > 0) {
                        entity.hurt(level.damageSources().explosion(source, source), damage);
                        hitIds.add(entity.getId());
                    }
                }
            }

            distance += 1.0f;
            penetration -= 1.0f;
        }
    }

    // ==================== ОБЫЧНЫЕ ЛУЧИ ====================

    private static void scheduleRays(ServerLevel level, Vec3 center, Entity source) {
        List<Vec3> directions = new ArrayList<>();
        for (int i = 0; i < RAY_COUNT; i++) {
            directions.add(randomDirection());
        }
        processRayBatch(level, center, source, directions, 0, 200);
    }

    private static void processRayBatch(ServerLevel level, Vec3 center, Entity source,
                                        List<Vec3> directions, int startIdx, int batchSize) {
        int endIdx = Math.min(startIdx + batchSize, directions.size());
        List<LivingEntity> allEntities = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(MAX_RANGE));

        for (int i = startIdx; i < endIdx; i++) {
            Vec3 dir = directions.get(i);
            double verticalFactor = Math.abs(dir.y);
            double multiplier = 1.0 - VERTICAL_PENALTY * verticalFactor;
            float penetration = (float) (MAX_PENETRATION * Math.max(0.0, multiplier));
            boolean hasBranches = RANDOM.nextFloat() < BRANCH_CHANCE;
            castRay(level, center, dir, source, allEntities, MAX_RANGE, penetration, hasBranches);
        }

        // Припекаем/зачищаем накопленные заготовки батчем (безопасно для FPS/TPS)
        drainBasaltJobs(level);

        // Лучи летят долго и в это время могут нападать дропы от мобов/блоков — подчищаем
        discardItemsNearby(level, center, MAX_RANGE);

        if (endIdx < directions.size()) {
            level.getServer().tell(new net.minecraft.server.TickTask(1, () ->
                    processRayBatch(level, center, source, directions, endIdx, batchSize)));
        }
    }

    private static void castRay(ServerLevel level, Vec3 origin, Vec3 direction, Entity source,
                                List<LivingEntity> allEntities, float maxRange, float maxPenetration,
                                boolean canBranch) {
        float penetration = maxPenetration;
        float destroyFloor = maxPenetration * DESTROY_THRESHOLD;
        float distance = 0;
        Set<Integer> hitIds = new HashSet<>();
        Direction axis = majorAxisDirection(direction);

        while (distance < maxRange && penetration > 0) {
            Vec3 current = origin.add(direction.scale(distance));
            BlockPos pos = new BlockPos((int) Math.floor(current.x), (int) Math.floor(current.y), (int) Math.floor(current.z));

            BlockState state = level.getBlockState(pos);

            // Запёкшийся базальт термостоек — луч упирается в него и гаснет.
            // Так край кратера остаётся базальтовым ободом.
            if (state.is(ModBlocks.BASALT_SCORCHED.get()) || state.is(ModBlocks.BASALT_ROUGH.get())) {
                break;
            }

            if (state.getFluidState().isSource() || state.getBlock() instanceof LiquidBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                distance += 1.0f;
                continue;
            }

            if (!state.isAir()) {
                float hardness = state.getDestroySpeed(level, pos);
                if (hardness < 0) break;

                float cost = Math.max(1.0f, hardness);
                if (penetration < cost) break;

                // Порог 60%: если осталось меньше — блок не пробивается, это край кратера
                if (penetration <= destroyFloor) break;

                penetration -= cost;
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

                // Припекаем/зачищаем выживших соседей (отложенно, без траты пробития)
                enqueueBasaltEdges(level, origin, pos, axis, penetration);
            }

            // Ветвление только на первом шаге
            if (canBranch && distance < 1.0f) {
                spawnBranches(level, origin, direction, source, allEntities, maxPenetration);
                canBranch = false;
            }

            // Оплачиваем проход шага луча
            distance += 1.0f;
            penetration -= 1.0f;

            // Если пробитие кончилось — обрываем до нанесения урона
            if (penetration <= 0) break;

            // Урон мобам — только от РЕАЛЬНО оставшегося пробития
            for (LivingEntity entity : allEntities) {
                if (entity == source || hitIds.contains(entity.getId())) continue;
                if (entity.distanceToSqr(current.x, current.y, current.z) < 2.25) {
                    // Урон только если блоки защиты на пути действительно пробиты
                    if (!isPathClear(level, origin, entity)) continue;
                    float damage = penetration * RAY_DAMAGE_PER_PENETRATION;
                    if (damage > 0) {
                        entity.hurt(level.damageSources().explosion(source, source), damage);
                        hitIds.add(entity.getId());
                    }
                }
            }
        }
    }

    /**
     * Откладывает обработку соседних блоков разрушенной клетки:
     * - крепкие цельные блоки запекутся в базальт;
     * - блоки с крайне низкой прочностью (листва, цветы, трава…) просто уничтожатся.
     * Не считает пробитие как расход — лишь требует, чтобы луч был ещё жив
     * (penetration > 0) на момент разрушения.
     */
    private static void enqueueBasaltEdges(ServerLevel level, Vec3 center, BlockPos destroyedPos,
                                           Direction axis, float penetration) {
        if (penetration <= 0) return;
        for (Direction d : Direction.values()) {
            // Вдоль оси луча припекать нечего: спереди «съест» сам луч, сзади уже обработано.
            // У ударной волны ось отсутствует (axis == null) — печём со всех сторон.
            if (axis != null && (d == axis || d == axis.getOpposite())) continue;

            BlockPos neighbor = destroyedPos.relative(d);
            BlockState ns = level.getBlockState(neighbor);
            if (ns.isAir()) continue;
            if (ns.is(ModBlocks.BASALT_SCORCHED.get()) || ns.is(ModBlocks.BASALT_ROUGH.get())) continue;

            float neighborHardness = ns.getDestroySpeed(level, neighbor);
            if (neighborHardness < 0) continue; // нексрушимые (бедрок и т.п.)

            boolean weak = neighborHardness <= WEAK_BLOCK_HARDNESS;
            if (BASALT_QUEUE.size() < BASALT_QUEUE_CAP) {
                BASALT_QUEUE.addLast(new BasaltJob(neighbor, center, weak));
            }
        }
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
            if (s.is(ModBlocks.BASALT_SCORCHED.get()) || s.is(ModBlocks.BASALT_ROUGH.get())) continue;

            if (job.destroy()) {
                level.setBlock(job.pos(), Blocks.AIR.defaultBlockState(), 3);
            } else {
                int light = gradientLevel(job.pos());
                level.setBlock(job.pos(),
                        ModBlocks.BASALT_SCORCHED.get().defaultBlockState().setValue(ScorchedBasaltBlock.LIGHT, light), 3);
            }
        }
        if (!BASALT_QUEUE.isEmpty()) {
            level.getServer().tell(new net.minecraft.server.TickTask(1, () -> drainBasaltJobs(level)));
        }
    }

    /**
     * Ступень осветления базальта по расстоянию от центрального нижнего блока дна кратера:
     * 0 у самого дна, максимум (MAX_LIGHT) на расстоянии GRADIENT_RADIUS — +50% осветления.
     */
    private static int gradientLevel(BlockPos pos) {
        BlockPos anchor = GRADIENT_ANCHOR;
        if (anchor == null) {
            anchor = pos; // запасной вариант, если якорь ещё не найден
        }
        double dx = pos.getX() - anchor.getX();
        double dy = pos.getY() - anchor.getY();
        double dz = pos.getZ() - anchor.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double t = Math.min(1.0, dist / GRADIENT_RADIUS);
        return (int) Math.round(t * (ScorchedBasaltBlock.MAX_LIGHT));
    }

    /**
     * Ищет дно кратера для якоря градиента: первый непустой блок
     * в колонне под центром взрыва (это и есть дно будущей воронки,
     * которое волна/лучи потом запекут в базальт).
     */
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

    /** Ось, вдоль которой летит луч (по доминирующей компоненте направления). */
    private static Direction majorAxisDirection(Vec3 dir) {
        double ax = Math.abs(dir.x);
        double ay = Math.abs(dir.y);
        double az = Math.abs(dir.z);
        if (ax >= ay && ax >= az) return dir.x >= 0 ? Direction.EAST : Direction.WEST;
        if (ay >= ax && ay >= az) return dir.y >= 0 ? Direction.UP : Direction.DOWN;
        return dir.z >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static void spawnBranches(ServerLevel level, Vec3 branchOrigin, Vec3 parentDir,
                                      Entity source, List<LivingEntity> allEntities, float parentPenetration) {
        float branchRange = MAX_RANGE * BRANCH_RANGE_MULTIPLIER;
        float branchPenetration = parentPenetration * BRANCH_PENETRATION_MULTIPLIER;

        for (int b = 0; b < 2; b++) {
            Vec3 branchDir = deviateDirection(parentDir, BRANCH_ANGLE);
            double verticalFactor = Math.abs(branchDir.y);
            double multiplier = 1.0 - VERTICAL_PENALTY * verticalFactor;
            float finalPenetration = branchPenetration * (float) multiplier;
            castRay(level, branchOrigin, branchDir, source, allEntities, branchRange, finalPenetration, false);
        }
    }

    private static Vec3 randomDirection() {
        double theta = RANDOM.nextDouble() * 2.0 * Math.PI;
        double phi = Math.acos(2.0 * RANDOM.nextDouble() - 1.0);
        return new Vec3(Math.sin(phi) * Math.cos(theta), Math.sin(phi) * Math.sin(theta), Math.cos(phi));
    }

    private static Vec3 deviateDirection(Vec3 original, double maxAngle) {
        double theta = RANDOM.nextDouble() * 2.0 * Math.PI;
        double phi = RANDOM.nextDouble() * maxAngle;

        Vec3 arbitrary = Math.abs(original.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 xAxis = original.cross(arbitrary).normalize();
        if (xAxis.lengthSqr() < 0.001) {
            xAxis = new Vec3(1, 0, 0);
        }
        Vec3 yAxis = original.cross(xAxis).normalize();

        double sinPhi = Math.sin(phi);
        double cosPhi = Math.cos(phi);

        Vec3 deviated = original.scale(cosPhi)
                .add(xAxis.scale(Math.cos(theta) * sinPhi))
                .add(yAxis.scale(Math.sin(theta) * sinPhi));

        return deviated.normalize();
    }
}