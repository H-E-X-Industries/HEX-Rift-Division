package com.trd.item.weapons.grenades;

import com.trd.entity.weapons.grenades.GrenadeIfProjectileEntity;
import com.trd.entity.weapons.grenades.GrenadeIfType;
import com.trd.sound.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Ударная граната с инерционным взрывателем: взрывается по фиксированной задержке
 * после первого касания, отскок задаёт {@link GrenadeIfProjectileEntity}.
 *
 * <p><b>Зарядка броска такая же, как у обычных гранат.</b> Раньше предмет
 * бросался сразу, на максимальной скорости, а {@link ChargableGrenadeItem} с
 * зарядкой у него был перекрыт. Теперь эти три перекрытия убраны, и ударная
 * граната заряжается удержанием ПКМ ровно так же, как осколочная: тот же
 * {@link net.minecraft.world.item.UseAnim#BOW}, тот же разброс скорости от
 * {@link #getMinVelocity()} до {@link #getMaxVelocity()}, тот же кулдаун и та же
 * отмена при слишком коротком нажатии. Из своего осталось ровно то, что от
 * зарядки не зависит: инерционный взрыватель, своя точка вылета из руки и
 * собственный звук броска.
 *
 * <p>Зарядка меняет <b>только начальную скорость</b>. Ни разброса, ни вращения,
 * ни отдачи у гранаты нет и не появилось: {@code shootFromRotation} получает
 * скорость и <b>постоянный</b> разброс 1.0, поэтому сильный бросок летит так же
 * ровно, как слабый, — просто дальше.
 *
 * <p><b>Куда играют звуки.</b> Щелчок чеки — только клиенту, у того, кто держит
 * гранату (см. {@link GrenadeIfClientAnim}): он разыгрывается при взятии в руку.
 * Звук броска — общий для всех гранат и уходит с сервера, потому что бросок слышат
 * все вокруг, а момент «взял в руку» на сервере не существует.
 *
 * <p><b>Клиентские расширения здесь не выдаются.</b> Старый способ с
 * {@code Item#initializeClient} в NeoForge 21.1 не вызывается ниоткуда, поэтому
 * рендерер предмета выдаётся подпиской на
 * {@code RegisterClientExtensionsEvent} — см.
 * {@code com.trd.client.gecko.item.grenades.GrenadeIfClientItem}.
 */
public class GrenadeIfItem extends ChargableGrenadeItem implements PinnedGrenadeItem {

    private final GrenadeIfType grenadeType;
    private final EntityType<? extends GrenadeIfProjectileEntity> entityType;

    public GrenadeIfItem(Properties properties, GrenadeIfType grenadeIf, EntityType<? extends GrenadeIfProjectileEntity> entityType) {
        // Тот же разброс, что у осколочной: минимум — короткий подброс, максимум —
        // полный. Раньше верхняя граница была единственной, потому что зарядки
        // не было вовсе.
        super(properties, 0.5f, 1.5f);
        this.grenadeType = grenadeIf;
        this.entityType = entityType;
    }

    /** Тип ударной гранаты: по нему и предмет, и сущность берут текстуру из атласа. */
    public GrenadeIfType getGrenadeType() {
        return grenadeType;
    }

    /**
     * Собственный звук броска, а не общий звук брошенного предмета.
     * <p>
     * У остальных гранат он общий, ударные же с самого начала играли
     * {@code grenade_throw}, и менять это вместе с появлением зарядки незачем.
     */
    @Override
    protected SoundEvent getThrowSound() {
        return ModSounds.GRENADE_THROW.get();
    }

    /**
     * Бросок из руки, а не из центра игрока.
     * <p>
     * Точка появления считается от руки: направление взгляда сдвинуто вперёд и
     * вправо по нему. Так граната вылетает оттуда, куда игрок смотрит, и с
     * вытянутой руки — ровно как любой брошенный предмет. Без этого она
     * появлялась в груди, и бросок в упор выглядел так, будто граната выходит
     * сквозь игрока.
     * <p>
     * Здесь {@code velocity} приходит уже пересчитанным зарядкой, а разброс
     * остаётся постоянным — см. верхний комментарий класса.
     */
    @Override
    protected void throwGrenade(ItemStack stack, Level level, Player player, float velocity, float chargePercent) {
        GrenadeIfProjectileEntity grenade = new GrenadeIfProjectileEntity(
                entityType, level, player, grenadeType
        );
        grenade.setItem(stack);

        Vec3 hand = handOrigin(player);
        grenade.setPos(hand.x, hand.y, hand.z);
        grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, 1.0F);

        level.addFreshEntity(grenade);
    }

    /**
     * Точка, из которой вылетает граната: чуть впереди и правее глаз.
     * <p>
     * Вправо — это крест направления взгляда с вертикалью вверх: в системе
     * координат Майнкрафта он уходит именно вправо от взгляда. Если взгляд
     * смотрит строго вверх или вниз, крест вырождается в ноль, и тогда берётся
     * фиксированное «вправо» от поворота по горизонтали — иначе граната
     * появилась бы ровно в глазах.
     */
    private static Vec3 handOrigin(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 eye = player.getEyePosition(1.0F);

        Vec3 right = look.cross(UP);
        if (right.lengthSqr() < 1.0E-6) {
            float yaw = player.getYRot() * ((float) Math.PI / 180.0F);
            right = new Vec3(-Mth.sin(yaw), 0.0F, Mth.cos(yaw));
        } else {
            right = right.normalize();
        }

        return eye.add(look.scale(HAND_FORWARD)).add(right.scale(HAND_SIDE)).subtract(0.0, HAND_DOWN, 0.0);
    }

    /** Вверх по миру: крест направления взгляда с ним и даёт правый вектор. */
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    /** Насколько вылет вперёд от глаз, в блоках. */
    private static final float HAND_FORWARD = 0.3F;
    /** Насколько вылет вправо от линии взгляда, в блоках. */
    private static final float HAND_SIDE = 0.4F;
    /** Насколько вниз от глаз, в блоках: кисть ниже глаз на полметра. */
    private static final float HAND_DOWN = 0.2F;
}