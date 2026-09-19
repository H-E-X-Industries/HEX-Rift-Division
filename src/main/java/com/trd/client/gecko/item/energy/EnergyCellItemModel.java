package com.trd.client.gecko.item.energy;

import com.trd.main.MainRegistry;
import net.minecraft.resources.ResourceLocation;
import com.trd.item.industrial.energy.EnergyCellItem;
import software.bernie.geckolib.model.GeoModel;

public class EnergyCellItemModel extends GeoModel<EnergyCellItem> {

    @Override
    public ResourceLocation getModelResource(EnergyCellItem animatable) {
        // assets/trd/geo/energy_cell_basic.geo.json
        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "geo/energy_cell_basic.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(EnergyCellItem animatable) {
        // assets/trd/textures/item/energy_cell_basic.png
        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/item/energy_cell_basic.png");
    }

    @Override
    public ResourceLocation getAnimationResource(EnergyCellItem animatable) {
        // Нет анимаций — используем пустой файл
        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "animations/empty.animation.json");
    }
}
