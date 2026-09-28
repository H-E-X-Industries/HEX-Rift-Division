package com.trd.entity;

import com.trd.entity.weapons.bullets.TurretBulletEntity;
import com.trd.main.MainRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Реестр типов сущностей мода.
 * <p>
 * Записи добавляются вместе с классами сущностей: снаряды и гранаты придут в пачке
 * с гранатами, турели — вместе с турелями. Здесь только каркас (DeferredRegister +
 * шина событий), чтобы регистрация не расползалась по MainRegistry.
 * <p>
 * Атрибуты живых сущностей задаются в {@link EntityAttributes}.
 */
public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MainRegistry.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<TurretBulletEntity>> TURRET_BULLET =
            ENTITY_TYPES.register("turret_bullet", () -> EntityType.Builder
                    .<TurretBulletEntity>of(TurretBulletEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("trd:turret_bullet"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
