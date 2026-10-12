package com.trd.worldgen.feature.vien;

import com.mojang.serialization.Codec;
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
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class DepthWormHiveFeature extends Feature<NoneFeatureConfiguration> {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Блоки-маркеры «здесь идёт структура»: спавнер, рельсы, сундук, факел, паутина,
     * замшелый булыжник, блоки крепостей, древних городов и триалов и т.п.
     * Структуры других модов подключаются датапаком одной записью в этот тег,
     * поэтому список лежит в теге, а не в коде.
     */
    public static final TagKey<Block> ANCHOR_TAG = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "depth_worm_hive_anchors"));

    private static final int MIN_Y = -60;
    private static final int MAX_Y = -5;
    /** Сколько колонн прочесать в поисках якоря (одна колонка = 56 чтений). */
    private static final int ANCHOR_COLUMNS = 2;
    /** Радиус кольца, в котором ищется пол улья вокруг якоря. */
    private static final int FLOOR_SEARCH_RADIUS = 4;
    private static final int FLOOR_SEARCH_UP = 3;
    private static final int FLOOR_SEARCH_DOWN = 5;

    public DepthWormHiveFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        // 1. Find a structure anchor. Without one there is no hive at all: this is what
        //    keeps the feature from scanning an entire cave column per attempt, which is
        //    what overloaded chunk loading.
        BlockPos anchor = null;
        for (int attempt = 0; attempt < ANCHOR_COLUMNS && anchor == null; attempt++) {
            int cx = (origin.getX() & ~15) + random.nextInt(16);
            int cz = (origin.getZ() & ~15) + random.nextInt(16);
            for (int y = MAX_Y; y >= MIN_Y; y--) {
                BlockState state = level.getBlockState(new BlockPos(cx, y, cz));
                if (!state.isAir() && state.is(ANCHOR_TAG)) {
                    anchor = new BlockPos(cx, y, cz);
                    break;
                }
            }
        }

        if (anchor == null) {
            return false;
        }

        // 2. Walk outwards from the anchor to the nearest valid hive floor.
        //    Rings are visited in order, so the first hit is always the closest one.
        BlockPos validFloorPos = findFloorNear(level, anchor);
        if (validFloorPos == null) {
            return false;
        }

        // 3. Decide core count (1 to 2 cores)
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

        // 4. Determine hive soil blocks:
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
                    // Find actual floor surface for this (dx, dz) within +/- 1 block of core level
                    BlockPos bestSurface = null;
                    for (int dy = 1; dy >= 0; dy--) {
                        BlockPos cand = c.offset(dx, dy, dz);
                        BlockState candState = level.getBlockState(cand);
                        BlockState candBelow = level.getBlockState(cand.below());
                        if (isReplaceableGround(candState) &&
                            level.getBlockState(cand.above()).isAir() &&
                            !candBelow.isAir() && candBelow.getFluidState().isEmpty() &&
                            !candState.is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
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

        // 5. Validate conditions:
        // Condition A: NO AIR BELOW CORES OR SOIL!
        // "доступ воздуха снизу блокаем для того, чтобы ульи не спавнились на потолках."
        for (BlockPos c : corePositions) {
            BlockState belowState = level.getBlockState(c.below());
            if (belowState.isAir() || !belowState.getFluidState().isEmpty()) {
                return false; // Core must not be on ceiling or in air!
            }
            if (level.getBlockState(c).is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
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

        // 6. Generate blocks:
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

        // 7. Register into HiveNetworkManager & assign 30-60 points (network spawns worms on its own)
        ServerLevel serverLevel = level.getLevel();
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

        LOGGER.debug("[HEX-Rift-Division] Spawned Depth Worm Hive at {} (cores: {}, points: {})", core1, coreCount, points);
        return true;
    }

    private static BlockPos findFloorNear(WorldGenLevel level, BlockPos anchor) {
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

    private static boolean isValidFloor(WorldGenLevel level, BlockPos floor) {
        if (!isReplaceableGround(level.getBlockState(floor))) return false;
        BlockPos air = floor.above();
        // At least 2 blocks of air above the floor for the worms and for the player to walk in.
        if (!level.getBlockState(air).isAir()) return false;
        if (!level.getBlockState(air.above()).isAir()) return false;
        return level.getFluidState(air).isEmpty();
    }

    private static boolean isReplaceableGround(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (state.is(net.minecraft.world.level.block.Blocks.BEDROCK)) return false;
        if (state.is(net.minecraft.world.level.block.Blocks.BARRIER)) return false;
        return state.isSolid();
    }
}
