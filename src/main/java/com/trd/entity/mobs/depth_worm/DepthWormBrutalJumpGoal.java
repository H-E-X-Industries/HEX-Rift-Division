package com.trd.entity.mobs.depth_worm;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class DepthWormBrutalJumpGoal extends Goal {
    private final DepthWormBrutalEntity worm;
    private final float jumpRangeMin, jumpRangeMax;

    private LivingEntity target;
    private int prepareTimer;
    private boolean jumpPerformed;
    private static final int PREPARE_TIME = 10;

    private static final double MAX_HORIZONTAL_SPEED = 3.5;
    private static final double MAX_VERTICAL_SPEED = 2.0;
    private static final double GRAVITY = 0.08;
    private int failedJumpCooldown = 0;
    private static final int FAILED_JUMP_COOLDOWN = 40;
    private int jumpTickCounter = 0;
    private static final int MAX_JUMP_TICKS = 60;
    private int noMovementTicks = 0;
    private Vec3 lastJumpPos = Vec3.ZERO;

    private int totalFlightTicks = 0;
    private Vec3 launchDirection = Vec3.ZERO;
    private double baseHorizSpeed = 0.0;

    public DepthWormBrutalJumpGoal(DepthWormBrutalEntity worm, double speedModifier, float jumpRangeMin, float jumpRangeMax) {
        this.worm = worm;
        this.jumpRangeMin = jumpRangeMin;
        this.jumpRangeMax = jumpRangeMax;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public void start() {
        this.prepareTimer = PREPARE_TIME;
        this.jumpPerformed = false;
        this.jumpTickCounter = 0;
        this.totalFlightTicks = 0;
        this.noMovementTicks = 0;
        this.worm.setPreparingJump(true);
        this.worm.getNavigation().stop();
        this.worm.setAttacking(true);
    }

    @Override
    public void stop() {
        this.target = null;
        this.worm.setPreparingJump(false);
        this.worm.setFlying(false);
        this.jumpPerformed = false;
        this.jumpTickCounter = 0;
        this.totalFlightTicks = 0;
        this.noMovementTicks = 0;
        this.worm.getNavigation().stop();
        this.worm.triggerPostAttackAnim();
    }

    @Override
    public boolean canUse() {
        if (this.worm.isColonist()) return false;
        if (failedJumpCooldown > 0) {
            failedJumpCooldown--;
            return false;
        }

        if (this.worm.isRetreating()) return false;

        this.target = this.worm.getTarget();
        if (this.target == null || !this.target.isAlive()) {
            if (this.worm.isPreparingJump()) {
                this.worm.setPreparingJump(false);
                this.worm.setAttacking(false);
            }
            return false;
        }
        if (this.target.getType() == net.minecraft.world.entity.EntityType.BAT) return false;
        if (this.worm.isInWater() || this.target.isInWater()) {
            if (this.worm.isPreparingJump()) abortPrepare();
            return false;
        }
        if (this.worm.isInLava() || this.target.isInLava()) {
            if (this.worm.isPreparingJump()) abortPrepare();
            return false;
        }

        net.minecraft.world.level.block.state.BlockState targetBelow =
                this.target.level().getBlockState(this.target.blockPosition().below());
        if (targetBelow.is(net.minecraft.world.level.block.Blocks.LAVA)) {
            if (this.worm.isPreparingJump()) abortPrepare();
            return false;
        }

        if (this.worm.isImpaling()) return false;

        double dist = this.worm.distanceTo(this.target);

        if (dist < this.jumpRangeMin) {
            if (this.worm.isPreparingJump()) abortPrepare();
            return false;
        }

        if (dist > this.jumpRangeMax) {
            if (this.worm.isPreparingJump()) abortPrepare();
            return false;
        }

        if (this.worm.isPreparingJump()) return false;

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.worm.isRetreating()) return false;
        if (this.worm.isColonist()) return false;

        if (this.target == null || !this.target.isAlive()) return false;
        if (this.target.getType() == net.minecraft.world.entity.EntityType.BAT) return false;
        if (this.worm.isInWater() || this.target.isInWater()) return false;
        if (this.worm.isInLava() || this.target.isInLava()) return false;

        net.minecraft.world.level.block.state.BlockState targetBelow =
                this.target.level().getBlockState(this.target.blockPosition().below());
        if (targetBelow.is(net.minecraft.world.level.block.Blocks.LAVA)) return false;

        if (this.worm.isImpaling()) {
            LivingEntity impaled = this.worm.getImpaledTarget();
            return impaled != null && impaled.isAlive();
        }

        if (!jumpPerformed) {
            if (prepareTimer <= 0) return false;

            double dist = this.worm.distanceTo(this.target);

            if (dist > this.jumpRangeMax + 4.0F) return false;

            if (dist < this.jumpRangeMin) {
                abortPrepare();
                return false;
            }
            return true;
        }

        if (jumpTickCounter > MAX_JUMP_TICKS) return false;
        // Do not abort on ground if still within expected flight duration
        if (jumpTickCounter >= totalFlightTicks && worm.onGround() && !worm.isImpaling()) return false;
        return true;
    }

    @Override
    public void tick() {
        if (this.target == null || !this.target.isAlive()) {
            abortPrepare();
            return;
        }
        if (this.target.getType() == net.minecraft.world.entity.EntityType.BAT ||
            this.worm.isInWater() || this.target.isInWater() ||
            this.worm.isInLava() || this.target.isInLava() ||
            this.target.level().getBlockState(this.target.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.LAVA)) {
            abortPrepare();
            return;
        }

        if (!jumpPerformed) {
            double dist = this.worm.distanceTo(this.target);

            if (dist > this.jumpRangeMax + 4.0F) {
                abortPrepare();
                return;
            }
            if (dist < this.jumpRangeMin) {
                abortPrepare();
                return;
            }

            this.worm.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

            if (--this.prepareTimer <= 0) {
                if (tryExecuteJump()) {
                    jumpPerformed = true;
                    lastJumpPos = this.worm.position();
                } else {
                    failedJumpCooldown = FAILED_JUMP_COOLDOWN;
                    abortPrepare();
                }
            }
        } else {
            jumpTickCounter++;
            Vec3 cur = this.worm.position();
            if (cur.distanceToSqr(lastJumpPos) < 0.0025) {
                if (++noMovementTicks > 12) return;
            } else {
                noMovementTicks = 0;
                lastJumpPos = cur;
            }

            // Carry through ground bumps during the flight window
            if (jumpTickCounter < totalFlightTicks && this.worm.onGround() && !this.worm.isImpaling()) {
                Vec3 curVel = this.worm.getDeltaMovement();
                double expectedSpeed = baseHorizSpeed * Math.pow(0.91, jumpTickCounter);
                if (curVel.horizontalDistance() < expectedSpeed * 0.7 && launchDirection.lengthSqr() > 0.001) {
                    Vec3 fwd = launchDirection.scale(expectedSpeed * 0.85);
                    this.worm.setDeltaMovement(fwd.x, Math.max(curVel.y, 0.12D), fwd.z);
                    this.worm.hasImpulse = true;
                }
            }

            checkMidAirCollision();
        }
    }

    private void abortPrepare() {
        this.prepareTimer = 0;
        this.worm.setPreparingJump(false);
        this.worm.setAttacking(false);
        this.jumpPerformed = true;
    }

    private void executeImpaleOrBounce() {
        int armor = this.target.getArmorValue();

        if (armor < 12) {
            this.target.hurt(this.worm.damageSources().mobAttack(this.worm), 8.0F);
            this.worm.setImpaledTarget(this.target);
            Vec3 targetVel = this.target.getDeltaMovement();
            this.worm.setDeltaMovement(targetVel.scale(0.5));
            this.worm.setFlying(false);
        } else {
            this.target.hurt(this.worm.damageSources().mobAttack(this.worm), 3.0F);
            this.worm.setImpaledTarget(this.target);
            Vec3 bounce = this.worm.getLookAngle().scale(-0.3).add(0, 0.2, 0);
            this.worm.setDeltaMovement(bounce);
            this.worm.setFlying(false);
        }
    }

    private boolean tryExecuteJump() {
        Vec3 wormPos = this.worm.position();
        Vec3 targetPos = this.target.position();
        Vec3 targetVel = this.target.getDeltaMovement();

        double dx = targetPos.x - wormPos.x;
        double dz = targetPos.z - wormPos.z;
        double flatDist = Math.sqrt(dx * dx + dz * dz);
        if (flatDist < 0.001) return false;

        // Flight time: fast, high-momentum leap (10 to 18 ticks)
        int ticks = Math.max(10, Math.min(18, (int) Math.round(6.0 + flatDist * 0.55)));
        this.totalFlightTicks = ticks;

        // Target movement prediction
        Vec3 predictedPos = targetPos.add(targetVel.x * ticks * 0.5, 0, targetVel.z * ticks * 0.5);
        dx = predictedPos.x - wormPos.x;
        dz = predictedPos.z - wormPos.z;
        flatDist = Math.sqrt(dx * dx + dz * dz);

        // Substantial lead (+3.5 blocks) so forward speed stays high through the entire leap
        Vec3 dir = new Vec3(dx, 0, dz).normalize();
        this.launchDirection = dir;
        double leadDist = flatDist + 3.5;

        // Target chest/head height + 1.2 blocks above target so descent cleanly descends directly onto target
        double targetY = predictedPos.y + this.target.getBbHeight() * 0.5 + 1.2;
        double dy = targetY - wormPos.y;

        // Exact Minecraft LivingEntity physics with drag 0.91 horizontal, 0.98 vertical + 0.08 gravity:
        double hFactor = (1.0 - Math.pow(0.91, ticks)) / 0.09;
        double horizSpeed = leadDist / hFactor;
        this.baseHorizSpeed = horizSpeed;

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

        Vec3 velocity = new Vec3(dir.x * horizSpeed, vy, dir.z * horizSpeed);

        if (!isTrajectoryClear(wormPos, velocity, ticks)) {
            return false;
        }

        double yaw = Math.atan2(dz, dx) * (180 / Math.PI) - 90;
        this.worm.setYRot((float) yaw);
        this.worm.yHeadRot = (float) yaw;
        this.worm.yBodyRot = (float) yaw;

        this.worm.setOnGround(false);
        this.worm.setDeltaMovement(velocity);
        this.worm.setFlying(true);
        this.worm.hasImpulse = true;
        this.worm.ignoreFallDamageTicks = 60;

        this.worm.setPreparingJump(false);
        this.worm.setAttacking(false);

        return true;
    }

    private boolean isTrajectoryClear(Vec3 start, Vec3 initialVel, int ticks) {
        Vec3 pos = start;
        Vec3 v = initialVel;
        for (int i = 0; i <= ticks; i++) {
            pos = pos.add(v);

            // Lava check along trajectory and landing
            net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(pos);
            if (this.worm.level().getFluidState(bp).is(net.minecraft.tags.FluidTags.LAVA) ||
                this.worm.level().getBlockState(bp).is(net.minecraft.world.level.block.Blocks.LAVA) ||
                this.worm.level().getBlockState(bp.below()).is(net.minecraft.world.level.block.Blocks.LAVA)) {
                return false;
            }

            // Only check solid block collisions in mid-flight (exclude first 2 ticks and last 3 ticks)
            if (i > 2 && i < ticks - 3) {
                AABB box = new AABB(pos.x - 0.35, pos.y, pos.z - 0.35, pos.x + 0.35, pos.y + 0.7, pos.z + 0.35);
                if (!worm.level().noCollision(box)) {
                    return false;
                }
            }

            v = new Vec3(v.x * 0.91, (v.y - 0.08) * 0.98, v.z * 0.91);
        }
        return true;
    }

    private void checkMidAirCollision() {
        if (this.worm.isImpaling()) return;

        AABB wormBox = this.worm.getBoundingBox().inflate(1.2);
        if (wormBox.intersects(this.target.getBoundingBox())) {
            executeImpaleOrBounce();
        }
    }
}
