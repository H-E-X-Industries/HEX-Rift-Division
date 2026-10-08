package com.trd.explosion.logic;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ExplosionStandard {

    public static void explode(Level level, Vec3 center, Entity source, float radius, float damage) {
        explode(level, center, source, radius, damage, SoundEvents.GENERIC_EXPLODE);
    }

    /**
     * Осколочный взрыв со своим звуком.
     * <p>
     * Перегрузка нужна ударной гранате: её взрыв должен звучать
     * {@code trd:grenade_explosion}, а не ванильным {@code entity.generic.explode}.
     * Всё, кроме звука, остаётся ванильным.
     *
     * @param sound звук вместо ванильного
     */
    public static void explode(Level level, Vec3 center, Entity source, float radius, float damage,
                               Holder<SoundEvent> sound) {
        if (level.isClientSide) return;

        // Ванильный взрыв: разрушение блоков, дроп и отбрасывание.
        // Урон сущностям подавляем — считаем его самим по damage.
        VanillaExplosionSound.explode(level, center, source, radius, damage,
                Level.ExplosionInteraction.BLOCK, sound);
    }
}
