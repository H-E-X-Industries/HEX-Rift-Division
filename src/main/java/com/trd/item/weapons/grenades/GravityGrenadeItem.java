package com.trd.item.weapons.grenades;

import com.trd.entity.weapons.grenades.GravityGrenadeProjectileEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.List;

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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.trd.gravity_grenade.line1").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.trd.gravity_grenade.line2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.trd.grenade.charge_hint").withStyle(ChatFormatting.DARK_GRAY));
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