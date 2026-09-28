package com.trd.fx.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class FireSparkParticle extends AbstractExplosionParticle {

    private static final double MOB_IGNITE_RADIUS = 1.5;
    private int igniteCheckCooldown = 0;

    public FireSparkParticle(ClientLevel level, double x, double y, double z,
                             SpriteSet sprites, double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, sprites);

        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;

        this.lifetime = 20 + this.random.nextInt(15);

        this.gravity = 0.3F;
        this.hasPhysics = false;

        this.quadSize = 0.3F + this.random.nextFloat() * 0.3F;

        this.rCol = 1.0F;
        this.gCol = 0.7F + this.random.nextFloat() * 0.3F;
        this.bCol = 0.15F;

        this.alpha = 1.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return AdditiveParticleRenderType.INSTANCE;
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

        if (igniteCheckCooldown > 0) {
            igniteCheckCooldown--;
        } else {
            igniteMobs();
            igniteCheckCooldown = 3;
        }

        this.yd -= this.gravity;

        double oldX = this.x;
        double oldY = this.y;
        double oldZ = this.z;

        this.x += this.xd;
        this.y += this.yd;
        this.z += this.zd;

        this.xd *= 0.98F;
        this.yd *= 0.98F;
        this.zd *= 0.98F;

        checkBlockCollision(oldX, oldY, oldZ);

        float fadeProgress = (float) this.age / (float) this.lifetime;
        this.alpha = Math.max(0.6F, 1.0F - fadeProgress);

        this.quadSize *= 0.98F;
    }

    private void igniteMobs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSingleplayerServer() == null) return;

        ServerLevel serverLevel = mc.getSingleplayerServer().getLevel(this.level.dimension());
        if (serverLevel == null) return;

        var nearbyMobs = serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(
                this.x - MOB_IGNITE_RADIUS, this.y - MOB_IGNITE_RADIUS, this.z - MOB_IGNITE_RADIUS,
                this.x + MOB_IGNITE_RADIUS, this.y + MOB_IGNITE_RADIUS, this.z + MOB_IGNITE_RADIUS
        ));

        if (!nearbyMobs.isEmpty()) {
            serverLevel.getServer().execute(() -> {
                for (LivingEntity living : nearbyMobs) {
                    living.setSecondsOnFire(10);
                }
            });
        }
    }

    private void checkBlockCollision(double oldX, double oldY, double oldZ) {
        BlockPos currentPos = BlockPos.containing(this.x, this.y, this.z);
        BlockState blockState = this.level.getBlockState(currentPos);

        if (!blockState.isAir() && blockState.isSolidRender(this.level, currentPos)) {
            Direction hitSide = determineHitSide(oldX, oldY, oldZ, currentPos);

            if (hitSide != null) {
                igniteBlockSide(currentPos, hitSide);
            }

            this.remove();
        }
    }

    private Direction determineHitSide(double oldX, double oldY, double oldZ, BlockPos blockPos) {
        double dx = oldX - (blockPos.getX() + 0.5);
        double dy = oldY - (blockPos.getY() + 0.5);
        double dz = oldZ - (blockPos.getZ() + 0.5);

        double absDx = Math.abs(dx);
        double absDy = Math.abs(dy);
        double absDz = Math.abs(dz);

        if (absDx > absDy && absDx > absDz) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else if (absDy > absDx && absDy > absDz) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private void igniteBlockSide(BlockPos blockPos, Direction side) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSingleplayerServer() == null) return;

        ServerLevel serverLevel = mc.getSingleplayerServer().getLevel(this.level.dimension());
        if (serverLevel == null) return;

        BlockPos firePos = blockPos.relative(side);

        if (serverLevel.isEmptyBlock(firePos) || serverLevel.getBlockState(firePos).canBeReplaced()) {
            if (side == Direction.UP) {
                BlockState belowState = serverLevel.getBlockState(blockPos);
                if (!belowState.isAir() && belowState.isSolidRender(serverLevel, blockPos)) {
                    serverLevel.getServer().execute(() ->
                            serverLevel.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3));
                }
            } else {
                serverLevel.getServer().execute(() ->
                        serverLevel.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3));
            }
        }
    }

    public static class Provider extends AbstractExplosionParticle.Provider<FireSparkParticle> {
        public Provider(SpriteSet sprites) {
            super(sprites, FireSparkParticle::new);
        }
    }
}
