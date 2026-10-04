package com.trd.entity.mobs.depth_worm;

import com.trd.api.hive.HiveNetwork;
import com.trd.api.hive.HiveNetworkManager;
import com.trd.api.hive.HiveNetworkMember;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.hive.DepthWormNestBlockEntity;
import com.trd.block.entity.hive.HiveSoilBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;
import javax.annotation.Nullable;

public class ReturnToHiveGoal extends Goal {
    private final DepthWormEntity worm;
    private BlockPos targetPos;
    private boolean targetIsSoil = false;
    private int stuckTicks = 0;
    private BlockPos lastPos = BlockPos.ZERO;
    private static final int STUCK_THRESHOLD = 40;

    private enum ApproachPhase { NAVIGATING, SLIDING, ENTERING }
    private ApproachPhase phase = ApproachPhase.NAVIGATING;
    private int slidingTicks = 0;
    private boolean routerActive = false;
    private BlockPos routerTarget = null;
    private static final double ROUTER_DISABLE_DISTANCE_SQ = 256.0;
    private static final double ROUTER_ARRIVE_DISTANCE_SQ = 4.0;

    private BlockPos relayTarget = null;
    private static final double RELAY_SWITCH_DISTANCE_SQ = 9.0;
    private static final double LONG_RANGE_THRESHOLD_SQ = 4096.0;

    private int pathRecalcCooldown = 0;

    public ReturnToHiveGoal(DepthWormEntity worm) {
        this.worm = worm;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private UUID getWormNetworkId() {
        BlockPos bound = worm.getBoundNestPos();
        if (bound != null) {
            BlockEntity be = worm.level().getBlockEntity(bound);
            if (be instanceof HiveNetworkMember member) return member.getNetworkId();
        }
        if (targetPos != null) {
            BlockEntity be = worm.level().getBlockEntity(targetPos);
            if (be instanceof HiveNetworkMember member) return member.getNetworkId();
        }
        return null;
    }

    private BlockPos findNearestRelay() {
        BlockPos lastExit = worm.getLastExitPos();
        if (lastExit != null) {
            double distToExit = worm.distanceToSqr(
                    lastExit.getX() + 0.5, lastExit.getY() + 0.5, lastExit.getZ() + 0.5);
            double distToTarget = targetPos != null ? worm.distanceToSqr(
                    targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5) : Double.MAX_VALUE;

            if (distToExit < distToTarget && distToExit > 4.0 && worm.level().isLoaded(lastExit)) {
                return lastExit;
            }
        }

        UUID netId = getWormNetworkId();
        if (netId == null) return null;
        HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
        if (manager == null) return null;
        HiveNetwork network = manager.getNetwork(netId);
        if (network == null || network.members.isEmpty()) return null;

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 wormPos = worm.position();

        for (BlockPos member : network.members) {
            if (!worm.level().isLoaded(member)) continue;

            double distToWorm = wormPos.distanceToSqr(
                    member.getX() + 0.5, member.getY() + 0.5, member.getZ() + 0.5);

            if (distToWorm < 4.0) continue;

            double distToTarget = targetPos != null ? targetPos.distSqr(member) : 0;
            double score = distToWorm + distToTarget * 0.3;

            if (score < bestScore) {
                bestScore = score;
                best = member;
            }
        }
        return best;
    }

    @Override
    public boolean canUse() {
        if (worm.isColonist()) return false;

        if (worm.isRetreating()) {
            if (worm.getTarget() != null) worm.setTarget(null);
        } else {
            LivingEntity target = worm.getTarget();
            if (target != null && target.isAlive()) return false;
        }

        BlockPos boundNest = worm.getBoundNestPos();
        if (boundNest != null) {
            if (worm.level().isLoaded(boundNest)) {
                if (isValidEntryPoint(boundNest)) {
                    this.targetPos = boundNest;
                    this.targetIsSoil = isSoil(boundNest);
                    return true;
                }
            } else {
                this.targetPos = boundNest;
                this.targetIsSoil = false;
                return true;
            }
        }

        boolean found = findAndSetNearestEntry();
        if (!found && worm.isRetreating()) {
            worm.setRetreating(false);
        }
        return found;
    }

    private boolean findAndSetNearestEntry() {
        BlockPos entry = findNearestEntryPoint();
        if (entry != null) {
            this.targetPos = entry;
            this.targetIsSoil = isSoil(entry);
            if (!targetIsSoil) {
                worm.bindToNest(entry);
            } else {
                worm.bindToNest(findNearestNest(entry));
            }
            return true;
        }
        return false;
    }

    private boolean isValidEntryPoint(BlockPos pos) {
        BlockEntity be = worm.level().getBlockEntity(pos);
        if (be instanceof DepthWormNestBlockEntity nest) return nest.getNetworkId() != null;
        if (be instanceof HiveSoilBlockEntity soil) {
            UUID netId = soil.getNetworkId();
            if (netId == null) return false;
            HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
            if (manager != null) {
                HiveNetwork network = manager.getNetwork(netId);
                return network != null;
            }
        }
        return false;
    }

    private boolean isSoil(BlockPos pos) {
        return worm.level().getBlockState(pos).is(ModBlocks.HIVE_SOIL.get());
    }

    private BlockPos findNearestNest(BlockPos entryPos) {
        HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
        if (manager == null) return entryPos;
        BlockEntity be = worm.level().getBlockEntity(entryPos);
        if (!(be instanceof HiveNetworkMember member) || member.getNetworkId() == null) return entryPos;

        HiveNetwork network = manager.getNetwork(member.getNetworkId());
        if (network == null) return entryPos;

        BlockPos nearest = null;
        double minDist = Double.MAX_VALUE;
        for (BlockPos nestPos : network.wormCounts.keySet()) {
            double dist = entryPos.distSqr(nestPos);
            if (dist < minDist) { minDist = dist; nearest = nestPos; }
        }
        return nearest != null ? nearest : entryPos;
    }

    private BlockPos findNearestEntryPoint() {
        BlockPos bound = worm.getBoundNestPos();
        if (bound != null && worm.level().isLoaded(bound) && isValidHiveEntry(bound)) {
            return bound;
        }

        UUID netId = getWormNetworkId();
        HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
        if (manager != null && netId != null) {
            HiveNetwork network = manager.getNetwork(netId);
            if (network != null && !network.members.isEmpty()) {
                BlockPos best = null;
                double bestDist = Double.MAX_VALUE;
                Vec3 wormPos = worm.position();
                for (BlockPos p : network.members) {
                    if (worm.level().isLoaded(p)) {
                        double d = wormPos.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                        if (d < bestDist) {
                            bestDist = d;
                            best = p;
                        }
                    }
                }
                if (best != null) return best;
            }
        }

        BlockPos wormPos = worm.blockPosition();
        BlockPos bestEntry = null;
        double bestDist = Double.MAX_VALUE;
        int radius = worm.isRetreating() ? 64 : 24;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos p = wormPos.offset(x, y, z);
                    if (!isValidHiveEntry(p)) continue;

                    double d = worm.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                    if (d < bestDist) {
                        bestDist = d;
                        bestEntry = p.immutable();
                    }
                }
            }
        }
        return bestEntry;
    }

    private boolean isValidHiveEntry(BlockPos pos) {
        if (worm.level().getBlockState(pos).is(ModBlocks.HIVE_ROOTS.get())) return false;

        BlockEntity be = worm.level().getBlockEntity(pos);
        if (be instanceof DepthWormNestBlockEntity nest) {
            return nest.getNetworkId() != null;
        }
        if (be instanceof HiveSoilBlockEntity) {
            return isValidSoilEntry(pos);
        }
        return false;
    }

    private boolean isValidSoilEntry(BlockPos pos) {
        BlockEntity be = worm.level().getBlockEntity(pos);
        if (!(be instanceof HiveSoilBlockEntity soil) || soil.getNetworkId() == null) return false;
        HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
        if (manager == null) return false;
        HiveNetwork network = manager.getNetwork(soil.getNetworkId());
        return network != null;
    }

    @Override
    public void start() {
        this.phase = ApproachPhase.NAVIGATING;
        this.stuckTicks = 0;
        this.slidingTicks = 0;
        this.lastPos = worm.blockPosition();
        this.routerActive = false;
        this.routerTarget = null;
        this.relayTarget = null;
        this.pathRecalcCooldown = 0;
    }

    @Override
    public void tick() {
        if (worm.isRetreating() && worm.getTarget() != null) {
            worm.setTarget(null);
        }

        if (targetPos == null) return;

        double targetX = targetPos.getX() + 0.5;
        double targetZ = targetPos.getZ() + 0.5;
        double targetY = targetPos.getY() + 0.5;

        Vec3 wormPos = worm.position();
        double distSq = wormPos.distanceToSqr(targetX, targetY, targetZ);
        double dy = Math.abs(targetY - wormPos.y);
        BlockPos currentBlockPos = worm.blockPosition();

        if (isValidHiveEntry(currentBlockPos)) {
            this.targetPos = currentBlockPos;
            this.targetIsSoil = isSoil(currentBlockPos);
            enterNetwork(currentBlockPos);
            return;
        }

        // Track stuck progress
        if (currentBlockPos.equals(lastPos)) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastPos = currentBlockPos;
        }

        // Release retreat mode if stuck for too long (15s motionless)
        if (stuckTicks > 300) {
            worm.setRetreating(false);
            this.targetPos = null;
            this.stuckTicks = 0;
            return;
        }

        Vec3 targetCenter = new Vec3(targetX, targetY, targetZ);
        boolean pathClear = isPathClearOfNonHiveBlocks(worm.getEyePosition(), targetCenter);
        double horizDistSq = (targetX - wormPos.x) * (targetX - wormPos.x) + (targetZ - wormPos.z) * (targetZ - wormPos.z);
        double verticalDiff = wormPos.y - targetY; // positive if worm is above target

        if (distSq < 2.0 && Math.abs(verticalDiff) <= 1.5) {
            phase = ApproachPhase.ENTERING;
        } else if (pathClear && ((distSq < 25.0 && horizDistSq < 16.0 && verticalDiff >= -1.0 && verticalDiff <= 5.0) || (distSq < 8.0 && Math.abs(verticalDiff) < 2.5))) {
            if (phase != ApproachPhase.SLIDING) slidingTicks = 0;
            phase = ApproachPhase.SLIDING;
        } else {
            phase = ApproachPhase.NAVIGATING;
            slidingTicks = 0;
        }

        switch (phase) {
            case NAVIGATING -> {
                BlockPos approachPos = findWalkableApproachPos(targetPos);

                if (--pathRecalcCooldown <= 0 || worm.getNavigation().isDone()) {
                    pathRecalcCooldown = 15;

                    BlockPos navDest = approachPos;
                    Path path = worm.getNavigation().createPath(navDest, 0);
                    if (path == null || !path.canReach()) {
                        Path altPath = worm.getNavigation().createPath(targetPos, 1);
                        if (altPath != null && altPath.canReach()) {
                            path = altPath;
                        }
                    }

                    // If direct path is blocked (e.g. ceiling sealed, wall, winding cave), find cave waypoint through open air!
                    if (path == null || !path.canReach()) {
                        BlockPos caveWp = findCaveWaypoint(currentBlockPos, targetPos);
                        if (caveWp != null) {
                            Path cavePath = worm.getNavigation().createPath(caveWp, 0);
                            if (cavePath != null && cavePath.canReach()) {
                                path = cavePath;
                            }
                        }
                    }

                    if (path != null && path.canReach()) {
                        if (worm.horizontalCollision && !worm.isFlying()) {
                            // If colliding horizontally even though path reports canReach,
                            // we are stuck at a diagonal corner slit or cave entrance threshold!
                            boolean dug = worm.tryDigTowardsHive(targetPos);
                            if (dug) {
                                stuckTicks = 0;
                                pathRecalcCooldown = 20;
                            }
                        }
                        worm.getNavigation().moveTo(path, 1.2D);
                    } else {
                        // Direct navigation through open air is blocked or trapped in a cave!
                        // Try to dig towards hive!
                        boolean dug = worm.tryDigTowardsHive(targetPos);
                        if (dug) {
                            stuckTicks = 0;
                            pathRecalcCooldown = 20;
                        } else {
                            // Search for open space away from ceiling/wall to escape dead end or get closer to wall
                            BlockPos openEscape = findOpenSpaceAwayFromCeiling(currentBlockPos, 14);
                            if (openEscape != null) {
                                Path escPath = worm.getNavigation().createPath(openEscape, 0);
                                if (escPath != null && escPath.canReach()) {
                                    worm.getNavigation().moveTo(escPath, 1.2D);
                                } else {
                                    worm.getNavigation().moveTo(openEscape.getX() + 0.5, openEscape.getY(), openEscape.getZ() + 0.5, 1.2D);
                                }
                            }
                        }
                    }
                }

                // Look where the worm is walking (path node), or horizontally toward hive if close
                if (worm.getNavigation().getPath() != null && !worm.getNavigation().getPath().isDone()) {
                    net.minecraft.world.level.pathfinder.Node nextNode = worm.getNavigation().getPath().getNextNode();
                    worm.getLookControl().setLookAt(nextNode.x + 0.5, nextNode.y + 0.5, nextNode.z + 0.5, 30.0F, 30.0F);
                } else if (distSq < 36.0) {
                    double lookY = (worm.getY() < targetY - 2.0 && getCeilingClearance(worm.level(), currentBlockPos) <= 3) ? worm.getEyeY() : targetY;
                    worm.getLookControl().setLookAt(targetX, lookY, targetZ, 30.0F, 30.0F);
                }

                // Anti-stuck logic
                if (stuckTicks > STUCK_THRESHOLD) {
                    // Try to dig towards hive if stuck!
                    if (worm.tryDigTowardsHive(targetPos)) {
                        stuckTicks = 0;
                        return;
                    }

                    int clearance = getCeilingClearance(worm.level(), currentBlockPos);
                    boolean hasCeiling = clearance < 4;

                    if (hasCeiling) {
                        // NEVER jump into a ceiling! Find open cave space leading away from ceiling
                        BlockPos openPos = findOpenSpaceAwayFromCeiling(currentBlockPos, 14);
                        if (openPos != null) {
                            Path escPath = worm.getNavigation().createPath(openPos, 0);
                            if (escPath != null && escPath.canReach()) {
                                worm.getNavigation().moveTo(escPath, 1.3D);
                            } else {
                                worm.getNavigation().moveTo(openPos.getX() + 0.5, openPos.getY(), openPos.getZ() + 0.5, 1.3D);
                            }
                        }
                        stuckTicks = STUCK_THRESHOLD / 2;
                    } else {
                        // Open above: check if standing on or next to hive blocks
                        if (isPassableHiveBlock(worm.level().getBlockState(currentBlockPos.below()), currentBlockPos.below())
                                || isPassableHiveBlock(worm.level().getBlockState(currentBlockPos), currentBlockPos)) {
                            // On hive roof! Start sliding into the hive
                            phase = ApproachPhase.SLIDING;
                            slidingTicks = 0;
                        } else if (worm.onGround() && !hasCeiling) {
                            // Only jump if there is NO ceiling above!
                            worm.getJumpControl().jump();
                        }
                    }
                    relayTarget = null;
                }
            }

            case SLIDING -> {
                slidingTicks++;
                Vec3 tgtCenter = new Vec3(targetX, targetY, targetZ);

                if (slidingTicks > 60) {
                    if (distSq < 16.0 && isPathClearOfNonHiveBlocks(worm.position(), tgtCenter)) {
                        enterNetwork(targetPos);
                    } else {
                        phase = ApproachPhase.NAVIGATING;
                        slidingTicks = 0;
                    }
                    return;
                }

                // If non-hive obstacle appeared or distance grew too large, abort sliding
                if (distSq > 30.0 || !isPathClearOfNonHiveBlocks(worm.position(), tgtCenter)) {
                    phase = ApproachPhase.NAVIGATING;
                    slidingTicks = 0;
                    return;
                }

                worm.getNavigation().stop();
                Vec3 toTarget = new Vec3(targetX - wormPos.x, targetY - wormPos.y, targetZ - wormPos.z);
                double dist = Math.sqrt(distSq);
                double speed = Math.min(0.20, Math.max(0.08, dist * 0.08));
                Vec3 move = toTarget.normalize().scale(speed);
                worm.setPos(wormPos.x + move.x, wormPos.y + move.y, wormPos.z + move.z);
                worm.setDeltaMovement(Vec3.ZERO);
                worm.getLookControl().setLookAt(targetX, targetY, targetZ, 30.0F, 30.0F);

                if (dist < 1.2 || (worm.onGround() && dist < 2.0 && wormPos.y <= targetY + 0.8)) {
                    enterNetwork(targetPos);
                }
            }

            case ENTERING -> {
                worm.getNavigation().stop();
                worm.setDeltaMovement(Vec3.ZERO);
                Vec3 tgtCenter = new Vec3(targetX, targetY, targetZ);
                if (distSq < 4.0 && isPathClearOfNonHiveBlocks(worm.position(), tgtCenter)) {
                    enterNetwork(targetPos);
                } else {
                    phase = ApproachPhase.NAVIGATING;
                }
            }
        }
    }

    private boolean isPassableHiveBlock(BlockState state, BlockPos pos) {
        if (state.isAir()) return true;
        if (state.is(ModBlocks.HIVE_SOIL.get()) ||
            state.is(ModBlocks.HIVE_SOIL_DEAD.get()) ||
            state.is(ModBlocks.HIVE_ROOTS.get()) ||
            state.is(ModBlocks.DEPTH_WORM_NEST.get())) {
            return true;
        }
        BlockEntity be = worm.level().getBlockEntity(pos);
        return be instanceof HiveNetworkMember;
    }

    private boolean isObstacleBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        if (isPassableHiveBlock(state, pos)) return false;
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private boolean isPathClearOfNonHiveBlocks(Vec3 from, Vec3 to) {
        Vec3 diff = to.subtract(from);
        double dist = diff.length();
        if (dist < 0.1) return true;
        Vec3 step = diff.normalize().scale(0.35);
        int numSteps = (int) Math.ceil(dist / 0.35);
        Level level = worm.level();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int i = 0; i <= numSteps; i++) {
            Vec3 point = from.add(step.scale(i));
            mpos.set(point.x, point.y, point.z);
            if (isObstacleBlock(level, mpos)) {
                return false;
            }
        }
        return true;
    }

    private BlockPos findWalkableApproachPos(BlockPos target) {
        Level level = worm.level();
        // Check vertically above target first (find the roof/surface)
        for (int dy = 0; dy <= 5; dy++) {
            BlockPos p = target.above(dy);
            if (isStandable(level, p)) return p;
        }

        // Check horizontal & diagonal neighbors and surface above them
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos wormPos = worm.blockPosition();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = 4; dy >= -2; dy--) {
                    BlockPos p = target.offset(dx, dy, dz);
                    if (isStandable(level, p)) {
                        double d = wormPos.distSqr(p);
                        if (d < bestDist) {
                            bestDist = d;
                            best = p;
                        }
                        break;
                    }
                }
            }
        }

        if (best != null) return best;
        return target.above();
    }

    private boolean isStandable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getCollisionShape(level, pos).isEmpty()) return false;
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        return !belowState.getCollisionShape(level, below).isEmpty() || belowState.isFaceSturdy(level, below, Direction.UP);
    }

    private boolean isSolidBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
    }

    private BlockPos findOpenSpaceAwayFromCeiling(BlockPos start, int radius) {
        Level level = worm.level();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int r = 2; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int dy = -3; dy <= 4; dy++) {
                        BlockPos p = start.offset(dx, dy, dz);
                        if (isStandable(level, p)) {
                            int clearance = getCeilingClearance(level, p);
                            if (clearance >= 4) {
                                double d = start.distSqr(p);
                                if (d < bestDist) {
                                    bestDist = d;
                                    best = p;
                                }
                            }
                        }
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private int getCeilingClearance(Level level, BlockPos pos) {
        int clearance = 0;
        for (int h = 1; h <= 10; h++) {
            if (isObstacleBlock(level, pos.above(h))) {
                break;
            }
            clearance++;
        }
        return clearance;
    }

    @Nullable
    private BlockPos findCaveWaypoint(BlockPos start, BlockPos goal) {
        Level level = worm.level();
        java.util.Queue<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();

        queue.add(start);
        visited.add(start);

        BlockPos bestWaypoint = null;
        double bestScore = -Double.MAX_VALUE;

        int iterations = 0;
        int maxIterations = 400;

        int startClearance = getCeilingClearance(level, start);
        boolean startUnderCeiling = startClearance <= 3;

        while (!queue.isEmpty() && iterations < maxIterations) {
            BlockPos current = queue.poll();
            iterations++;

            int clearance = getCeilingClearance(level, current);
            double distSqToGoal = current.distSqr(goal);
            double hDistFromStart = Math.sqrt((current.getX() - start.getX()) * (current.getX() - start.getX())
                    + (current.getZ() - start.getZ()) * (current.getZ() - start.getZ()));

            // Score this position:
            // 1. If start was trapped under a ceiling (e.g. 1x1 sealed shaft):
            //    We heavily reward positions with high ceiling clearance and horizontal distance away from start!
            // 2. We reward higher Y (progressing upwards towards hive if hive is above start)
            // 3. We reward closer distance to goal
            double score = 0.0;
            if (startUnderCeiling) {
                if (clearance > 3) {
                    score += clearance * 15.0;
                    score += Math.min(hDistFromStart, 16.0) * 10.0;
                }
                if (goal.getY() > start.getY()) {
                    int yGain = current.getY() - start.getY();
                    score += yGain * 25.0;
                }
                score -= Math.sqrt(distSqToGoal) * 2.0;
            } else {
                score -= Math.sqrt(distSqToGoal) * 5.0;
                if (goal.getY() > current.getY()) {
                    score += (current.getY() - start.getY()) * 15.0;
                }
            }

            // Only consider as waypoint candidate if it's at least 3 blocks away from start
            // (or if it has climbed at least 1 block higher)
            if (hDistFromStart >= 3.0 || current.getY() > start.getY()) {
                if (score > bestScore) {
                    bestScore = score;
                    bestWaypoint = current;
                }
            }

            // Expand neighbors
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(dir);

                // Check vertical steps from +3 down to -3 (worm can jump up 3-5 blocks, drop safely)
                for (int dy = 3; dy >= -3; dy--) {
                    BlockPos cand = next.above(dy);
                    if (visited.contains(cand)) continue;
                    if (Math.abs(cand.getX() - start.getX()) > 32 ||
                        Math.abs(cand.getZ() - start.getZ()) > 32 ||
                        Math.abs(cand.getY() - start.getY()) > 18) {
                        continue;
                    }

                    if (isStandable(level, cand) && !isObstacleBlock(level, cand.above())) {
                        visited.add(cand);
                        queue.add(cand);
                        break;
                    }
                }
            }
        }

        return bestWaypoint;
    }

    private void enterNetwork(BlockPos entryPos) {
        Vec3 wormPos = worm.position();
        Vec3 entryCenter = new Vec3(entryPos.getX() + 0.5, entryPos.getY() + 0.5, entryPos.getZ() + 0.5);
        double distSq = wormPos.distanceToSqr(entryCenter);
        if (distSq > 16.0 || !isPathClearOfNonHiveBlocks(wormPos, entryCenter)) {
            // Worm is too far or vertically separated by non-hive blocks (e.g. under floor)
            return;
        }

        HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
        if (manager == null) return;

        BlockEntity be = worm.level().getBlockEntity(entryPos);
        UUID netId = (be instanceof HiveNetworkMember member) ? member.getNetworkId() : null;

        if (netId == null) {
            BlockEntity be2 = worm.level().getBlockEntity(worm.blockPosition());
            if (be2 instanceof HiveNetworkMember member2) {
                netId = member2.getNetworkId();
            }
        }

        if (netId == null) return;

        HiveNetwork network = manager.getNetwork(netId);
        if (network == null) return;

        int kills = worm.getKills();
        if (kills > 0) network.addPoints(kills, worm.level());

        BlockPos boundNest = worm.getBoundNestPos();
        BlockPos actualNest = (boundNest == null || targetIsSoil) ? findNearestNest(entryPos) : boundNest;

        CompoundTag tag = new CompoundTag();
        worm.saveWithoutId(tag);
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(worm.getType()).toString());
        tag.putInt("Kills", 0);

        boolean success = manager.addWormToNetwork(netId, tag, actualNest != null ? actualNest : entryPos, worm.level());

        if (success) {
            worm.setRetreating(false);
            worm.setKills(0);
            network.removeActiveWorm();
            worm.discard();
        } else {
            this.targetPos = null;
            this.stuckTicks = 0;
            this.phase = ApproachPhase.NAVIGATING;
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (worm.isColonist()) return false;
        if (targetPos == null) return false;
        if (!worm.isRetreating()) {
            if (worm.getTarget() != null && worm.getTarget().isAlive()) {
                return false;
            }
        }
        return isValidEntryPoint(targetPos);
    }

    @Override
    public void stop() {
        this.targetPos = null;
        this.targetIsSoil = false;
        this.stuckTicks = 0;
        this.slidingTicks = 0;
        this.lastPos = BlockPos.ZERO;
        this.phase = ApproachPhase.NAVIGATING;
        this.routerActive = false;
        this.routerTarget = null;
        this.relayTarget = null;
        worm.getNavigation().stop();
    }
}
