package com.trd.entity;

import com.trd.main.MainRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Реестр типов сущностей мода.
 * <p>
 * Записи добавляются вместе с классами сущностей: снаряды и гранаты придут в пачке
 * с гранатами, турели и пули — с оружием. Здесь только каркас (DeferredRegister +
 * шина событий), чтобы регистрация не расползалась по MainRegistry.
 * <p>
 * Атрибуты живых сущностей задаются в {@link EntityAttributes}.
 */
public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MainRegistry.MOD_ID);

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
