package com.trd.entity;

import com.trd.entity.weapons.bullets.TurretBulletEntity;
import com.trd.entity.weapons.grenades.GravityGrenadeProjectileEntity;
import com.trd.entity.weapons.grenades.GrenadeIfProjectileEntity;
import com.trd.entity.weapons.grenades.GrenadeProjectileEntity;
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
                    // 0.05 как в 1.20.1: хитбокс и так расширяется на 0.5 в traceHit
                    .sized(0.05F, 0.05F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    // без этого клиент не получает скорость пули и не может
                    // развернуть её вдоль движения
                    .setShouldReceiveVelocityUpdates(true)
                    .build("trd:turret_bullet"));

    // === ГРАНАТЫ ===
    // Зажигательные и водородная гранаты добавятся вместе с ExplosionFire/ExplosionHydrogen.

    public static final DeferredHolder<EntityType<?>, EntityType<GravityGrenadeProjectileEntity>> GRAVITY_GRENADE_PROJECTILE =
            ENTITY_TYPES.register("gravity_grenade_projectile", () -> EntityType.Builder
                    .<GravityGrenadeProjectileEntity>of(GravityGrenadeProjectileEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .build("trd:gravity_grenade_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeProjectileEntity>> GRENADE_PROJECTILE =
            ENTITY_TYPES.register("grenade_projectile", () -> EntityType.Builder
                    .<GrenadeProjectileEntity>of(GrenadeProjectileEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .build("trd:grenade_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeProjectileEntity>> GRENADEHE_PROJECTILE =
            ENTITY_TYPES.register("grenadehe_projectile", () -> EntityType.Builder
                    .<GrenadeProjectileEntity>of(GrenadeProjectileEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .build("trd:grenadehe_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeProjectileEntity>> GRENADESMART_PROJECTILE =
            ENTITY_TYPES.register("grenadesmart_projectile", () -> EntityType.Builder
                    .<GrenadeProjectileEntity>of(GrenadeProjectileEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .build("trd:grenadesmart_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeProjectileEntity>> GRENADESLIME_PROJECTILE =
            ENTITY_TYPES.register("grenadeslime_projectile", () -> EntityType.Builder
                    .<GrenadeProjectileEntity>of(GrenadeProjectileEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .build("trd:grenadeslime_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeIfProjectileEntity>> GRENADE_IF_PROJECTILE =
            ENTITY_TYPES.register("grenade_if_projectile", () -> EntityType.Builder
                    .<GrenadeIfProjectileEntity>of(GrenadeIfProjectileEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("trd:grenade_if_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeIfProjectileEntity>> GRENADE_IF_SLIME_PROJECTILE =
            ENTITY_TYPES.register("grenade_if_slime_projectile", () -> EntityType.Builder
                    .<GrenadeIfProjectileEntity>of(GrenadeIfProjectileEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("trd:grenade_if_slime_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeIfProjectileEntity>> GRENADE_IF_HE_PROJECTILE =
            ENTITY_TYPES.register("grenade_if_he_projectile", () -> EntityType.Builder
                    .<GrenadeIfProjectileEntity>of(GrenadeIfProjectileEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .build("trd:grenade_if_he_projectile"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
