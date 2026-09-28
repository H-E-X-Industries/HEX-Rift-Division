package com.trd.item.weapons.grenades;

import com.trd.entity.weapons.grenades.GravityGrenadeProjectileEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

public class GravityGrenadeItem extends ChargableGrenadeItem {

    private final RegistryObject<? extends EntityType<?>> entityType;

    public GravityGrenadeItem(Properties properties, RegistryObject<? extends EntityType<?>> entityType) {
        super(properties, 0.5f, 1.5f);
        this.entityType = entityType;
    }

    @Override
    protected float getThrowSoundVolume() {
        return 1.2f;
    }

    @Override
    protected void throwGrenade(ItemStack stack, Level level, Player player, float velocity, float chargePercent) {
        GravityGrenadeProjectileEntity grenade = new GravityGrenadeProjectileEntity(
                entityType.get(), level, player
        );
        grenade.setItem(stack);
        grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, 0.8F);
        level.addFreshEntity(grenade);
    }
}