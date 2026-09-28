package com.trd.fx.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;

public class DarkSmokeParticle extends AbstractExplosionParticle {

    public DarkSmokeParticle(ClientLevel level, double x, double y, double z,
                             SpriteSet sprites, double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, sprites);

        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;

        this.lifetime = 50 + this.random.nextInt(40);

        this.gravity = 0.05F;
        this.hasPhysics = false;

        this.quadSize = 0.5F + this.random.nextFloat() * 0.9F;

        float grayValue = 0.35F + this.random.nextFloat() * 0.2F;
        this.rCol = grayValue;
        this.gCol = grayValue;
        this.bCol = grayValue;

        this.alpha = 0.8F;
    }

    @Override
    public void tick() {
        super.tick();

        float fadeProgress = (float) this.age / (float) this.lifetime;
        this.alpha = 0.8F * (1.0F - fadeProgress);

        this.quadSize *= 1.005F;
    }

    public static class Provider extends AbstractExplosionParticle.Provider<DarkSmokeParticle> {
        public Provider(SpriteSet sprites) {
            super(sprites, DarkSmokeParticle::new);
        }
    }
}
