package com.trd.entity.mobs.depth_worm;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class DepthWormJumpGoal extends Goal {
    private final DepthWormEntity worm;
    private LivingEntity target;
    private final double speedModifier;
    private final float jumpRangeMin, jumpRangeMax;
    private int jumpTimer;
    private boolean jumpPerformed;
    private static final int PREPARE_TIME = 30;

    public DepthWormJumpGoal(DepthWormEntity worm, double speedModifier, float jumpRangeMin, float jumpRangeMax) {
        this.worm = worm;
        this.speedModifier = speedModifier;
        this.jumpRangeMin = jumpRangeMin;
        this.jumpRangeMax = jumpRangeMax;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.worm.isColonist()) return false;
        if (this.worm.isRetreating()) return false;

        this.target = this.worm.getTarget();
        if (this.target == null || !this.target.isAlive()) return false;
        if (this.target.getType() == net.minecraft.world.entity.EntityType.BAT) return false;
        if (this.worm.isInWater() || this.target.isInWater()) return false;
        if (this.worm.isInLava() || this.target.isInLava()) return false;

        net.minecraft.world.level.block.state.BlockState targetBelow =
                this.target.level().getBlockState(this.target.blockPosition().below());
        if (targetBelow.is(net.minecraft.world.level.block.Blocks.LAVA)) return false;

        double dist = this.worm.distanceTo(this.target);
        return dist >= this.jumpRangeMin && dist <= this.jumpRangeMax;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.worm.isColonist()) return false;
        if (this.worm.isRetreating()) return false;
        return !jumpPerformed && jumpTimer > 0;
    }

    @Override
    public void start() {
        this.jumpTimer = PREPARE_TIME;
        this.jumpPerformed = false;
        this.worm.setAttacking(true);
        this.worm.setPreparingJump(true);
        this.worm.getNavigation().stop();
        this.worm.hasImpulse = true;
    }

    @Override
    public void stop() {
        this.target = null;
        this.worm.setAttacking(false);
        this.worm.setPreparingJump(false);
        this.jumpPerformed = false;
    }

    @Override
    public void tick() {
        if (this.target == null || !this.target.isAlive()) {
            this.worm.setAttacking(false);
            this.worm.setPreparingJump(false);
            this.jumpTimer = 0;
            this.jumpPerformed = true;
            return;
        }

        if (this.target.getType() == net.minecraft.world.entity.EntityType.BAT ||
            this.target.isInLava() ||
            this.target.level().getBlockState(this.target.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.LAVA)) {
            this.worm.setAttacking(false);
            this.worm.setPreparingJump(false);
            this.jumpTimer = 0;
            this.jumpPerformed = true;
            return;
        }

        double dist = this.worm.distanceTo(this.target);
        if (dist > this.jumpRangeMax + 2.0F) {
            this.worm.setAttacking(false);
            this.worm.setPreparingJump(false);
            this.jumpTimer = 0;
            this.jumpPerformed = true;
            return;
        }

        this.worm.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (--this.jumpTimer <= 0 && !jumpPerformed) {
            this.worm.setPreparingJump(false);
            doJump();
            jumpPerformed = true;
            this.worm.ignoreFallDamageTicks = 30;
        }
    }

    private void doJump() {
        Vec3 wormPos = this.worm.position();
        Vec3 targetPos = this.target.position();

        double dx = targetPos.x - wormPos.x;
        double dz = targetPos.z - wormPos.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDist < 0.001) return;

        double targetY = targetPos.y + this.target.getBbHeight() * 0.4;
        double dy = targetY - wormPos.y;

        // Exact Minecraft LivingEntity flight time and velocity calculation:
        // Living entity air drag: v_horiz_{k+1} = v_horiz_k * 0.91
        // v_y_{k+1} = (v_y_k - 0.08) * 0.98
        int ticks = Math.max(8, Math.min(14, (int) Math.round(6.0 + horizontalDist * 0.7)));

        double hFactor = (1.0 - Math.pow(0.91, ticks)) / 0.09;
        double horizSpeed = horizontalDist / hFactor;

        double a = 0.0;
        double b = 0.0;
        double curA = 1.0;
        double curB = 0.0;
        for (int k = 0; k < ticks; k++) {
            a += curA;
            b += curB;
            curA *= 0.98;
            curB = (curB + 0.08) * 0.98;
        }

        double vy = (dy + b) / a;

        Vec3 horizDir = new Vec3(dx, 0, dz).normalize();
        Vec3 velocity = new Vec3(horizDir.x * horizSpeed, vy, horizDir.z * horizSpeed);

        // Check if trajectory passes into or over lava
        if (isTrajectoryLava(wormPos, velocity, ticks)) {
            return;
        }

        double yaw = Math.atan2(dz, dx) * (180 / Math.PI) - 90;
        this.worm.setYRot((float) yaw);
        this.worm.yHeadRot = (float) yaw;
        this.worm.yBodyRot = (float) yaw;

        this.worm.setDeltaMovement(velocity);
        this.worm.setFlying(true);
        this.worm.ignoreFallDamageTicks = 30;
    }

    private boolean isTrajectoryLava(Vec3 start, Vec3 initialVel, int ticks) {
        Vec3 pos = start;
        Vec3 v = initialVel;
        for (int k = 0; k <= ticks; k++) {
            pos = pos.add(v);
            net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(pos);
            if (this.worm.level().getFluidState(bp).is(net.minecraft.tags.FluidTags.LAVA) ||
                this.worm.level().getBlockState(bp).is(net.minecraft.world.level.block.Blocks.LAVA) ||
                this.worm.level().getBlockState(bp.below()).is(net.minecraft.world.level.block.Blocks.LAVA)) {
                return true;
            }
            v = new Vec3(v.x * 0.91, (v.y - 0.08) * 0.98, v.z * 0.91);
        }
        return false;
    }
}
