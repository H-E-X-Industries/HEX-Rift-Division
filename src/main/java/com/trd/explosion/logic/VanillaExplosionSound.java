package com.trd.explosion.logic;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Взрыв с собственным звуком вместо ванильного {@code entity.generic.explode}.
 *
 * <p><b>Зачем это нужно.</b> Обычный {@code level.explode} играет звук сам,
 * внутри себя, и подменить его нечем: {@code Interaction} управляет только
 * разрушением блоков, а {@code Explosion} получает звук в конструкторе и больше
 * ни с чем не сверяется. Но у {@code Level} есть перегрузка, которая принимает
 * готовое звуковое событие, — это ровно то, что нужно: звук взрыва становится
 * параметром, а не свойством ванильного метода.
 *
 * <p>Всё остальное поведение остаётся ванильным без единой правки: та же
 * источник урона, та же сила, те же частицы, та же громкость и тон (их ставит
 * уже {@code Explosion}, поэтому смена звука их не касается).
 *
 * <p>Урон сущностям по-прежнему подавляется — его считает вызывающий, см.
 * {@link VanillaExplosionDamage}.
 */
public final class VanillaExplosionSound {

    private VanillaExplosionSound() {
    }

    /**
     * Взрыв с заданным звуком.
     *
     * @param level        мир, в котором взрывается
     * @param center       эпицентр
     * @param source       источник взрыва (бросивший гранату)
     * @param radius       радиус поражения блоков
     * @param damage       урон, который насчитает вызывающий (здесь подавляется)
     * @param interaction  что именно ломаем
     * @param sound        звук вместо ванильного
     */
    public static void explode(Level level, Vec3 center, Entity source, float radius, float damage,
                               Level.ExplosionInteraction interaction, Holder<SoundEvent> sound) {
        if (level.isClientSide) return;

        VanillaExplosionDamage.withoutEntityDamage(() ->
                level.explode(
                        source,
                        Explosion.getDefaultDamageSource(level, source),
                        null, // калькулятор урона по умолчанию — как и у level.explode
                        center.x, center.y, center.z,
                        radius,
                        false, // огонь ставит вызывающий, если он ему нужен
                        interaction,
                        ParticleTypes.EXPLOSION,
                        ParticleTypes.EXPLOSION_EMITTER,
                        sound
                ));

        VanillaExplosionDamage.damageEntities(level, center, source, radius, damage);
    }
}