package com.trd.explosion.logic;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Фугасный взрыв без разрушения блоков.
 * Наносит урон сущностям и отбрасывает их, но не трогает блоки.
 */
public class ExplosionHENonDestructive {

    public static void explode(Level level, Vec3 center, Entity source, float radius, float damage) {
        if (level.isClientSide) return;

        // Ванильный взрыв в режиме NONE: разрушения блоков нет, сущности получают отбрасывание.
        // Урон при этом подавляем — иначе он сложился бы с собственным уроном ниже.
        VanillaExplosionDamage.withoutEntityDamage(() ->
                level.explode(
                        source,
                        center.x, center.y, center.z,
                        radius,
                        false,
                        Level.ExplosionInteraction.NONE // НЕ разрушаем блоки
                ));

        // Собственный урон: максимум в эпицентре, линейный спад к нулю на краю радиуса.
        VanillaExplosionDamage.damageEntities(level, center, source, radius, damage);
    }
}
