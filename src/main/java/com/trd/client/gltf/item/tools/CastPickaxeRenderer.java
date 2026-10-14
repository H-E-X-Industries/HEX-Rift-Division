package com.trd.client.gltf.item.tools;

import com.trd.item.tools.cast_pickaxes.CastPickaxeClientAnim;
import com.hex.hexrender.direct.DirectPass;
import com.hex.hexrender.direct.DirectRenderer;
import com.hex.hexrender.direct.GltfVanillaRenderer;
import com.hex.hexrender.direct.IrisDirectBridge;
import com.hex.hexrender.direct.TextureSlots;
import com.hex.hexrender.gltf.GemRenderGltfModel;
import com.hex.hexrender.gltf.GltfAnimation;
import com.hex.hexrender.render.Vanilla;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Рендерер литой кирки на glTF.
 *
 * <h2>Зачем он свой, а не {@code GemRenderItemRenderer}</h2>
 * Обе кирки рисуются одной моделью и различаются только текстурой, а
 * {@code GemRenderItemRenderer} текстуру предмету подменить не умеет: у него нет
 * такого обратного хода, он зовёт {@code DirectRenderer#submit} без слотов.
 *
 * <p>Слоты для этого и сделаны: если в материале модели стоит метка
 * {@value CastPickaxeGltfAppearance#TEXTURE_SLOT}, то такая часть рисуется
 * <b>только</b> с текстурой, которую подставит вызывающий. Здесь она и
 * подставляется — та, что выбрал облик по предмету.
 *
 * <p>Остальное — сдвиг на пол-пикселя, трансформация облика, выбор клипа и
 * времени, проход отрисовки — повторяет {@code GemRenderItemRenderer#draw}.
 * Смешивания между клипами здесь нет и не предвидится: облик его не включает, а
 * клипы у кирки не пересекаются, потому что автомат держит ровно один активный.
 */
public class CastPickaxeRenderer extends BlockEntityWithoutLevelRenderer {

    private final CastPickaxeGltfAppearance appearance;

    /** Подставная текстура для слота из материала модели. */
    private final TextureSlots slots = new TextureSlots() {
        @Override
        public @Nullable ResourceLocation texture(String slot) {
            if (!CastPickaxeGltfAppearance.TEXTURE_SLOT.equals(slot)) {
                return null;
            }
            return CastPickaxeGltfAppearance.texture();
        }
    };

    public CastPickaxeRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
        this.appearance = CastPickaxeGltfAppearance.create();
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        GemRenderGltfModel model = appearance.model(stack, displayContext);
        if (model == null) {
            return;
        }

        float partialTick = Vanilla.partialTick();

        poseStack.pushPose();
        try {
            // Пол-пикселя: без него модель рисуется, сдвинутая на четверть блока
            // относительно рамки, в которую её показывает предметный контекст.
            poseStack.translate(0.5F, 0.5F, 0.5F);
            appearance.transform(stack, displayContext, poseStack);

            if (IrisDirectBridge.isShaderPackInUse()) {
                GltfVanillaRenderer.render(
                        model,
                        appearance.clip(stack, displayContext),
                        appearance.seconds(stack, displayContext, partialTick),
                        poseStack,
                        bufferSource,
                        packedLight,
                        packedOverlay,
                        appearance.tint(stack, displayContext),
                        appearance.variant(stack, displayContext),
                        slots);
            } else {
                DirectRenderer.submit(
                        model,
                        appearance.clip(stack, displayContext),
                        appearance.seconds(stack, displayContext, partialTick),
                        poseStack.last().pose(),
                        packedLight,
                        packedOverlay,
                        appearance.tint(stack, displayContext),
                        passFor(displayContext),
                        appearance.variant(stack, displayContext),
                        slots);
            }
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * Проход отрисовки по контексту.
     *
     * <p>Тот же расклад, что у {@code GemRenderItemRenderer}: в окне интерфейса —
     * {@link DirectPass#GUI}, в руке от первого лица — {@link DirectPass#HAND},
     * всё остальное — {@link DirectPass#LEVEL}. Руки от третьего лица тоже идут
     * по мировому проходу: они рисуются вместе с сущностью в мире.
     */
    private static DirectPass passFor(ItemDisplayContext context) {
        if (context == ItemDisplayContext.GUI) {
            return DirectPass.GUI;
        }
        if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            return DirectPass.HAND;
        }
        return DirectPass.LEVEL;
    }
}