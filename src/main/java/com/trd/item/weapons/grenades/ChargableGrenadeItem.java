package com.trd.item.weapons.grenades;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Граната с зарядкой броска: чем дольше держишь ПКМ, тем сильнее бросок.
 */
public abstract class ChargableGrenadeItem extends Item {

    /** 40 тиков = 2 секунды для 100% силы броска */
    public static final int MAX_CHARGE_TICKS = 40;
    /** Минимальное время удержания, иначе бросок отменяется (0.2 сек) */
    public static final int MIN_CHARGE_TICKS = 4;
    /** Кулдаун после броска (1 сек), как у литых кирок */
    public static final int COOLDOWN_TICKS = 20;

    private final float minVelocity;
    private final float maxVelocity;

    public ChargableGrenadeItem(Properties properties, float minVelocity, float maxVelocity) {
        super(properties);
        this.minVelocity = minVelocity;
        this.maxVelocity = maxVelocity;
    }

    public float getMinVelocity() { return minVelocity; }
    public float getMaxVelocity() { return maxVelocity; }

    /** Громкость звука броска (переопредели в подклассе если нужно) */
    protected float getThrowSoundVolume() { return 0.5f; }

    /**
     * Звук броска.
     * <p>
     * Общий звук броска предмета — он и шёл до сих пор у всех заряжаемых гранат.
     * Ударные гранаты радились с собственным звуком, и переводить их на общий
     * было бы тихой регрессией, поэтому у них он переопределён.
     */
    protected SoundEvent getThrowSound() { return SoundEvents.SNOWBALL_THROW; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    /**
     * Ванильной анимации натяжения нет намеренно.
     * <p>
     * Здесь стоял {@link UseAnim#BOW}, и от первого лица граната при зарядке
     * растягивалась по горизонтали почти в 1.75 раза и уезжала из руки — это
     * выглядит так, будто модель раздувается резиновой, а не натягивается рукой.
     * Заряд теперь показывает облик предмета сам: лёгкое качание вокруг
     * собственного центра, растущее к полному заряду, — см.
     * {@code GrenadeGltfAppearance#transform}. Позиция при этом не меняется.
     */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;

        int chargeTime = 72000 - timeLeft;

        // Слишком быстрое нажатие — отмена, граната не тратится
        if (chargeTime < MIN_CHARGE_TICKS) {
            return;
        }

        float chargePercent = Math.min(1.0f, chargeTime / (float) MAX_CHARGE_TICKS);
        float velocity = minVelocity + (maxVelocity - minVelocity) * chargePercent;

        if (!level.isClientSide) {
            throwGrenade(stack, level, player, velocity, chargePercent);

            player.awardStat(Stats.ITEM_USED.get(this));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            // Кулдаун на ВСЕ гранаты в инвентаре
            applyGlobalGrenadeCooldown(player);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                getThrowSound(), SoundSource.NEUTRAL,
                getThrowSoundVolume(), 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));

        // Граната выброшена из этой руки, и в руке осталась следующая — той же
        // самой. Повод для клипа чеки только один и точный, как раз бросок:
        // счёт в стопке меняется и от подбора, и от серверной синхронизации, и по
        // нему анимация заводилась бы сама собой. Без этого вызова следующая
        // граната осталась бы с уже выдернутой чекой — то есть выглядела бы
        // готовой к броску.
        //
        // Только клиенту и только здесь: момент «взял в руку» на сервере не
        // существует, а на клиенте повод тот же — сам факт броска.
        if (level.isClientSide) {
            GrenadeIfClientAnim.onThrown(player.getUsedItemHand());
        }
    }

    /** Ставит кулдаун на каждую гранату в инвентаре и оффхенде */
    protected void applyGlobalGrenadeCooldown(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack invStack = player.getInventory().getItem(i);
            if (invStack.getItem() instanceof ChargableGrenadeItem) {
                player.getCooldowns().addCooldown(invStack.getItem(), COOLDOWN_TICKS);
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof ChargableGrenadeItem) {
            player.getCooldowns().addCooldown(offhand.getItem(), COOLDOWN_TICKS);
        }
    }

    protected abstract void throwGrenade(ItemStack stack, Level level, Player player, float velocity, float chargePercent);

    /**
     * Ставит снаряд в вытянутую руку, а не в центр игрока.
     * <p>
     * Точка появления считается от руки: направление взгляда сдвинуто вперёд и
     * вправо по нему. Так граната вылетает оттуда, куда игрок смотрит, и с
     * вытянутой руки — ровно как любой брошенный предмет. Без этого она
     * появлялась в груди, и бросок в упор выглядел так, будто граната выходит
     * сквозь игрока.
     * <p>
     * Зовется <b>до</b> {@code shootFromRotation}: тот считает направление от
     * позиции игрока и меняет скорость, а позицию снаряда не трогает, так что
     * порядок неважен — важен лишь сам сдвиг.
     */
    protected static void placeFromHand(Entity grenade, Player player) {
        Vec3 hand = handOrigin(player);
        grenade.setPos(hand.x, hand.y, hand.z);
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
