package com.trd.api.hive;

import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.ModBlockEntities;
import com.trd.block.entity.hive.DepthWormNestBlockEntity;
import com.trd.entity.mobs.depth_worm.DepthWormEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HiveColonizationExpedition {
    public enum State { MOVING, WAITING, RETURNING, SUCCESS, FAILED }

    private State state = State.MOVING;
    private final BlockPos targetPos;
    private final BlockPos homePos;
    private final UUID homeNetworkId;
    private final List<UUID> colonistIds = new ArrayList<>();
    private transient List<DepthWormEntity> colonistRefs = new ArrayList<>();
    private final long startTime;
    private final long timeoutTime;
    private final int pointsPerWorm;

    private int waitingTicks = 0;
    private static final int WAIT_RADIUS_SQ = 25; // 5^2
    private static final int WAIT_STABILIZE_TICKS = 20;
    private static final int RETURN_TIMEOUT_EXTRA = 1200; // +60 сек

    public HiveColonizationExpedition(BlockPos targetPos, BlockPos homePos, UUID homeNetworkId,
                                      List<UUID> colonistIds, List<DepthWormEntity> colonistRefs,
                                      long gameTime, int pointsPerWorm) {
        this.targetPos = targetPos.immutable();
        this.homePos = homePos.immutable();
        this.homeNetworkId = homeNetworkId;
        this.colonistIds.addAll(colonistIds);
        if (colonistRefs != null) this.colonistRefs.addAll(colonistRefs);
        this.startTime = gameTime;
        this.timeoutTime = gameTime + 1200; // 1 минута
        this.pointsPerWorm = pointsPerWorm;
    }

    public State tick(Level level, HiveNetwork homeNetwork) {
        if (level.isClientSide) return state;
        if (state == State.SUCCESS || state == State.FAILED) return state;

        long time = level.getGameTime();
        List<DepthWormEntity> alive = collectAlive(level);

        if (alive.size() < colonistIds.size()) {
            return fail(homeNetwork, level, alive);
        }

        boolean someoneHurt = false;
        for (DepthWormEntity worm : alive) {
            if (worm.getHealth() < worm.getMaxHealth() * 0.66f) {
                someoneHurt = true;
                break;
            }
        }

        if (someoneHurt && state != State.RETURNING) {
            return retreat(homeNetwork, level, alive);
        }

        switch (state) {
            case MOVING -> {
                boolean allArrived = true;
                for (DepthWormEntity worm : alive) {
                    double distSq = worm.distanceToSqr(
                            targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5
                    );
                    if (distSq > WAIT_RADIUS_SQ) {
                        allArrived = false;
                        break;
                    }
                }

                if (allArrived) {
                    state = State.WAITING;
                    waitingTicks = 0;
                } else if (time > timeoutTime) {
                    return fail(homeNetwork, level, alive);
                }
            }
            case WAITING -> {
                waitingTicks++;
                boolean allStillHere = true;
                for (DepthWormEntity worm : alive) {
                    double distSq = worm.distanceToSqr(
                            targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5
                    );
                    if (distSq > WAIT_RADIUS_SQ) {
                        allStillHere = false;
                        state = State.MOVING;
                        break;
                    }
                }

                if (allStillHere && waitingTicks >= WAIT_STABILIZE_TICKS) {
                    return succeed(homeNetwork, level, alive);
                }
                if (time > timeoutTime + 200) {
                    return fail(homeNetwork, level, alive);
                }
            }
            case RETURNING -> {
                boolean allHome = true;
                for (DepthWormEntity worm : alive) {
                    double distSq = worm.distanceToSqr(
                            homePos.getX() + 0.5, homePos.getY() + 0.5, homePos.getZ() + 0.5
                    );
                    if (distSq > 36) {
                        allHome = false;
                        break;
                    }
                }

                if (allHome) {
                    int returnedPoints = alive.size() * pointsPerWorm;
                    homeNetwork.killsPool += returnedPoints;
                    for (DepthWormEntity worm : alive) {
                        worm.setColonist(false, null);
                    }
                    state = State.FAILED;
                    homeNetwork.setColonizationCooldown(time + 2400);
                } else if (time > timeoutTime + RETURN_TIMEOUT_EXTRA) {
                    return fail(homeNetwork, level, alive);
                }
            }
        }
        return state;
    }

    private List<DepthWormEntity> collectAlive(Level level) {
        List<DepthWormEntity> alive = new ArrayList<>();
        if (colonistRefs != null && !colonistRefs.isEmpty()) {
            for (DepthWormEntity worm : colonistRefs) {
                if (worm.isAlive()) alive.add(worm);
            }
        } else {
            // Fallback после перезагрузки — ищем по UUID в радиусе 200 блоков
            if (level instanceof ServerLevel sl) {
                AABB box = new AABB(targetPos).inflate(200);
                for (DepthWormEntity worm : sl.getEntitiesOfClass(DepthWormEntity.class, box)) {
                    if (colonistIds.contains(worm.getUUID()) && worm.isAlive()) {
                        alive.add(worm);
                    }
                }
            }
        }
        return alive;
    }

    private State retreat(HiveNetwork homeNetwork, Level level, List<DepthWormEntity> worms) {
        state = State.RETURNING;
        for (DepthWormEntity worm : worms) {
            worm.setColonist(false, null);
            worm.bindToNest(homePos);
            worm.setRetreating(true);
        }
        return state;
    }

    private State fail(HiveNetwork homeNetwork, Level level, List<DepthWormEntity> survivors) {
        state = State.FAILED;
        for (DepthWormEntity worm : survivors) {
            worm.setColonist(false, null);
            worm.bindToNest(homePos);
            worm.setRetreating(true);
        }
        if (colonistRefs != null) {
            for (DepthWormEntity worm : colonistRefs) {
                if (worm.isAlive() && !survivors.contains(worm)) {
                    worm.setColonist(false, null);
                    worm.bindToNest(homePos);
                    worm.setRetreating(true);
                }
            }
        }
        homeNetwork.setColonizationCooldown(level.getGameTime() + 2400);
        return state;
    }

    private State succeed(HiveNetwork homeNetwork, Level level, List<DepthWormEntity> worms) {
        state = State.SUCCESS;

        BlockPos nestPos = findValidNestPos(level, targetPos);
        if (nestPos == null) {
            return fail(homeNetwork, level, worms);
        }

        HiveNetworkManager manager = HiveNetworkManager.get(level);
        UUID newNetworkId = UUID.randomUUID();

        BlockState nestState = ModBlocks.DEPTH_WORM_NEST.get().defaultBlockState();
        level.setBlock(nestPos, nestState, 3);

        BlockEntity be = ModBlockEntities.DEPTH_WORM_NEST.get().create(nestPos, nestState);
        if (be instanceof DepthWormNestBlockEntity nest) {
            nest.setNetworkId(newNetworkId);
            level.setBlockEntity(be);
            manager.addNode(newNetworkId, nestPos, true);
        }

        HiveNetwork newNetwork = manager.getNetwork(newNetworkId);
        newNetwork.killsPool = 99;
        newNetwork.isAwakened = true;
        newNetwork.currentState = HiveNetwork.HiveState.EXPANSION;
        newNetwork.currentScenario = HiveNetwork.DevelopmentScenario.STARTUP;
        newNetwork.hiveCenter = nestPos.immutable();
        newNetwork.lastFedTime = level.getGameTime();

        for (DepthWormEntity worm : worms) {
            worm.setColonist(false, null);
            worm.bindToNest(nestPos);
            worm.setKills(0);
            worm.setRetreating(false);
            worm.setHomePos(nestPos);
        }

        homeNetwork.activeWorms = Math.max(0, homeNetwork.activeWorms - worms.size());
        return state;
    }

    private BlockPos findValidNestPos(Level level, BlockPos center) {
        for (int y = -2; y <= 3; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() || !state.getFluidState().isEmpty()) continue;

                    BlockPos below = pos.below();
                    BlockState belowState = level.getBlockState(below);
                    if (belowState.isAir() || !belowState.getFluidState().isEmpty()) continue;

                    boolean hasSolidNeighbor = false;
                    for (Direction dir : Direction.values()) {
                        BlockState n = level.getBlockState(pos.relative(dir));
                        if (!n.isAir() && n.getFluidState().isEmpty()) {
                            hasSolidNeighbor = true;
                            break;
                        }
                    }
                    if (!hasSolidNeighbor) continue;

                    boolean fluidNearby = false;
                    for (int fx = -2; fx <= 2 && !fluidNearby; fx++) {
                        for (int fy = -1; fy <= 2 && !fluidNearby; fy++) {
                            for (int fz = -2; fz <= 2 && !fluidNearby; fz++) {
                                if (!level.getBlockState(pos.offset(fx, fy, fz)).getFluidState().isEmpty()) {
                                    fluidNearby = true;
                                }
                            }
                        }
                    }
                    if (fluidNearby) continue;
                    return pos;
                }
            }
        }
        return null;
    }

    public BlockPos getTargetPos() { return targetPos; }
    public State getState() { return state; }
    public boolean isColonist(UUID entityId) { return colonistIds.contains(entityId); }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("State", state.name());
        tag.putLong("StartTime", startTime);
        tag.putLong("Timeout", timeoutTime);
        tag.putInt("PointsPerWorm", pointsPerWorm);
        tag.putLong("TargetPos", targetPos.asLong());
        tag.putLong("HomePos", homePos.asLong());
        tag.putUUID("HomeNetwork", homeNetworkId);
        ListTag ids = new ListTag();
        for (UUID id : colonistIds) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            ids.add(t);
        }
        tag.put("Colonists", ids);
        return tag;
    }

    public static HiveColonizationExpedition fromNBT(CompoundTag tag) {
        BlockPos target = BlockPos.of(tag.getLong("TargetPos"));
        BlockPos home = BlockPos.of(tag.getLong("HomePos"));
        UUID homeNet = tag.getUUID("HomeNetwork");
        List<UUID> ids = new ArrayList<>();
        ListTag list = tag.getList("Colonists", 10);
        for (int i = 0; i < list.size(); i++) ids.add(list.getCompound(i).getUUID("Id"));
        long start = tag.getLong("StartTime");
        int points = tag.getInt("PointsPerWorm");
        HiveColonizationExpedition exp = new HiveColonizationExpedition(
                target, home, homeNet, ids, null, start, points);
        try { exp.state = State.valueOf(tag.getString("State")); } catch (Exception ignored) {}
        return exp;
    }
}