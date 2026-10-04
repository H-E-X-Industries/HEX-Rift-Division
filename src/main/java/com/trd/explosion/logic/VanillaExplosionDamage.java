package com.trd.explosion.logic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Общие примитивы для взрывов с заданным уроном.
 * <p>
 * Ванильный {@code Level#explode} всегда наносит свой урон по формуле
 * {@code (d10² + d10) / 2 * 7 * exposure + 1} — от 1 до 8 в эпицентре, и отключить его нельзя:
 * ни {@code ExplosionDamageCalculator}, ни {@code ExplosionInteraction} не позволяют занулить
 * урон сущностям. Поэтому, когда нужен свой урон, ванильный проход выполняется с подавленным
 * уроном (см. {@link VanillaExplosionDamageGuard}), а урон считается здесь.
 */
public final class VanillaExplosionDamage {

    private VanillaExplosionDamage() {
    }

    private static int suppressDepth = 0;

    /** Глубина вложенности подавления: спасает от повторного входа, если взрыв вложит другой. */
    public static boolean isSuppressed() {
        return suppressDepth > 0;
    }

    /**
     * Выполняет действие, отменяя урон сущностям от ванильных взрывов.
     * Разрушение блоков, дроп и отбрасывание при этом работают как обычно.
     */
    public static void withoutEntityDamage(Runnable action) {
        suppressDepth++;
        try {
            action.run();
        } finally {
            suppressDepth--;
        }
    }

    /**
     * Наносит урон сущностям в радиусе: максимум в эпицентре, линейный спад к нулю на краю.
     * Метатель урон получает наравне со всеми — как и в ванильном взрыве.
     * Отбрасывание не делается: его уже применил ванильный взрыв.
     * <p>
     * <b>Расстояние считается до центра хитбокса, а не до ног.</b> Раньше здесь
     * было {@code entity.distanceToSqr(center)}, то есть до {@code position()} —
     * до земли под ногами. Для высокой сущности это совсем другая точка: у лошади
     * центр хитбокса выше ног на 1.15 блока, и взрыв, сорванный ровно у её центра,
     * оказывался для неё на расстоянии 1.15. При радиусе 2.0 это спад больше
     * половины, а при радиусе 1.0 — вообще ничего, потому что срабатывал отсев
     * {@code distance > radius}: фугасный патрон пролетал сквозь высокую цель и
     * не наносил ровно ничего. Мерить надо туда, куда попал взрыв, то есть к
     * телу.
     * <p>
     * Ванильный взрыв меряет до ног, но компенсирует это тем, что держит урон
     * на расстоянии до двух радиусов, — здесь отсев идёт ровно по радиусу, так
     * что обе величины обязаны совпадать.
     */
    public static void damageEntities(Level level, Vec3 center, Entity source, float radius, float damage) {
        if (level.isClientSide || damage <= 0.0F || radius <= 0.0F) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        DamageSource damageSource = level.damageSources().explosion(source, source);
        AABB area = new AABB(center, center).inflate(radius);
        List<LivingEntity> found = serverLevel.getEntitiesOfClass(LivingEntity.class, area);

        for (LivingEntity entity : found) {
            if (!entity.isAlive() || entity.ignoreExplosion(null)) continue;

            double distance = entity.getBoundingBox().getCenter().distanceTo(center);
            if (distance > radius) continue;

            float falloff = (float) (1.0 - distance / radius);
            float amount = damage * falloff;
            if (amount <= 0.0F) continue;

            entity.hurt(damageSource, amount);
        }
    }
}
