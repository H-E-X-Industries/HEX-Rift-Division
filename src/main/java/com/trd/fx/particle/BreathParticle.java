package com.trd.fx.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Частица пара изо рта на холоде.
 * Выходит изо рта строго по направлению взгляда камеры/головы,
 * объемная, плавно замедляется в воздухе и рассеивается до прозрачности 0%.
 */
public class BreathParticle extends TextureSheetParticle {
    private final float initialAlpha;
    private final float initialSize;

    public BreathParticle(ClientLevel level, double x, double y, double z,
                          SpriteSet sprites, double vx, double vy, double vz) {
        super(level, x, y, z, vx, vy, vz);
        this.pickSprite(sprites);

        this.hasPhysics = false;
        this.gravity = 0.0F;    // Нет гравитации, пар следует импульсу выдоха
        this.friction = 0.93F;   // Мягкое сопротивление воздуха (+30% дистанции полета)

        this.xd = vx;
        this.yd = vy;
        this.zd = vz;

        // Увеличенное время жизни, чтобы пар успевал пролететь увеличенную дистанцию
        this.lifetime = 28 + this.random.nextInt(16); // ~1.4 - 2.2 секунды

        // Увеличенный размер частиц (объемное облачко пара)
        this.initialSize = 0.075F + this.random.nextFloat() * 0.025F;
        this.quadSize = this.initialSize;

        // Нежный полупрозрачный пар (30-45% непрозрачности)
        this.initialAlpha = 0.35F + this.random.nextFloat() * 0.12F;
        this.alpha = this.initialAlpha;

        // Морозный белый оттенок
        this.rCol = 0.95F;
        this.gCol = 0.97F;
        this.bCol = 1.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
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

        // Движение по направлению выдоха
        this.move(this.xd, this.yd, this.zd);

        // Плавное замедление в воздухе
        this.xd *= (double) this.friction;
        this.yd *= (double) this.friction;
        this.zd *= (double) this.friction;

        // Прогресс жизни частицы [0.0 ... 1.0]
        float progress = (float) this.age / (float) this.lifetime;

        // Легкий естественный подъем теплого воздуха начинается только на излете скорости
        this.yd += 0.0012D * progress;

        // Прозрачность увеличивается со временем (альфа плавно уменьшается до 0)
        this.alpha = Math.max(0.0F, this.initialAlpha * (1.0F - progress));

        // Рассеивание и расширение пара (+30% объемнее)
        this.quadSize = this.initialSize * (1.0F + progress * 1.5F);

        // При достижении 0 прозрачности частица исчезает
        if (this.alpha <= 0.01F) {
            this.remove();
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public BreathParticle createParticle(SimpleParticleType type, ClientLevel level,
                                             double x, double y, double z,
                                             double vx, double vy, double vz) {
            return new BreathParticle(level, x, y, z, this.sprites, vx, vy, vz);
        }
    }
}
