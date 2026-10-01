package com.trd.client.gecko.item.guns;

import com.trd.item.weapons.guns.MachineGunClientAnim;
import com.trd.main.MainRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Облик пушки на glTF вместо геколибовской geo-модели.
 * <p>
 * Модель и её клипы живут в {@code assets/trd/models/item/ap_17.gltf}: там же
 * зашиты анимации {@code shot}, {@code reload} и {@code flip}. Какой клип
 * показывать и на какой секунде — решает {@link MachineGunClientAnim}.
 * <p>
 * Геколибовский рендерер подставлял 5 разных текстур по типу заряженного
 * патрона; в glTF текстура одна, и вариативности тут больше нет.
 */
public class MachineGunGltfAppearance implements ItemAppearance {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/item/ap_17.gltf");

    @Override
    public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
        GemRenderGltfModel model = GemRenderModels.get(MODEL);
        // Длины клипов берём из самого glTF, а не из констант в коде: при
        // переэкспорте модели из Blockbench они изменятся, и захардкоженные
        // значения снова разойдутся с анимацией.
        if (model != null) {
            MachineGunClientAnim.syncDurations(model);
        }
        return model;
    }

    @Override
    public GltfAnimation clip(ItemStack stack, ItemDisplayContext context) {
        GemRenderGltfModel model = model(stack, context);
        if (model == null) return null;

        String current = MachineGunClientAnim.current();
        if (current == null) return null;

        // Клип мог не загрузиться — лучше показать пушку в покое, чем упасть.
        return model.animation(current);
    }

    @Override
    public float seconds(ItemStack stack, ItemDisplayContext context, float partialTick) {
        return MachineGunClientAnim.seconds(partialTick);
    }

    @Override
    public void transform(ItemStack stack, ItemDisplayContext context, PoseStack poseStack) {
        // Трансформацию не трогаем: любая попытка сдвинуть модель здесь
        // двигает само оружие в руке. Покачивание камеры (bobView) при этом
        // остаётся, его глушат на уровне рендера — см. MachineGunScopeOverlay.
    }
}