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
    private static final int MIN_DISTANCE = 20;
    private static final int MAX_DISTANCE = 45;
    private static final double SAMPLE_STEP = 3.5;

    public static BlockPos findTarget(Level level, BlockPos homeCenter) {
        if (level.isClientSide) return null;
        Random random = new Random(homeCenter.asLong() ^ level.getGameTime());
        boolean isUnderground = !level.canSeeSky(homeCenter);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);

            int tx = homeCenter.getX() + (int) (Math.cos(angle) * distance);
            int tz = homeCenter.getZ() + (int) (Math.sin(angle) * distance);

            BlockPos bestPos = null;

            if (isUnderground) {
                // In caves, search around homeCenter's Y-level first
                int centerY = homeCenter.getY();
                for (int dy = 0; dy <= 12; dy++) {
                    int[] yOffsets = dy == 0 ? new int[]{0} : new int[]{dy, -dy};
                    for (int yOff : yOffsets) {
                        int testY = centerY + yOff;
                        if (testY < level.getMinBuildHeight() + 3 || testY > level.getMaxBuildHeight() - 3) continue;
                        BlockPos testPos = new BlockPos(tx, testY, tz);
                        if (isValidColonizationSpot(level, testPos)) {
                            bestPos = testPos;
                            break;
                        }
                    }
                    if (bestPos != null) break;
                }
            } else {
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(tx, homeCenter.getY(), tz));
                int startY = Math.max(level.getMinBuildHeight() + 5, Math.min(surface.getY(), homeCenter.getY() + 15));
                for (int dy = -6; dy <= 6; dy++) {
                    BlockPos testPos = new BlockPos(tx, startY + dy, tz);
                    if (isValidColonizationSpot(level, testPos)) {
                        bestPos = testPos;
                        break;
                    }
                }
            }

            if (bestPos == null) continue;
            if (hasNearbyLava(level, bestPos, 5)) continue;
            if (!hasWalkableGroundPath(level, homeCenter, bestPos)) continue;
            return bestPos;
        }
        return null;
    }

    private static boolean hasNearbyLava(Level level, BlockPos pos, int radius) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-radius, -2, -radius), pos.offset(radius, 2, radius))) {
            if (level.getFluidState(p).is(net.minecraft.tags.FluidTags.LAVA)) return true;
            if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.LAVA)) return true;
        }
        return false;
    }

    private static boolean isValidColonizationSpot(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() || !state.getFluidState().isEmpty()) return false;

        BlockState headState = level.getBlockState(pos.above());
        if (!headState.isAir() || !headState.getFluidState().isEmpty()) return false;

        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.isAir() || !belowState.getFluidState().isEmpty()) return false;
        if (belowState.getCollisionShape(level, below).isEmpty() && !belowState.isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }

        // Must not be near water or lava
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (!level.getBlockState(pos.offset(x, y, z)).getFluidState().isEmpty()) return false;
                }
            }
        }

        // Must have at least one solid neighbour to connect/anchor
        for (Direction dir : Direction.values()) {
            BlockState n = level.getBlockState(pos.relative(dir));
            if (!n.isAir() && n.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    private static boolean hasWalkableGroundPath(Level level, BlockPos from, BlockPos to) {
        Vec3 start = new Vec3(from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5);
        Vec3 end = new Vec3(to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5);
        Vec3 dir = end.subtract(start);
        double length = dir.length();
        if (length < 1) return true;
        dir = dir.normalize();

        double checkDist = 0;
        while (checkDist < length) {
            checkDist += SAMPLE_STEP;
            if (checkDist > length) checkDist = length;
            Vec3 point = start.add(dir.scale(checkDist));
            BlockPos centerPos = BlockPos.containing(point);

            // Verify there is a floor near centerPos within dy +/- 4 that is not fluid and has air above it
            boolean hasFloor = false;
            for (int dy = -4; dy <= 4; dy++) {
                BlockPos floorCheck = centerPos.above(dy);
                BlockState s = level.getBlockState(floorCheck);
                BlockState aboveS = level.getBlockState(floorCheck.above());

                if (!s.isAir() && s.getFluidState().isEmpty() && !s.getCollisionShape(level, floorCheck).isEmpty()) {
                    if (aboveS.isAir() && aboveS.getFluidState().isEmpty()) {
                        // Check if lava is right here
                        if (!level.getFluidState(floorCheck.above()).is(net.minecraft.tags.FluidTags.LAVA)) {
                            hasFloor = true;
                            break;
                        }
                    }
                }
            }

            if (!hasFloor) {
                return false;
            }
        }
        return true;
    }
}
