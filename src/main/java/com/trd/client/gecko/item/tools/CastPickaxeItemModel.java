package com.trd.client.gecko.item.tools;

import com.trd.item.tools.cast_pickaxes.CastPickaxeItem;
import com.trd.item.tools.cast_pickaxes.materials.CastPickaxeSteelItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class CastPickaxeItemModel extends GeoModel<CastPickaxeItem> {

    @Override
    public ResourceLocation getModelResource(CastPickaxeItem animatable) {
        if (animatable instanceof CastPickaxeSteelItem) {
            return ResourceLocation.fromNamespaceAndPath("trd", "geo/cast_pickaxe_steel.geo.json");
        }
        return ResourceLocation.fromNamespaceAndPath("trd", "geo/cast_pickaxe_iron.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CastPickaxeItem animatable) {
        if (animatable instanceof CastPickaxeSteelItem) {
            return ResourceLocation.fromNamespaceAndPath("trd", "textures/item/cast_pickaxe_steel.png");
        }
        return ResourceLocation.fromNamespaceAndPath("trd", "textures/item/cast_pickaxe_iron.png");
    }

    @Override
    public ResourceLocation getAnimationResource(CastPickaxeItem animatable) {
        // У железа и стали кость называется одинаково (cast_pickaxe), поэтому им
        // хватает одного файла анимаций. Отдельного cast_pickaxe_steel.animation.json
        // в проекте нет — если сослаться на него, стальная кирка остаётся без анимаций.
        return ResourceLocation.fromNamespaceAndPath("trd", "animations/cast_pickaxe_iron.animation.json");
    }

    /**
     * Скорость анимации подстраивается под время зарядки кирки, иначе анимация
     * «charging» (2 секунды = 40 тиков) отыгрывает не за тот же промежуток, что и
     * сама зарядка, и визуал расходится с механикой.
     * <p>
     * Ставим скорость безусловно (а не только когда она != 1.0), иначе контроллер
     * навсегда сохраняет скорость предыдущей кирки — например, 0.8 от стали.
     */
    @Override
    public void setCustomAnimations(CastPickaxeItem animatable, long instanceId, AnimationState<CastPickaxeItem> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        float speed = animatable.getAnimationSpeed();
        if (speed > 0.0f) {
            animationState.getController().setAnimationSpeed(speed);
        }
    }
}
