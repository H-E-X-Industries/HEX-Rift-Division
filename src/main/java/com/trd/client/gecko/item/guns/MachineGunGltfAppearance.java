package com.trd.client.gecko.item.guns;

import com.trd.client.overlay.MachineGunScope;
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
 * Геколиб в проекте не осталось: от него отказывались ради перехода на glTF, и
 * всё, что раньше жило в его animation.json, теперь живёт в самой модели —
 * см. {@link MachineGunModel}.
 * <p>
 * Геколибовский рендерер подставлял 5 разных текстур по типу заряженного
 * патрона; в glTF текстура одна, и вариативности тут больше нет.
 */
public class MachineGunGltfAppearance implements ItemAppearance {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/item/ap_17.gltf");

    @Override
    public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
        // Пока открыт прицел, оружие от первого лица не рисуется: в круге
        // оптики видна только мушка, а ствол и лента закрывали бы обзор.
        // GemRender трактует null как «нечего рисовать» и просто выходит из
        // draw(), поэтому возвращать пустую модель тут безопасно.
        //
        // Проверка именно first-person: в третьем лице пушку в руках другого
        // игрока по-прежнему видно, иначе она исчезала бы у всех, кто смотрит
        // на стрелка.
        if (context.firstPerson() && MachineGunScope.isScoped()) {
            return null;
        }

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
        // Локатор дула снимается именно здесь. GemRenderItemRenderer#draw зовёт
        // transform последним перед тем, как отдать матрицу в
        // DirectRenderer.submit, поэтому внутри PoseStack уже лежит полная
        // матрица «модель → мир» — со всеми смещениями предметного контекста.
        // Взять её позже негде, а без неё кость модели остаётся в своей системе
        // координат, и вспышка выстрела уезжает от ствола.
        MachineGunModel.captureItemMatrix(context, poseStack.last().pose());
    }
}