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
        // Здесь появятся вызовы event.put(...) вместе с классами сущностей:
        //   TURRET_LIGHT и TURRET_LIGHT_LINKED -> TurretLightEntity.createAttributes()
    }
}
