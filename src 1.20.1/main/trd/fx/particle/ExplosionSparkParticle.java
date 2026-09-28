package com.trd.fx.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;

public class ExplosionSparkParticle extends AbstractExplosionParticle {

    public ExplosionSparkParticle(ClientLevel level, double x, double y, double z,
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
        super.tick();

        float fadeProgress = (float) this.age / (float) this.lifetime;
        this.alpha = Math.max(0.6F, 1.0F - fadeProgress);

        this.quadSize *= 0.98F;
    }

    public static class Provider extends AbstractExplosionParticle.Provider<ExplosionSparkParticle> {
        public Provider(SpriteSet sprites) {
            super(sprites, ExplosionSparkParticle::new);
        }
    }
}
