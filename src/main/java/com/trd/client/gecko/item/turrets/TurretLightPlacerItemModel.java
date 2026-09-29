package com.trd.client.gecko.item.turrets;

import net.minecraft.resources.ResourceLocation;
import com.trd.item.weapons.turrets.TurretLightPlacerBlockItem;
import software.bernie.geckolib.model.GeoModel;

// Обрати внимание: дженерик теперь TurretLightPlacerBlockItem
public class TurretLightPlacerItemModel extends GeoModel<TurretLightPlacerBlockItem> {

    @Override
    public ResourceLocation getModelResource(TurretLightPlacerBlockItem animatable) {
        return ResourceLocation.fromNamespaceAndPath("trd", "geo/turret_light.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(TurretLightPlacerBlockItem animatable) {
        return ResourceLocation.fromNamespaceAndPath("trd", "textures/entity/turret_light.png");
    }

    @Override
    public ResourceLocation getAnimationResource(TurretLightPlacerBlockItem animatable) {
        // В 1.20.1 здесь была ссылка на animations/turret_light_placer.animation.json,
        // которого не существует ни в одной ветке. У обоих placer-классов
        // registerControllers пуст, анимации здесь не проигрываются никогда —
        // поэтому указываем на пустой файл, который уже лежит в проекте.
        return ResourceLocation.fromNamespaceAndPath("trd", "animations/empty_animation.json");
    }
}
