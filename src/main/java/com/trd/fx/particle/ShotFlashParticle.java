package com.trd.fx.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Вспышка в дуле: короткая, всегда светящаяся и абсолютно инертная.
 *
 * <p>Живёт один тик, то есть рисуется ровно один кадр. Раньше было четыре,
 * потом два, и при автоматическом огне вспышки накрывали друг друга: пушка
 * выстреливает каждые шесть тиков, то есть предыдущая ещё не гасла, и дуло
 * светилось непрерывно даже после того, как игрок отпустил огонь. Тик — это
 * меньше, чем может длиться частица, зато столько же, сколько длится выстрел
 * на экране: вспышка совпадает с началом клипа и не живёт дольше него.
 *
 * <p>Каждая вспышка слегка отличается от предыдущей: у неё свой
 * {@link #roll} — случайный поворот вокруг собственной оси, то есть ровно тот
 * штрих несимметричного пятна, который иначе выдаёт, что одна и та же
 * картинка просто мигает на месте, — и свой размер в разбросе
 * {@link #SIZE_JITTER_MIN..#SIZE_JITTER_MAX}. Обе величины берутся один раз на
 * частицу и не меняются: {@code oRoll} ставится в то же значение, что и
 * {@code roll}, иначе кадр интерполировался бы от нуля и вспышка ещё и
 * проворачивалась бы, пока гаснет.
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

    /** Диаметр вспышки в блоках: полметра, вдвое больше прежних 0.125. */
    private static final float SIZE = 0.25F;

    /** Время жизни в тиках: 0.05 секунды, один кадр. */
    private static final int LIFETIME_TICKS = 1;

    /**
     * Границы разброса размера.
     * <p>
     * Плюс-минус десять процентов: ровно столько нужно, чтобы соседние
     * вспышки в одной очереди не читались как одна и та же картинка, и мало
     * достаточно, чтобы дуло не «дышало».
     */
    private static final float SIZE_JITTER_MIN = 0.9F;
    private static final float SIZE_JITTER_MAX = 1.1F;

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
        this.quadSize = SIZE * 0.5F * Mth.nextFloat(this.random, SIZE_JITTER_MIN, SIZE_JITTER_MAX);
        this.alpha = 1.0F;

        // Поворот вокруг своей оси на всём времени жизни: oRoll равен roll, иначе
        // частица ещё и крутилась бы, пока гаснет.
        float angle = this.random.nextFloat() * (float) (Math.PI * 2.0D);
        this.roll = angle;
        this.oRoll = angle;

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

        // При жизни в один тик это даёт нулевую прозрачность сразу, то есть
        // вспышка гаснет вместе с последним кадром, а не висит затухающим
        // пятном. Если LIFETIME_TICKS поднимут, строка начнёт снова гасить
        // по линейке, и отдельного кода для этого не понадобится.
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
