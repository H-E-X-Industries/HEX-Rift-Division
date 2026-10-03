package com.trd.fx.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Вспышка в дуле: короткая, всегда светящаяся и абсолютно инертная.
 *
 * <p>Ни физики, ни гравитации, ни собственной скорости: вспышка просто висит в
 * точке вылета все четыре тика своей жизни и исчезает. Единственное, что она
 * делает, — гаснет alpha'ом, поэтому читается как выстрел даже боковым зрением.
 *
 * <p>Свет задан не материалом, а {@link #getLightColor(float)}: у частиц нет
 * своего шейдера, и яркость приходит из второго UV-канала вершины, то есть
 * ровно тем же packed light, что и у любой сущности. Возврат
 * {@link LightTexture#FULL_BRIGHT} кладёт текстуру на texel'у (15, 15) лайтмапы,
 * которая есть в карте освещения всегда, поэтому вспышка одинаково яркая и в
 * пещере, и на солнце.
 *
 * <p>Размер: {@link SingleQuadParticle} строит вершины в {@code ±quadSize}, то
 * есть сторона квадрата равна {@code 2 * quadSize}. Отсюда {@code SIZE / 2} —
 * иначе спрайт был бы вдвое шире задуманного.
 */
public class ShotFlashParticle extends TextureSheetParticle {

    /** Диаметр вспышки в блоках. */
    private static final float SIZE = 0.125F;

    /** Время жизни в тиках: 0.2 секунды. */
    private static final int LIFETIME_TICKS = 4;

    public ShotFlashParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);

        this.pickSprite(sprites);

        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;

        this.lifetime = LIFETIME_TICKS;
        this.quadSize = SIZE * 0.5F;
        this.alpha = 1.0F;

        // Аддитивный блендинг: вспышка добавляет свет, а не закрывает его.
        // На светлом фоне вырез виден и обычным наложением, на тёмном — нет.
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return AdditiveParticleRenderType.INSTANCE;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
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

        // Плавное гашение вместо резкого исчезновения на последнем тике.
        this.alpha = Math.max(0.0F, 1.0F - (float) this.age / (float) this.lifetime);
    }

    /** Провайдер для {@code RegisterParticleProvidersEvent#registerSpriteSet}. */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public ShotFlashParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                double x, double y, double z,
                                                double xSpeed, double ySpeed, double zSpeed) {
            return new ShotFlashParticle(level, x, y, z, this.sprites);
        }
    }
}
