package com.trd.item.weapons.grenades;

import com.trd.entity.weapons.grenades.GrenadeNucProjectileEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

public class GrenadeNucItem extends ChargableGrenadeItem {

    private final RegistryObject<? extends EntityType<?>> entityType;

    public GrenadeNucItem(Properties properties, RegistryObject<? extends EntityType<?>> entityType) {
        super(properties, 0.4f, 1.2f);
        this.entityType = entityType;
    }

    @Override
    protected float getThrowSoundVolume() {
        return 1.5f;
    }

    @Override
    protected void throwGrenade(ItemStack stack, Level level, Player player, float velocity, float chargePercent) {
        GrenadeNucProjectileEntity grenade = new GrenadeNucProjectileEntity(
                entityType.get(), level, player
        );
        grenade.setItem(stack);
        grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, 1.0F);
        level.addFreshEntity(grenade);
    }
}