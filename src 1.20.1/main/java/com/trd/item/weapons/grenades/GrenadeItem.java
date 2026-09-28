package com.trd.item.weapons.grenades;

import com.trd.entity.weapons.grenades.GrenadeProjectileEntity;
import com.trd.entity.weapons.grenades.GrenadeType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

public class GrenadeItem extends ChargableGrenadeItem {

    private final GrenadeType grenadeType;
    private final RegistryObject<EntityType<GrenadeProjectileEntity>> entityType;

    public GrenadeItem(Properties properties, GrenadeType grenadeType, RegistryObject<EntityType<GrenadeProjectileEntity>> entityType) {
        super(properties, 0.5f, 1.5f);
        this.grenadeType = grenadeType;
        this.entityType = entityType;
    }

    @Override
    protected void throwGrenade(ItemStack stack, Level level, Player player, float velocity, float chargePercent) {
        GrenadeProjectileEntity grenade = new GrenadeProjectileEntity(
                entityType.get(), level, player, grenadeType
        );
        grenade.setItem(stack);
        grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, 1.0F);
        level.addFreshEntity(grenade);
    }
}