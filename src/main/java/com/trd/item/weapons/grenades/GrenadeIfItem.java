package com.trd.item.weapons.grenades;

import com.trd.entity.weapons.grenades.GrenadeIfProjectileEntity;
import com.trd.entity.weapons.grenades.GrenadeIfType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Ударная граната с инерционным взрывателем: взрывается по фиксированной задержке
 * после первого касания, отскок задаёт {@link GrenadeIfProjectileEntity}.
 *
 * <p><b>Зарядки нет.</b> Раньше предмет держал правую кнопку и бросал по силе
 * удержания; теперь он кидается сразу, на максимальной скорости. Анимации замаха
 * тоже нет: граната исчезает из руки в тот же тик, и половина жеста всё равно не
 * успела бы отрисоваться. Кулдаун после броска остался — без него правый клик
 * спамил бы гранатами двадцать раз в секунду. Остальные гранаты зарядку
 * сохраняют: она переопределена здесь, у них нет.
 *
 * <p><b>Куда играют звуки.</b> Щелчок чеки — только клиенту, у того, кто держит
 * гранату (см. {@link GrenadeIfClientAnim}). Звук броска — серверу и всем
 * nearby-игрокам, потому что момент «взял в руку» на сервере не существует, а
 * бросок слышат все. Поэтому здесь вызывается только {@link GrenadeIfAnimation}:
 * он общий код, в отличие от клиентского автомата анимаций.
 *
 * <p><b>Клиентские расширения здесь не выдаются.</b> Старый способ с
 * {@code Item#initializeClient} в NeoForge 21.1 не вызывается ниоткуда, поэтому
 * рендерер предмета выдаётся подпиской на
 * {@code RegisterClientExtensionsEvent} — см.
 * {@code com.trd.client.gecko.item.grenades.GrenadeIfClientItem}.
 */
public class GrenadeIfItem extends ChargableGrenadeItem {

    private final GrenadeIfType grenadeType;
    private final EntityType<? extends GrenadeIfProjectileEntity> entityType;

    public GrenadeIfItem(Properties properties, GrenadeIfType grenadeIf, EntityType<? extends GrenadeIfProjectileEntity> entityType) {
        // Из этих двух чисел здесь нужен только верхний: бросок без зарядки идёт
        // на максимуме, ровно как будто игрок дожал правую кнопку до конца.
        super(properties, 0.5f, 1.5f);
        this.grenadeType = grenadeIf;
        this.entityType = entityType;
    }

    /** Тип ударной гранаты: по нему и предмет, и сущность берут текстуру из атласа. */
    public GrenadeIfType getGrenadeType() {
        return grenadeType;
    }

    /**
     * Бросок без удержания.
     * <p>
     * Перекрывает зарядку {@link ChargableGrenadeItem#use}, который звал
     * {@code startUsingItem} и ждал отпускания. Проверка кулдауна идёт на обеих
     * сторонах и до всего остального: без неё на клиенте анимация броска
     * начиналась бы в тик, когда сервер бросок отменил, и граната дёргалась бы в
     * руке из ничего.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            throwGrenade(stack, level, player, getMaxVelocity(), 1.0f);

            player.awardStat(Stats.ITEM_USED.get(this));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            // Кулдаун на ВСЕ гранаты в инвентаре, как и у заряжаемых.
            applyGlobalGrenadeCooldown(player);

            // Звук броска уходит в очередь на серверные тики и попадает в ту же
            // секунду клипа, в которую он помечен в экспорте анимаций. Клипа
            // броска у модели теперь нет, но имя в разметке осталось: по нему
            // находится нужный кадр.
            if (level instanceof ServerLevel serverLevel) {
                GrenadeIfAnimation.schedule(serverLevel, player, GrenadeIfAnimation.THROW);
            }
        }

        // Клиенту счёт в стопке приходит отдельным пакетом и может не прийти в
        // этом же тике, поэтому повод для анимации — сам факт броска, а не
        // изменение счёта: следующая граната в руке обязана сама выдернуть себе
        // чеку, иначе после кулдауна кидаешь уже готовую.
        if (level.isClientSide) {
            GrenadeIfClientAnim.onThrown(hand);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Броска здесь не бывает: {@link #use} не зовёт {@code startUsingItem}, и
     * освобождать нечего. Оставлено явным пустым, чтобы у ребёнка в классе не
     * осталось ничего от зарядки.
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
    }

    /**
     * Анимации натяжения не бывает — бросок мгновенный.
     * <p>
     * {@link UseAnim#BOW} у родителя рисовал натяжение и покачивание от первого
     * лица; без удержания это выглядело бы как натянутый лук, который так и не
     * выпустили.
     */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    protected void throwGrenade(ItemStack stack, Level level, Player player, float velocity, float chargePercent) {
        GrenadeIfProjectileEntity grenade = new GrenadeIfProjectileEntity(
                entityType, level, player, grenadeType
        );
        grenade.setItem(stack);
        grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, 1.0F);
        level.addFreshEntity(grenade);
    }
}