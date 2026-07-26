package com.trd.entity.mobs.depth_worm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class ColonistMoveGoal extends Goal {
    private final DepthWormEntity worm;
    private static final double STOP_DISTANCE_SQ = 9.0; // 3 блока
    private static final double SPEED = 1.1D;

    public ColonistMoveGoal(DepthWormEntity worm) {
        this.worm = worm;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return worm.isColonist() && worm.getColonistTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        updatePath();
    }

    @Override
    public void tick() {
        updatePath();
    }

    private void updatePath() {
        BlockPos target = worm.getColonistTarget();
        if (target == null) return;

        double distSq = worm.distanceToSqr(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5
        );

        if (distSq > STOP_DISTANCE_SQ) {
            worm.getNavigation().moveTo(
                    target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, SPEED
            );
            worm.getLookControl().setLookAt(
                    target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                    30.0F, 30.0F
            );
        } else {
            worm.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        worm.getNavigation().stop();
    }
}