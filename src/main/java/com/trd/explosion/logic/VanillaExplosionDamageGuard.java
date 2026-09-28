package com.trd.explosion.logic;

import com.trd.main.MainRegistry;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Обнуляет урон сущностям от ванильного взрыва, пока выполняется
 * {@link VanillaExplosionDamage#withoutEntityDamage(Runnable)}.
 * <p>
 * Нужен, чтобы урон гранат считался ровно по их собственному параметру, а не складывался
 * с ванильными ~1-8 единицами. Разрушение блоков, дроп и отбрасывание не трогаем:
 * {@code Explosion#finalizeExplosion} применяет их независимо от результата {@code hurt}.
 */
@Mod.EventBusSubscriber(modid = MainRegistry.MOD_ID)
public class VanillaExplosionDamageGuard {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (VanillaExplosionDamage.isSuppressed() && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            event.setAmount(0.0F);
        }
    }
}
