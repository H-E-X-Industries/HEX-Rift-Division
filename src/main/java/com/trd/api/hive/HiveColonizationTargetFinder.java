package com.trd.api.hive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class HiveColonizationTargetFinder {
    private static final int MAX_ATTEMPTS = 60;
    private static final int MIN_DISTANCE = 50;
    private static final int MAX_DISTANCE = 120;
    private static final double RAYCAST_STEP = 2.5;

    public static BlockPos findTarget(Level level, BlockPos homeCenter) {
        if (level.isClientSide) return null;
        Random random = new Random(homeCenter.asLong() ^ level.getGameTime());

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE);

            int tx = homeCenter.getX() + (int) (Math.cos(angle) * distance);
            int tz = homeCenter.getZ() + (int) (Math.sin(angle) * distance);

            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(tx, homeCenter.getY(), tz));
            int startY = Math.max(level.getMinBuildHeight() + 5, Math.min(surface.getY(), homeCenter.getY() + 20));

            BlockPos bestPos = null;
            for (int dy = -15; dy <= 8; dy++) {
                BlockPos testPos = new BlockPos(tx, startY + dy, tz);
                if (isValidColonizationSpot(level, testPos)) {
                    bestPos = testPos;
                    break;
                }
            }
            if (bestPos == null) continue;
            if (!hasRoughPath(level, homeCenter, bestPos)) continue;
            return bestPos;
        }
        return null;
    }

    private static boolean isValidColonizationSpot(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() || !state.getFluidState().isEmpty()) return false;

        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.isAir() || !belowState.getFluidState().isEmpty()) return false;

        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (!level.getBlockState(pos.offset(x, y, z)).getFluidState().isEmpty()) return false;
                }
            }
        }

        for (Direction dir : Direction.values()) {
            BlockState n = level.getBlockState(pos.relative(dir));
            if (!n.isAir() && n.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    private static boolean hasRoughPath(Level level, BlockPos from, BlockPos to) {
        Vec3 start = new Vec3(from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5);
        Vec3 end = new Vec3(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5);
        Vec3 dir = end.subtract(start);
        double length = dir.length();
        if (length < 1) return true;
        dir = dir.normalize();

        double checkDist = 0;
        while (checkDist < length) {
            checkDist += RAYCAST_STEP;
            if (checkDist > length) checkDist = length;
            Vec3 point = start.add(dir.scale(checkDist));
            BlockPos pos = BlockPos.containing(point);
            BlockState state = level.getBlockState(pos);

            // ⭐ НЕЛЬЗЯ: путь проходит через жидкость
            if (!state.getFluidState().isEmpty()) return false;

            if (state.isSolidRender(level, pos)) {
                boolean hasGapNearby = false;
                for (int dx = -1; dx <= 1 && !hasGapNearby; dx++) {
                    for (int dy = -1; dy <= 1 && !hasGapNearby; dy++) {
                        for (int dz = -1; dz <= 1 && !hasGapNearby; dz++) {
                            if (!level.getBlockState(pos.offset(dx, dy, dz)).isSolidRender(level, pos.offset(dx, dy, dz))) {
                                hasGapNearby = true;
                            }
                        }
                    }
                }
                if (!hasGapNearby) return false;
            }
        }
        return true;
    }
}