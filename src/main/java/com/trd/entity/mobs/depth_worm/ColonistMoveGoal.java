package com.trd.entity.mobs.depth_worm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class ColonistMoveGoal extends Goal {
    private final DepthWormEntity worm;
    private static final double STOP_DISTANCE_SQ = 9.0;
    private static final double SPEED = 1.1D;

    private int pathRecalcCooldown = 0;

    public ColonistMoveGoal(DepthWormEntity worm) {
        this.worm = worm;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!worm.isColonist() || worm.getColonistTarget() == null) return false;
        // Allow self-defense: if attacked, let MeleeAttackGoal take precedence
        net.minecraft.world.entity.LivingEntity target = worm.getTarget();
        if (target != null && target.isAlive()) return false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.pathRecalcCooldown = 0;
        updatePath();
    }

    @Override
    public void tick() {
        BlockPos target = worm.getColonistTarget();
        if (target == null) return;

        worm.getLookControl().setLookAt(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                30.0F, 30.0F
        );

        if (--this.pathRecalcCooldown <= 0 || worm.getNavigation().isDone()) {
            this.pathRecalcCooldown = 15;
            updatePath();
        }
    }

    private void updatePath() {
        BlockPos target = worm.getColonistTarget();
        if (target == null) return;

        double distSq = worm.distanceToSqr(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5
        );

        if (distSq > STOP_DISTANCE_SQ) {
            if (distSq > 400.0) {
                // Beyond Minecraft navigation limit (~20 blocks): navigate via waypoint
                net.minecraft.world.phys.Vec3 toTarget = new net.minecraft.world.phys.Vec3(
                        target.getX() + 0.5 - worm.getX(),
                        0,
                        target.getZ() + 0.5 - worm.getZ()
                ).normalize();

                BlockPos roughWaypoint = BlockPos.containing(worm.position().add(toTarget.scale(14.0)));
                BlockPos standableWaypoint = findStandableNearby(roughWaypoint);

                if (standableWaypoint != null) {
                    worm.getNavigation().moveTo(
                            standableWaypoint.getX() + 0.5, standableWaypoint.getY(), standableWaypoint.getZ() + 0.5, SPEED
                    );
                } else {
                    worm.getNavigation().moveTo(
                            target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, SPEED
                    );
                }
            } else {
                worm.getNavigation().moveTo(
                        target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, SPEED
                );
            }
        } else {
            worm.getNavigation().stop();
        }
    }

    private BlockPos findStandableNearby(BlockPos base) {
        net.minecraft.world.level.Level level = worm.level();
        for (int dy = 3; dy >= -3; dy--) {
            BlockPos p = base.above(dy);
            if (level.getBlockState(p).isAir() && !level.getBlockState(p.below()).isAir() && level.getFluidState(p).isEmpty()) {
                return p;
            }
        }
        return null;
    }

    @Override
    public void stop() {
        this.pathRecalcCooldown = 0;
        worm.getNavigation().stop();
    }
}
