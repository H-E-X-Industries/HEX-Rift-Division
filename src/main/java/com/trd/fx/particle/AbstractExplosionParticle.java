package com.trd.fx.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.AABB;

public abstract class AbstractExplosionParticle extends TextureSheetParticle {

    private static final double MAX_RENDER_DISTANCE_SQ = 1024.0 * 1024.0;

    public AbstractExplosionParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.pickSprite(sprites);
        this.hasPhysics = false;
        this.friction = 0.98F;
    }

    /**
     * Дальность отрисовки 1024 блока. В 1.20.1 это был переопределённый {@code shouldCull()};
     * в 1.21.1 метода нет — отсечение идёт по фрустуму, и «не отсекать» выражается возвратом
     * {@link AABB#INFINITE} из {@link #getRenderBoundingBox(float)}.
     */
    @Override
    public AABB getRenderBoundingBox(float partialTicks) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera == null) {
            return AABB.INFINITE;
        }

        double dx = this.x - camera.getPosition().x;
        double dy = this.y - camera.getPosition().y;
        double dz = this.z - camera.getPosition().z;
        double distanceSq = dx * dx + dy * dy + dz * dz;

        if (distanceSq > MAX_RENDER_DISTANCE_SQ) {
            return super.getRenderBoundingBox(partialTicks);
        }
        return AABB.INFINITE;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return LongRangeParticleRenderType.INSTANCE;
    }

    public static abstract class Provider<T extends AbstractExplosionParticle> implements ParticleProvider<SimpleParticleType> {
        protected final SpriteSet sprites;
        private final ParticleFactory<T> factory;

        public Provider(SpriteSet sprites, ParticleFactory<T> factory) {
            this.sprites = sprites;
            this.factory = factory;
        }

        @Override
        public T createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                double xSpeed, double ySpeed, double zSpeed) {
            return this.factory.create(level, x, y, z, sprites, xSpeed, ySpeed, zSpeed);
        }
    }

    @FunctionalInterface
    public interface ParticleFactory<T> {
        T create(ClientLevel level, double x, double y, double z, SpriteSet sprites,
                 double xSpeed, double ySpeed, double zSpeed);
    }
}
