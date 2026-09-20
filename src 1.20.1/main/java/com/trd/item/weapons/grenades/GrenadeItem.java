package com.trd.item.weapons.grenades;

import com.trd.entity.weapons.grenades.GrenadeProjectileEntity;
import com.trd.entity.weapons.grenades.GrenadeType;
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

public class GrenadeItem extends ChargableGrenadeItem {

    private final GrenadeType grenadeType;
    private final RegistryObject<EntityType<GrenadeProjectileEntity>> entityType;

    public GrenadeItem(Properties properties, GrenadeType grenadeType, RegistryObject<EntityType<GrenadeProjectileEntity>> entityType) {
        super(properties, 0.5f, 1.5f);
        this.grenadeType = grenadeType;
        this.entityType = entityType;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.trd.grenade.common.line1").withStyle(ChatFormatting.YELLOW));
        String key = switch (grenadeType) {
            case SMART -> "tooltip.trd.grenade.smart.line2";
            case FIRE -> "tooltip.trd.grenade.fire.line2";
            case SLIME -> "tooltip.trd.grenade.slime.line2";
            case STANDARD -> "tooltip.trd.grenade.standard.line2";
            case HE -> "tooltip.trd.grenade.he.line2";
            default -> "tooltip.trd.grenade.default.line2";
        };
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.trd.grenade.charge_hint").withStyle(ChatFormatting.DARK_GRAY));
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