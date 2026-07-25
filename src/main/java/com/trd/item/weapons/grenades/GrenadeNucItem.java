package com.trd.item.weapons.grenades;

import com.trd.entity.weapons.grenades.GrenadeNucProjectileEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    public void appendHoverText(ItemStack stack, @Nullable Level level, @Nullable List<Component> tooltip, TooltipFlag flag) {
        if (tooltip == null) return;
        tooltip.add(Component.translatable("tooltip.trd.grenade_nuc.line1").withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.trd.grenade_nuc.line2").withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.trd.grenade_nuc.line3").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.trd.grenade.charge_hint").withStyle(ChatFormatting.DARK_GRAY));
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