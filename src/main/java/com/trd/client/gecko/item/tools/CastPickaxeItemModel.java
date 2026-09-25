package com.trd.client.gecko.item.tools;

import com.trd.item.tools.cast_pickaxes.CastPickaxeItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class CastPickaxeItemModel extends GeoModel<CastPickaxeItem> {

    @Override
    public ResourceLocation getModelResource(CastPickaxeItem animatable) {
        if (animatable instanceof com.trd.item.tools.cast_pickaxes.materials.CastPickaxeSteelItem) {
            return ResourceLocation.fromNamespaceAndPath("trd", "geo/cast_pickaxe_steel.geo.json");
        }
        return ResourceLocation.fromNamespaceAndPath("trd", "geo/cast_pickaxe_iron.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CastPickaxeItem animatable) {
        if (animatable instanceof com.trd.item.tools.cast_pickaxes.materials.CastPickaxeSteelItem) {
            return ResourceLocation.fromNamespaceAndPath("trd", "textures/item/cast_pickaxe_steel.png");
        }
        return ResourceLocation.fromNamespaceAndPath("trd", "textures/item/cast_pickaxe_iron.png");
    }

    @Override
    public ResourceLocation getAnimationResource(CastPickaxeItem animatable) {
        if (animatable instanceof com.trd.item.tools.cast_pickaxes.materials.CastPickaxeSteelItem) {
            return ResourceLocation.fromNamespaceAndPath("trd", "animations/cast_pickaxe_steel.animation.json");
        }
        return ResourceLocation.fromNamespaceAndPath("trd", "animations/cast_pickaxe_iron.animation.json");
    }
}
