package com.trd.client.gltf.entity.bullets;

import com.hex.hexrender.core.ModelRegistry;
import com.hex.hexrender.loader.GltfModelAsset;
import com.trd.entity.weapons.bullets.TurretBulletEntity;
import com.trd.entity.weapons.bullets.TurretBulletEntity.AmmoType;
import com.trd.main.MainRegistry;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Модели и текстуры пули через чистый HexRender ModelRegistry.
 */
public final class TurretBulletVariants {

    /** Модель трассера: геометрия общая для всех типов патрона. */
    public static final ResourceLocation TRACER_MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/entity/turret_bullet.gltf");

    /** Модель обычного боезаряда: одна на все типы. */
    public static final ResourceLocation PLAIN_MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/entity/turret_bullet2.gltf");

    /** Базовая текстура трассера. */
    public static final ResourceLocation BASE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/entity/turret_bullet.png");

    private static final Map<AmmoType, Map<ResourceLocation, ResourceLocation>> OVERRIDES = new EnumMap<>(AmmoType.class);

    static {
        registerOverride(AmmoType.NORMAL, BASE_TEXTURE);
        registerOverride(AmmoType.PIERCING, texture("turret_bullet_ap"));
        registerOverride(AmmoType.HOLLOW, texture("turret_bullet_hollow"));
        registerOverride(AmmoType.INCENDIARY, texture("turret_bullet_fire"));
        registerOverride(AmmoType.RADIO, texture("turret_bullet_radio"));
        registerOverride(AmmoType.HE, BASE_TEXTURE);
        registerOverride(AmmoType.HE_SAVE, BASE_TEXTURE);
    }

    private static void registerOverride(AmmoType type, ResourceLocation targetTex) {
        OVERRIDES.put(type, Map.of(BASE_TEXTURE, targetTex));
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/entity/" + name + ".png");
    }

    public static void load() {
        ModelRegistry.obtain(TRACER_MODEL);
        ModelRegistry.obtain(PLAIN_MODEL);
    }

    private TurretBulletVariants() {
    }

    @Nullable
    public static GltfModelAsset tracerModel() {
        return ModelRegistry.fetch(TRACER_MODEL);
    }

    @Nullable
    public static GltfModelAsset plainModel() {
        return ModelRegistry.fetch(PLAIN_MODEL);
    }

    @Nullable
    public static GltfModelAsset modelFor(TurretBulletEntity entity) {
        return entity.isTracer() ? tracerModel() : plainModel();
    }

    @Nullable
    public static Map<ResourceLocation, ResourceLocation> tracerOverrides(@Nullable AmmoType type) {
        if (type == null) {
            return null;
        }
        return OVERRIDES.get(type);
    }
}
