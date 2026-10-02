package com.trd.entity;

import com.trd.entity.weapons.bullets.GilseEntity;
import com.trd.entity.weapons.bullets.TurretBulletEntity;
import com.trd.entity.weapons.turrets.TurretLightEntity;
import com.trd.entity.weapons.turrets.TurretLightLinkedEntity;
import com.trd.entity.weapons.grenades.GravityGrenadeProjectileEntity;
import com.trd.entity.weapons.grenades.GrenadeIfProjectileEntity;
import com.trd.entity.weapons.grenades.GrenadeNucProjectileEntity;
import com.trd.entity.weapons.grenades.GrenadeProjectileEntity;
import com.trd.entity.mobs.grenadier.GrenadierZombieEntity;
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
                    // Дальность трекинга умножается на 16, то есть 16 -> 256 блоков.
                    // Пуля летит до 256 блоков (MAX_FLIGHT_DISTANCE) и на скорости
                    // 6 блоков/тик проходит это расстояние за ~43 тика, поэтому
                    // при 256 блоках она исчезала с экрана, не долетев до предела.
                    // 32 -> 512 блоков с запасом.
                    .clientTrackingRange(32)
                    // Позиция шлётся каждый тик: на такой скорости при updateInterval
                    // больше 1 клиент не успевает получать координаты и пуля
                    // отрисовывается крупными ступенями.
                    .updateInterval(1)
                    // без этого клиент не получает скорость пули и не может
                    // развернуть её вдоль движения
                    .setShouldReceiveVelocityUpdates(true)
                    .build("trd:turret_bullet"));

    /**
     * Гильза: падает на землю после выстрела, живёт 30 секунд, не больше
     * пяти штук на стрелка. Физика — своя, как у {@code ItemEntity}: нужны
     * {@code noPhysics=false} и гравитация, иначе она считает, что лежит на
     * земле с самого спавна.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<GilseEntity>> GILSE =
            ENTITY_TYPES.register("gilse", () -> EntityType.Builder
                    .<GilseEntity>of(GilseEntity::new, MobCategory.MISC)
                    // 0.12, а не 0.2: хитбокс обязан совпадать с видимой
                    // моделью (она 0.08 в сечении и 0.2 в длину, см.
                    // MODEL_SCALE в GilseRenderer). Прежний куб 0.2 был втрое
                    // толще самой гильзы, из-за чего она оседала на пол-блока
                    // раньше, чем касалась стены или пола. Ради столкновений
                    // гильз друг с другом тоже берётся getBbWidth(), поэтому
                    // здесь важна именно эта величина.
                    .sized(0.12F, 0.12F)
                    .clientTrackingRange(8)
                    // Позиция шлётся каждый тик. Раз в 4 тика клиент получал
                    // разреженные координаты, а собственную физику он больше не
                    // считает, так что гильза на глаз прыгала через блок.
                    .updateInterval(1)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("trd:gilse"));

    // === ТУРЕЛИ ===

    public static final DeferredHolder<EntityType<?>, EntityType<TurretLightEntity>> TURRET_LIGHT =
            ENTITY_TYPES.register("turret_light", () -> EntityType.Builder
                    .<TurretLightEntity>of(TurretLightEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 0.8F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("trd:turret_light"));

    public static final DeferredHolder<EntityType<?>, EntityType<TurretLightLinkedEntity>> TURRET_LIGHT_LINKED =
            ENTITY_TYPES.register("turret_light_linked", () -> EntityType.Builder
                    .<TurretLightLinkedEntity>of(TurretLightLinkedEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 0.8F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("trd:turret_light_linked"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.trd.entity.weapons.missiles.MissileLightEntity>> MISSILE_LIGHT =
            ENTITY_TYPES.register("missile_light", () -> EntityType.Builder
                    .<com.trd.entity.weapons.missiles.MissileLightEntity>of(com.trd.entity.weapons.missiles.MissileLightEntity::new, MobCategory.MISC)
                    .sized(0.4F, 1.0F)
                    .clientTrackingRange(128)
                    .updateInterval(1)
                    // ОБЯЗАТЕЛЬНО: в 1.21 IEntityAdditionalSpawnData/writeSpawnData удалены,
                    // скорость ракеты приходит только со штатным пакетом спавна, и без этого
                    // флага клиент её не получит — ракета будет висеть на месте.
                    .setShouldReceiveVelocityUpdates(true)
                    .build("trd:missile_light"));

    // === ГРАНАТЫ ===

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

    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeProjectileEntity>> GRENADEFIRE_PROJECTILE =
            ENTITY_TYPES.register("grenadefire_projectile", () -> EntityType.Builder
                    .<GrenadeProjectileEntity>of(GrenadeProjectileEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .build("trd:grenadefire_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeIfProjectileEntity>> GRENADE_IF_PROJECTILE =
            ENTITY_TYPES.register("grenade_if_projectile", () -> EntityType.Builder
                    .<GrenadeIfProjectileEntity>of(GrenadeIfProjectileEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("trd:grenade_if_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeIfProjectileEntity>> GRENADE_IF_FIRE_PROJECTILE =
            ENTITY_TYPES.register("grenade_if_fire_projectile", () -> EntityType.Builder
                    .<GrenadeIfProjectileEntity>of(GrenadeIfProjectileEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .build("trd:grenade_if_fire_projectile"));

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

    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeNucProjectileEntity>> GRENADE_NUC_PROJECTILE =
            ENTITY_TYPES.register("grenade_nuc_projectile", () -> EntityType.Builder
                    .<GrenadeNucProjectileEntity>of(GrenadeNucProjectileEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(64)
                    .updateInterval(2)
                    .build("trd:grenade_nuc_projectile"));

    // === МОБЫ ===

    public static final DeferredHolder<EntityType<?>, EntityType<GrenadierZombieEntity>> GRENADIER_ZOMBIE =
            ENTITY_TYPES.register("grenadier_zombie", () -> EntityType.Builder
                    .<GrenadierZombieEntity>of(GrenadierZombieEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F) // Размеры как у обычного зомби
                    .build("trd:grenadier_zombie"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
