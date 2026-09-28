package com.trd.client.gecko.item.guns;

import com.trd.item.weapons.guns.MachineGunItem;
import com.trd.main.MainRegistry;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class MachineGunModel extends GeoModel<MachineGunItem> {

    @Override
    public ResourceLocation getModelResource(MachineGunItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "geo/machinegun.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(MachineGunItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/item/gun/machinegun.png");
    }

    @Override
    public ResourceLocation getAnimationResource(MachineGunItem animatable) {
        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "animations/machinegun.animation.json");
    }
}
