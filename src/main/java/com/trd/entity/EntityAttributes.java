package com.trd.entity;

import com.trd.main.MainRegistry;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Регистрация набора атрибутов для живых сущностей мода.
 * <p>
 * В 1.20.1 это был метод в {@code MainRegistry}, подписанный на
 * {@code EntityAttributeCreationEvent}. В NeoForge 21.1 подписка на шине мода —
 * через {@link EventBusSubscriber#Bus#MOD}.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class EntityAttributes {

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.TURRET_LIGHT.get(),
                com.trd.entity.weapons.turrets.TurretLightEntity.createAttributes().build());
        // Блочная турель использует тот же набор, что и свободная
        event.put(ModEntities.TURRET_LIGHT_LINKED.get(),
                com.trd.entity.weapons.turrets.TurretLightEntity.createAttributes().build());
    }
}
