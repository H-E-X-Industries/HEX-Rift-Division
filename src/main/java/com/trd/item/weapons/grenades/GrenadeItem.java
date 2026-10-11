package com.trd.item.weapons.grenades;

import com.trd.entity.ModEntities;
import com.trd.entity.weapons.grenades.GrenadeProjectileEntity;
import com.trd.entity.weapons.grenades.GrenadeType;
import com.trd.sound.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Осколочная/фугасная/зажигательная/липучая/умная граната.
 * Взрыв и число отскоков берутся из {@link GrenadeType}.
 */
public class GrenadeItem extends ChargableGrenadeItem implements PinnedGrenadeItem {

    private final GrenadeType grenadeType;
    private final EntityType<? extends GrenadeProjectileEntity> entityType;

    public GrenadeItem(Properties properties, GrenadeType grenadeType, EntityType<? extends GrenadeProjectileEntity> entityType) {
        super(properties, 0.5f, 1.5f);
        this.grenadeType = grenadeType;
        this.entityType = entityType;
    }

    @Override
    protected void throwGrenade(ItemStack stack, Level level, Player player, float velocity, float chargePercent) {
        GrenadeProjectileEntity grenade = new GrenadeProjectileEntity(
                entityType, level, player, grenadeType
        );
        grenade.setItem(stack);
        placeFromHand(grenade, player);
        grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, 1.0F);
        level.addFreshEntity(grenade);
    }

    /**
     * Свой звук броска, а не ванильный снежок.
     * <p>
     * В базовом {@link ChargableGrenadeItem} стоит {@code SNOWBALL_THROW} — это
     * заглушка, которой в нём не место: бросок гранаты звучит совсем не так. Здесь
     * тот же {@code trd:grenade_throw}, что у ударных гранат, так что вся линия
     * бросается одинаково.
     * <p>
     * Переопределено здесь, а не в базовом классе, намеренно: водородная и
     * гравитационная гранаты бросаются по-своему и держат свои звуки.
     */
    @Override
    protected SoundEvent getThrowSound() {
        return ModSounds.GRENADE_THROW.get();
    }
}