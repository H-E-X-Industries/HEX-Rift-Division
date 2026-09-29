package com.trd.explosion.logic;

import com.trd.main.MainRegistry;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Обнуляет урон сущностям от ванильного взрыва, пока выполняется
 * {@link VanillaExplosionDamage#withoutEntityDamage(Runnable)}.
 * <p>
 * Нужен, чтобы урон гранат считался ровно по их собственному параметру, а не складывался
 * с ванильными ~1-8 единицами. Разрушение блоков, дроп и отбрасывание не трогаем:
 * {@code Explosion#finalizeExplosion} применяет их независимо от результата {@code hurt}.
 * <p>
 * В 1.20.1 здесь был {@code LivingHurtEvent}; в 1.21 NeoForge заменил его на
 * {@link LivingIncomingDamageEvent} — событие входящего урона с теми же
 * {@code getSource()}/{@code setAmount()}.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public class VanillaExplosionDamageGuard {

    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        if (VanillaExplosionDamage.isSuppressed() && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            event.setAmount(0.0F);
        }
    }
}
