package com.trd.item.weapons.grenades;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

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

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW; // ← анимация натягивания лука + покачивание от первого лица
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
                SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL,
                getThrowSoundVolume(), 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
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
}