package com.trd.client.gecko.entity.bullets;

import com.trd.entity.weapons.bullets.TurretBulletEntity;
import com.trd.main.MainRegistry;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Общая геометрия пули для всех типов боезаряда; текстура подбирается по id патрона.
 */
public class TurretBulletModel extends GeoModel<TurretBulletEntity> {

    private static final String ITEM_PREFIX = "ammo_turret";
    private static final String BULLET_PREFIX = "turret_bullet";

    @Override
    public ResourceLocation getModelResource(TurretBulletEntity object) {
        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "geo/turret_bullet.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(TurretBulletEntity object) {
        // "trd:ammo_turret_fire" -> "textures/entity/turret_bullet_fire.png"
        String path = object.getAmmoId();
        if (path.contains(":")) {
            path = path.substring(path.indexOf(':') + 1);
        }

        if (path.startsWith(ITEM_PREFIX)) {
            String suffix = path.replace(ITEM_PREFIX, "");
            if (suffix.isEmpty()) {
                return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID,
                        "textures/entity/" + BULLET_PREFIX + ".png");
            }
            return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID,
                    "textures/entity/" + BULLET_PREFIX + suffix + ".png");
        }

        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID,
                "textures/entity/" + BULLET_PREFIX + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(TurretBulletEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "animations/turret_bullet.animation.json");
    }
}
