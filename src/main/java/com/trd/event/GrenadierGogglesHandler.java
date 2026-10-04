package com.trd.event;

import com.trd.item.armor.GrenadierGogglesItem;
import com.trd.main.MainRegistry;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Очки гренадёра снижают входящий взрывный урон на 30%.
 * <p>
 * 1.20.1: {@code LivingHurtEvent}. В NeoForge 21.1 событие переименовано в
 * {@link LivingIncomingDamageEvent}, методы {@code getAmount}/{@code setAmount} прежние.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public class GrenadierGogglesHandler {

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
            if (helmet.getItem() instanceof GrenadierGogglesItem) {
                DamageSource source = event.getSource();
                if (source.is(DamageTypeTags.IS_EXPLOSION)) {
                    float originalDamage = event.getAmount();
                    float reducedDamage = originalDamage * 0.7f;
                    event.setAmount(reducedDamage);
                }
            }
        }
    }
}
