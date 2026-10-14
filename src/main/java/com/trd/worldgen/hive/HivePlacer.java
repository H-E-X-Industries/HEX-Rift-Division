package com.trd.worldgen.hive;

import com.trd.api.hive.HiveNetwork;
import com.trd.api.hive.HiveNetworkManager;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.hive.DepthWormNestBlockEntity;
import com.trd.block.entity.hive.HiveSoilBlockEntity;
import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Общая часть расстановки улья глубинных червей.
 * <p>
 * Вынесена отдельно от фичи, потому что улей строится двумя разными путями:
 * мягкие породы видны ещё во время генерации чанка (фича {@code trd:depth_worm_hive}),
 * а структуры появляются позже и обрабатываются отдельным пост-проходом
 * {@link StructureHivePass}. Логика проверок и условий тут общая.
 */
public final class HivePlacer {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int MIN_Y = -60;
    public static final int MAX_Y = -5;

    /**
     * Что улей вправе заместить: обычная порода, кирпичи из камня и дипслейта, земля,
     * гравий. Список белый, поэтому руды, обсидиан, древний мусор, укреплённый дипслейт,
     * спавнеры, сундуки и любые блоки других модов по умолчанию не трогаются — улей
     * просто не построится на таком полу. Свои блоки мод может открыть датапаком.
     */
    public static final TagKey<Block> REPLACEABLE_TAG = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "depth_worm_hive_replaceable"));

    /** Радиус кольца, в котором ищется пол улья вокруг якоря. */
    public static final int FLOOR_SEARCH_RADIUS = 4;
    private static final int FLOOR_SEARCH_UP = 3;
    private static final int FLOOR_SEARCH_DOWN = 5;

    private HivePlacer() {
    }

    /** Ищет первый блок нужного уровня в колонке сверху вниз. */
    public static BlockPos findAnchor(LevelAccessor level, int cx, int cz, TagKey<Block> tier) {
        for (int y = MAX_Y; y >= MIN_Y; y--) {
            BlockPos pos = new BlockPos(cx, y, cz);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.is(tier)) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Строит улей вокруг якоря.
     *
     * @return true, если улей поставлен
     */
    public static boolean place(LevelAccessor level, ServerLevel serverLevel, BlockPos anchor, RandomSource random) {
        // 1. Ближайший подходящий пол вокруг якоря. Кольца обходятся по порядку,
        // поэтому первый результат — всегда самый близкий.
        BlockPos validFloorPos = findFloorNear(level, anchor);
        if (validFloorPos == null) {
            return false;
        }

        // Мягкий якорь мог уже построить улей во время генерации чанка —
        // второй раз поверх него строить нельзя.
        if (hiveAlreadyThere(level, validFloorPos)) {
            return false;
        }

        // 2. Decide core count (1 to 2 cores)
        int coreCount = random.nextInt(2) + 1;
        BlockPos core1 = validFloorPos;
        BlockPos core2 = null;

        if (coreCount == 2) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos cand = validFloorPos.relative(dir);
                if (isReplaceableGround(level.getBlockState(cand)) &&
                    !level.getBlockState(cand.below()).isAir() &&
                    level.getFluidState(cand.below()).isEmpty() &&
                    level.getBlockState(cand.above()).isAir()) {
                    core2 = cand;
                    break;
                }
            }
            if (core2 == null) {
                coreCount = 1;
            }
        }

        Set<BlockPos> corePositions = new HashSet<>();
        corePositions.add(core1);
        if (core2 != null) corePositions.add(core2);

        // 3. Determine hive soil blocks:
        // Cores must always be surrounded by Hive Soil.
        // Every soil block MUST have solid ground beneath it (not air, not fluid).
        Set<BlockPos> soilPositions = new HashSet<>();
        for (BlockPos c : corePositions) {
            // Under each core: Hive Soil (embedded in floor)
            BlockPos underCore = c.below();
            if (isReplaceableGround(level.getBlockState(underCore)) &&
                !level.getBlockState(underCore.below()).isAir() &&
                level.getFluidState(underCore.below()).isEmpty()) {
                soilPositions.add(underCore);
            }

            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (dx * dx + dz * dz > 5) continue;
                    // Find actual floor surface for this (dx, dz) at or above core level
                    BlockPos bestSurface = null;
                    for (int dy = 1; dy >= 0; dy--) {
                        BlockPos cand = c.offset(dx, dy, dz);
                        BlockState candState = level.getBlockState(cand);
                        BlockState candBelow = level.getBlockState(cand.below());
                        if (isReplaceableGround(candState) &&
                            level.getBlockState(cand.above()).isAir() &&
                            !candBelow.isAir() && candBelow.getFluidState().isEmpty() &&
                            !candState.is(Blocks.BEDROCK)) {
                            bestSurface = cand;
                            break;
                        }
                    }
                    if (bestSurface != null && !corePositions.contains(bestSurface)) {
                        soilPositions.add(bestSurface);
                    }
                }
            }
        }

        // 4. Validate conditions:
        // Condition A: NO AIR BELOW CORES OR SOIL!
        // "доступ воздуха снизу блокаем для того, чтобы ульи не спавнились на потолках."
        for (BlockPos c : corePositions) {
            BlockState belowState = level.getBlockState(c.below());
            if (belowState.isAir() || !belowState.getFluidState().isEmpty()) {
                return false; // Core must not be on ceiling or in air!
            }
            if (level.getBlockState(c).is(Blocks.BEDROCK)) {
                return false;
            }
        }

        // Condition B: Cores must be surrounded by Hive Soil (at least 2 adjacent soil blocks per core)
        for (BlockPos c : corePositions) {
            int adjacentSoil = 0;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (soilPositions.contains(c.relative(dir))) {
                    adjacentSoil++;
                }
            }
            if (adjacentSoil < 2) {
                return false; // Skip rotation if not properly surrounded
            }
        }

        Set<BlockPos> allHiveBlocks = new HashSet<>();
        allHiveBlocks.addAll(corePositions);
        allHiveBlocks.addAll(soilPositions);

        // Condition C: At least 3 blocks of the hive must have access to air (not from below):
        // "одним из условий спавна является доступ у минимум 3 блоков уля к блоку воздуху, но не снизу, иначе скип ротации"
        int airExposedCount = 0;
        for (BlockPos p : allHiveBlocks) {
            if (level.getBlockState(p.above()).isAir() && level.getFluidState(p.above()).isEmpty()) {
                airExposedCount++;
            } else {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos side = p.relative(dir);
                    if (level.getBlockState(side).isAir() && level.getFluidState(side).isEmpty()) {
                        airExposedCount++;
                        break;
                    }
                }
            }
        }

        if (airExposedCount < 3) {
            return false; // Skip rotation!
        }

        // Condition D: Safety check - no lava or water on hive blocks or directly above
        for (BlockPos p : allHiveBlocks) {
            if (!level.getFluidState(p).isEmpty() || !level.getFluidState(p.above()).isEmpty()) {
                return false;
            }
        }

        // 5. Generate blocks:
        UUID networkId = UUID.randomUUID();
        int points = random.nextInt(31) + 30; // 30 to 60 points

        // Place Hive Soil around cores
        for (BlockPos soilPos : soilPositions) {
            level.setBlock(soilPos, ModBlocks.HIVE_SOIL.get().defaultBlockState(), 3);
            BlockEntity be = level.getBlockEntity(soilPos);
            if (be instanceof HiveSoilBlockEntity soil) {
                soil.setNetworkId(networkId);
            }
        }

        // Place Hive Cores (Nests)
        for (BlockPos corePos : corePositions) {
            level.setBlock(corePos, ModBlocks.DEPTH_WORM_NEST.get().defaultBlockState(), 3);
            BlockEntity be = level.getBlockEntity(corePos);
            if (be instanceof DepthWormNestBlockEntity nest) {
                nest.setNetworkId(networkId);
            }
        }

        // 6. Register into HiveNetworkManager & assign 30-60 points (network spawns worms on its own)
        HiveNetworkManager manager = HiveNetworkManager.get(serverLevel);
        if (manager != null) {
            for (BlockPos corePos : corePositions) {
                manager.addNode(networkId, corePos, true);
            }
            for (BlockPos soilPos : soilPositions) {
                manager.addNode(networkId, soilPos, false);
            }
            HiveNetwork network = manager.getNetwork(networkId);
            if (network != null) {
                network.killsPool = points;
                network.addPoints(0, serverLevel);
            }
        }

        LOGGER.debug("[HEX-Rift-Division] Spawned Depth Worm Hive at {} (cores: {}, points: {})",
                core1, coreCount, points);
        return true;
    }

    private static BlockPos findFloorNear(LevelAccessor level, BlockPos anchor) {
        int ax = anchor.getX();
        int ay = anchor.getY();
        int az = anchor.getZ();

        for (int r = 0; r <= FLOOR_SEARCH_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = ax + dx;
                    int z = az + dz;
                    for (int y = ay + FLOOR_SEARCH_UP; y >= ay - FLOOR_SEARCH_DOWN; y--) {
                        if (y < MIN_Y || y > MAX_Y) continue;
                        BlockPos floor = new BlockPos(x, y, z);
                        if (isValidFloor(level, floor)) {
                            return floor;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isValidFloor(LevelAccessor level, BlockPos floor) {
        if (!isReplaceableGround(level.getBlockState(floor))) return false;
        BlockPos air = floor.above();
        // At least 2 blocks of air above the floor for the worms and for the player to walk in.
        if (!level.getBlockState(air).isAir()) return false;
        if (!level.getBlockState(air.above()).isAir()) return false;
        return level.getFluidState(air).isEmpty();
    }

    private static boolean hiveAlreadyThere(LevelAccessor level, BlockPos floor) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockState state = level.getBlockState(floor.offset(dx, dy, dz));
                    if (state.is(ModBlocks.HIVE_SOIL.get()) || state.is(ModBlocks.DEPTH_WORM_NEST.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isReplaceableGround(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (state.is(Blocks.BEDROCK)) return false;
        if (state.is(Blocks.BARRIER)) return false;
        // Белый список: крепкую добычу и блоки других модов улей не замещает.
        return state.is(REPLACEABLE_TAG);
    }
}