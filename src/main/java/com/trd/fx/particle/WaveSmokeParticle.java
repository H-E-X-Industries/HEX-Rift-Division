package com.trd.fx.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class WaveSmokeParticle extends AbstractExplosionParticle {

    private static final double LIFT_FORCE = 0.15;
    private static final double MAX_LIFT_HEIGHT = 5.0;

    private static final double PLAYER_EFFECT_RADIUS = 2.0;
    private static final float MAX_SHAKE_INTENSITY = 1.0F;
    private static final double MAX_DISTANCE_FOR_EFFECT = 15.0;
    private static final float MIN_SHAKE_INTENSITY = 0.1F;

    private int effectCooldown = 0;

    private final double explosionCenterX;
    private final double explosionCenterZ;

    private final double originY;

    public WaveSmokeParticle(ClientLevel level, double x, double y, double z,
                             SpriteSet sprites, double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, sprites);

        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;

        this.originY = y;

        double dirLength = Math.sqrt(xSpeed * xSpeed + zSpeed * zSpeed);
        if (dirLength > 0.001) {
            double avgStartRadius = 6.0;
            this.explosionCenterX = x - (xSpeed / dirLength) * avgStartRadius;
            this.explosionCenterZ = z - (zSpeed / dirLength) * avgStartRadius;
        } else {
            this.explosionCenterX = x;
            this.explosionCenterZ = z;
        }

        this.lifetime = 60 + this.random.nextInt(40);
        this.gravity = 0.08F;
        this.hasPhysics = false;

        this.quadSize = 0.4F + this.random.nextFloat() * 0.6F;

        float grayValue = 0.5F + this.random.nextFloat() * 0.3F;
        this.rCol = grayValue;
        this.gCol = grayValue;
        this.bCol = grayValue;

        this.alpha = 0.7F;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        if (effectCooldown > 0) {
            effectCooldown--;
        } else {
            checkPlayerCollision();
        }

        Vec3 nextPos = new Vec3(this.x + this.xd, this.y + this.yd, this.z + this.zd);
        BlockPos blockPos = new BlockPos((int) Math.floor(nextPos.x),
                (int) Math.floor(nextPos.y),
                (int) Math.floor(nextPos.z));

        BlockState blockState = this.level.getBlockState(blockPos);
        boolean hasCollision = !blockState.isAir() && blockState.isSolidRender(this.level, blockPos);

        if (hasCollision) {
            double currentHeight = this.y - this.originY;

            if (currentHeight < MAX_LIFT_HEIGHT) {
                this.yd += LIFT_FORCE;
                this.xd *= 0.95;
                this.zd *= 0.95;
            } else {
                this.yd = Math.max(this.yd, 0);
                this.xd *= 1.05;
                this.zd *= 1.05;
            }
        } else {
            this.yd -= this.gravity;

            BlockPos belowPos = new BlockPos((int) Math.floor(this.x),
                    (int) Math.floor(this.y - 0.5),
                    (int) Math.floor(this.z));
            BlockState belowState = this.level.getBlockState(belowPos);
            boolean hasFloor = !belowState.isAir() && belowState.isSolidRender(this.level, belowPos);

            if (hasFloor && this.yd < 0) {
                this.yd = 0.02;
            }
        }

        this.x += this.xd;
        this.y += this.yd;
        this.z += this.zd;

        this.xd *= 0.98;
        this.zd *= 0.98;
        this.yd *= 0.95;

        float fadeProgress = (float) this.age / (float) this.lifetime;
        this.alpha = 0.7F * (1.0F - fadeProgress);
        this.quadSize *= 1.005F;
    }

    private void checkPlayerCollision() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        double dx = this.x - player.getX();
        double dy = this.y - player.getY();
        double dz = this.z - player.getZ();
        double distanceSq = dx * dx + dy * dy + dz * dz;

        if (distanceSq < PLAYER_EFFECT_RADIUS * PLAYER_EFFECT_RADIUS) {
            applyShockwaveEffect(distanceSq);
            effectCooldown = 10;
        }
    }

    private double getDistanceFromExplosionCenter() {
        double dx = this.x - this.explosionCenterX;
        double dz = this.z - this.explosionCenterZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void applyShockwaveEffect(double distanceSq) {
        double distance = Math.sqrt(distanceSq);
        float playerProximity = (float) (1.0 - distance / PLAYER_EFFECT_RADIUS);

        double distanceFromCenter = getDistanceFromExplosionCenter();

        float distanceFalloff;
        if (distanceFromCenter < MAX_DISTANCE_FOR_EFFECT) {
            distanceFalloff = 1.0F - (float) (distanceFromCenter / MAX_DISTANCE_FOR_EFFECT);
        } else {
            distanceFalloff = 0.0F;
        }

        float baseIntensity = MAX_SHAKE_INTENSITY * playerProximity * distanceFalloff;

        float finalIntensity = Math.max(baseIntensity, MIN_SHAKE_INTENSITY * distanceFalloff);

        CameraShakeHandler.addShake(finalIntensity, 10);
    }

    public static class Provider extends AbstractExplosionParticle.Provider<WaveSmokeParticle> {
        public Provider(SpriteSet sprites) {
            super(sprites, WaveSmokeParticle::new);
        }
    }
}
