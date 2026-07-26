package com.trd.entity.mobs.depth_worm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class ColonistReturnGoal extends Goal {
    private final DepthWormEntity worm;
    private static final double SPEED = 1.3D;

    public ColonistReturnGoal(DepthWormEntity worm) {
        this.worm = worm;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!worm.isColonist()) return false;
        BlockPos target = worm.getColonistTarget();
        if (target == null) return false;
        return worm.getHealth() < worm.getMaxHealth() * 0.66f;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        BlockPos target = worm.getColonistTarget();
        if (target == null) return;

        worm.getNavigation().moveTo(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, SPEED
        );
        worm.getLookControl().setLookAt(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                30.0F, 30.0F
        );
    }

    @Override
    public void stop() {
        worm.getNavigation().stop();
    }
}