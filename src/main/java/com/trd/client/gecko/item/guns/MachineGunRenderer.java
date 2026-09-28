package com.trd.client.gecko.item.guns;

import com.trd.item.weapons.guns.MachineGunItem;
import com.trd.main.MainRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Текстура ствола и ленты подбирается по заряженному боеприпасу, а кости
 * ленты скрываются по мере расхода патронов.
 */
public class MachineGunRenderer extends GeoItemRenderer<MachineGunItem> {

    public MachineGunRenderer() {
        super(new MachineGunModel());
    }

    @Override
    public void renderRecursively(
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            MachineGunItem animatable,
            software.bernie.geckolib.cache.object.GeoBone bone,
            net.minecraft.client.renderer.RenderType renderType,
            net.minecraft.client.renderer.MultiBufferSource bufferSource,
            com.mojang.blaze3d.vertex.VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            int color
    ) {
        ItemStack stack = this.getCurrentItemStack();
        if (stack == null) {
            super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
                    isReRender, partialTick, packedLight, packedOverlay, color);
            return;
        }

        int ammo = animatable.getAmmo(stack);
        String boneName = bone.getName();

        int visibleAmmoInBelt = Math.max(0, ammo - 1);
        if (boneName.equals("ammo3")) {
            if (visibleAmmoInBelt < 3) return;
        }
        if (boneName.equals("ammo2")) {
            if (visibleAmmoInBelt < 2) return;
        }
        if (boneName.equals("ammo")) {
            if (visibleAmmoInBelt < 1) return;
        }

        if (boneName.equals("gilse")) {
            if (ammo <= 0) return;
        }

        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, color);
    }

    @Override
    public ResourceLocation getTextureLocation(MachineGunItem animatable) {
        ItemStack stack = this.getCurrentItemStack();

        if (stack == null || stack.isEmpty()) {
            return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/item/gun/machinegun.png");
        }

        String loadedId = animatable.getLoadedAmmoID(stack);
        if (loadedId == null || loadedId.isEmpty()) {
            return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/item/gun/machinegun.png");
        }

        if (loadedId.contains(":")) {
            loadedId = loadedId.substring(loadedId.indexOf(':') + 1);
        }

        String textureName = "machinegun";
        if (loadedId.contains("piercing")) {
            textureName = "machinegun_piercing";
        } else if (loadedId.contains("hollow")) {
            textureName = "machinegun_hollow";
        } else if (loadedId.contains("radio")) {
            textureName = "machinegun_radio";
        } else if (loadedId.contains("fire")) {
            textureName = "machinegun_fire";
        }

        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/item/gun/" + textureName + ".png");
    }
}
