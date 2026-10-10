package com.trd.item.weapons.grenades;

import com.trd.entity.weapons.grenades.GrenadeIfProjectileEntity;
import com.trd.entity.weapons.grenades.GrenadeIfType;
import com.trd.sound.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

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
     * Бросок из вытянутой руки — так же, как у остальных гранат.
     * <p>
     * Точка вылета и её расчёт живут в {@link #placeFromHand}: раньше они были
     * здесь частным случаем, а стоило привести остальные гранаты к тому же, что
     * ударные, как обнаружилось, что общее у них ровно это — вылет из руки, и
     * держать вторую копию было незачем. Из своего у ударной гранаты остаётся
     * инерционный взрыватель.
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
        placeFromHand(grenade, player);
        grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, 1.0F);

        level.addFreshEntity(grenade);
    }
}