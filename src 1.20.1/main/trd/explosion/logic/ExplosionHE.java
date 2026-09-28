package com.trd.explosion.logic;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ExplosionHE {

    public static void explode(Level level, Vec3 center, Entity source, float radius, float damage) {
        if (level.isClientSide) return;

        // Мощный взрыв — разрушает больше, отбрасывает сильнее.
        // Урон сущностям подавляем — считаем его самим по damage.
        VanillaExplosionDamage.withoutEntityDamage(() ->
                level.explode(
                        source,
                        center.x, center.y, center.z,
                        radius,
                        false,
                        Level.ExplosionInteraction.TNT // более разрушительный режим
                ));

        // Собственный урон: максимум в эпицентре, линейный спад к нулю на краю радиуса.
        VanillaExplosionDamage.damageEntities(level, center, source, radius, damage);
    }
}
