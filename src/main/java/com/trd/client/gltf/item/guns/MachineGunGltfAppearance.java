package com.trd.client.gltf.item.guns;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.client.overlay.MachineGunScope;
import com.trd.item.weapons.guns.MachineGunClientAnim;
import com.trd.main.MainRegistry;
import com.hex.hexrender.asset.HexModels;
import com.hex.hexrender.direct.ItemAppearance;
import com.hex.hexrender.gltf.HexGltfModel;
import com.hex.hexrender.gltf.GltfAnimation;
import com.hex.hexrender.render.Vanilla;
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
 * <p>
 * <b>Анимируется пушка только в руках.</b> Состояние у
 * {@link MachineGunClientAnim} одно на весь клиент, и раньше клип отдавался всем
 * контекстам отрисовки сразу: выстрел дёргал не только ствол в руке, но и значок
 * в инвентаре, и в хотбаре, и на полке в рамке, и в голове. Вне рук возвращается
 * исходная поза модели — см. {@link #isHand}.
 */
public class MachineGunGltfAppearance implements ItemAppearance {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/item/ap_17.gltf");

    /**
     * Рисуется ли предмет в руке.
     * <p>
     * Только четыре контекста рук. Всё остальное — инвентарь, хотбар, полка,
     * голова, брошенный предмет, значок в креативе — обязано стоять в исходной
     * позе: это значок, а не оружие в руках, и жить анимацией выстрела он не
     * должен.
     * <p>
     * Сравнения, а не {@code switch}: {@link ItemDisplayContext} у NeoForge
     * расширяемый, и значения, добавленные чужим модом, попали бы в ветку
     * «не рука» только при честном {@code default}.
     */
    private static boolean isHand(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    @Override
    public HexGltfModel model(ItemStack stack, ItemDisplayContext context) {
        // Пока открыт прицел, оружие от первого лица не рисуется: в круге
        // оптики видна только мушка, а ствол и лента закрывали бы обзор.
        if (context.firstPerson() && MachineGunScope.isScoped()) {
            return null;
        }

        HexGltfModel model = HexModels.get(MODEL);
        if (model != null) {
            MachineGunClientAnim.syncDurations(model);
        }
        return model;
    }

    @Override
    public GltfAnimation clip(ItemStack stack, ItemDisplayContext context) {
        // Не в руке — исходная поза модели, без анимации.
        if (!isHand(context)) {
            return null;
        }

        HexGltfModel model = model(stack, context);
        if (model == null) return null;

        String current = MachineGunClientAnim.current();
        if (current == null) return null;

        return model.animation(current);
    }

    @Override
    public float seconds(ItemStack stack, ItemDisplayContext context, float partialTick) {
        // Вне рук клипа нет, поэтому и времени клипа не нужно: кадр и не должен
        // ехать. Без этой проверки значок в инвентаре продолжал бы двигаться по
        // анимации даже при null-клипе.
        if (!isHand(context)) {
            return 0.0F;
        }
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
