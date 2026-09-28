package com.trd.client.gecko.item.ammo;

import com.trd.item.weapons.ammo.AmmoTurretItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

// Общая геометрия для всех 5 патронов; текстура берётся по id предмета
public class AmmoTurretModel extends GeoModel<AmmoTurretItem> {

    @Override
    public ResourceLocation getModelResource(AmmoTurretItem object) {
        return ResourceLocation.fromNamespaceAndPath("trd", "geo/ammo_turret.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(AmmoTurretItem object) {
        // Все пять патронов используют одну модель, но разные текстуры.
        // Путь текстуры повторяет registry id: ammo_turret_fire -> textures/item/ammo/ammo_turret_fire.png
        ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(object);
        if (registryName != null) {
            return ResourceLocation.fromNamespaceAndPath("trd", "textures/item/ammo/" + registryName.getPath() + ".png");
        }
        return ResourceLocation.fromNamespaceAndPath("trd", "textures/item/ammo/ammo_turret.png");
    }

    @Override
    public ResourceLocation getAnimationResource(AmmoTurretItem animatable) {
        return ResourceLocation.fromNamespaceAndPath("trd", "animations/ammo_turret.animation.json");
    }
}
