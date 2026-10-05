package com.trd.client.gecko.item.guns;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.client.overlay.MachineGunScope;
import com.trd.item.weapons.guns.MachineGunClientAnim;
import com.trd.main.MainRegistry;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.render.Vanilla;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Облик пушки на glTF вместо геколибовской geo-модели.
 * <p>
 * Модель и её клипы живут в {@code assets/trd/models/item/ap_17.gltf}: там же
 * зашиты анимации {@code shot}, {@code reload} и {@code flip}. Какой клип
 * показывать и на какой секунде — решает {@link MachineGunClientAnim}.
 * <p>
 * При ходьбе и беге от первого лица пулемёт остаётся полностью неподвижным на экране
 * за счёт точной математической компенсации ванильного покачивания (bobView).
 */
public class MachineGunGltfAppearance implements ItemAppearance {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/item/ap_17.gltf");

    @Override
    public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
        // Пока открыт прицел, оружие от первого лица не рисуется: в круге
        // оптики видна только мушка, а ствол и лента закрывали бы обзор.
        if (context.firstPerson() && MachineGunScope.isScoped()) {
            return null;
        }

        GemRenderGltfModel model = GemRenderModels.get(MODEL);
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

        return model.animation(current);
    }

    @Override
    public float seconds(ItemStack stack, ItemDisplayContext context, float partialTick) {
        return MachineGunClientAnim.seconds(partialTick);
    }

    @Override
    public void transform(ItemStack stack, ItemDisplayContext context, PoseStack poseStack) {
        if (context.firstPerson()) {
            stabilizeBobbing(context, poseStack);
        }

        // Локатор дула снимается строго ПОСЛЕ применения трансформации:
        // так вспышка выстрела и эффекты ствола идеально сопровождают пулемёт.
        MachineGunModel.captureItemMatrix(context, poseStack.last().pose());
    }

    /**
     * Компенсирует ванильное покачивание (bobView) от первого лица при ходьбе и беге,
     * делая пулемёт полностью неподвижным на экране.
     */
    private void stabilizeBobbing(ItemDisplayContext context, PoseStack poseStack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || !mc.options.bobView().get()) {
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        float partialTick = Vanilla.partialTick();
        float f = player.walkDist - player.walkDistO;
        float f1 = -(player.walkDist + f * partialTick);
        float f2 = Mth.lerp(partialTick, player.oBob, player.bob);

        if (Math.abs(f2) < 1.0E-5F) {
            return;
        }

        // Смещение и вращения из GameRenderer#bobView
        float tx = Mth.sin(f1 * (float) Math.PI) * f2 * 0.5F;
        float ty = -Math.abs(Mth.cos(f1 * (float) Math.PI) * f2);
        float rz = Mth.sin(f1 * (float) Math.PI) * f2 * 3.0F;
        float rx = Math.abs(Mth.cos(f1 * (float) Math.PI - 0.2F) * f2) * 5.0F;

        // Положение руки из ItemInHandRenderer#applyItemArmTransform
        boolean isRightHand = (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
        float handSign = isRightHand ? 1.0F : -1.0F;
        float armX = handSign * 0.56F;
        float armY = -0.52F;
        float armZ = -0.72F;

        // Применяем обратные преобразования:
        // 1. Сдвигаем обратно в точку вращения руки
        poseStack.translate(-armX, -armY, -armZ);
        // 2. Отменяем вращения в обратном порядке
        poseStack.mulPose(Axis.XP.rotationDegrees(-rx));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-rz));
        // 3. Отменяем поступательное смещение покачивания
        poseStack.translate(-tx, -ty, 0.0F);
        // 4. Возвращаем руку на исходное статическое положение
        poseStack.translate(armX, armY, armZ);
    }
}