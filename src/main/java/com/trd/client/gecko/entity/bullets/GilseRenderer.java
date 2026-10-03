package com.trd.client.gecko.entity.bullets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.entity.weapons.bullets.GilseEntity;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Гильза на glTF через GemRender.
 * <p>
 * Модель статичная, поэтому клипов нет и передаём {@code null}: GemRender
 * берёт позу покоя. Рендер идёт через {@link DirectPass#LEVEL}, тот же путь,
 * что и у пули.
 */
public class GilseRenderer extends EntityRenderer<GilseEntity> {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID,
                    "models/entity/gilse.gltf");

    /**
     * Увеличение модели относительно исходного размера.
     * <p>
     * Размер сущности ({@code EntityType#sized}) задаёт только хитбокс, на
     * геометрию glTF он не влияет, поэтому масштаб ставится здесь.
     */
    private static final float MODEL_SCALE = 2.0F;

    /**
     * Сдвиг модели вдоль собственной оси цилиндра, в единицах до
     * {@link #MODEL_SCALE}.
     * <p>
     * В glTF цилиндр смещён вдоль своей оси: меш занимает Z от -0.03125 до
     * +0.06875, то есть центр геометрии стоит на +0.01875. Поскольку модель
     * рисуется от начала координат сущности, без этого сдвига половинка гильзы
     * уезжала бы в одну сторону от хитбокса.
     */
    private static final float MODEL_AXIS_OFFSET = 0.01875F;

    public GilseRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(GilseEntity entity) {
        // Текстура живёт внутри glTF и подставляется GemRender'ом.
        return ResourceLocation.withDefaultNamespace("missingno");
    }

    @Override
    public boolean shouldShowName(GilseEntity entity) {
        return false;
    }

    @Override
    public boolean shouldRender(GilseEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(0.1F));
    }

    /**
     * Сглаживает позицию гильзы.
     * <p>
     * Физику считает сервер, а приходит она дискретно. Встроенная интерполяция
     * уровня тут не работает: {@code ClientLevel#tickNonPassenger} перед тиком
     * вызывает {@code setOldPosAndRot()}, поэтому {@code xOld} на клиенте равен
     * текущей позиции и подстановка в рендер выходит нулевой. Смещение
     * считается целиком: сглаженная точка минус текущая позиция. Сам якорь и
     * его сброс при отсутствии пакетов живут в {@link GilseEntity}.
     */
    @Override
    public net.minecraft.world.phys.Vec3 getRenderOffset(GilseEntity entity, float partialTick) {
        return entity.renderOffset(partialTick);
    }

    @Override
    public void render(GilseEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight) {
        var model = GemRenderModels.get(MODEL);
        if (model == null) {
            return;
        }

        poseStack.pushPose();

        // Модель центрируется по хитбоксу ДО масштаба, и это не перестановка
        // для красоты: PoseStack домножает матрицу справа, поэтому translate
        // после scale умножается на MODEL_SCALE. Сдвиг 0.06 превращался в 0.12,
        // и при радиусе модели 0.04 гильза висела над полом на восемь
        // сантиметров — ровно то «зависает над поверхностью», ради которого
        // всё затевалось.
        poseStack.translate(0.0F, entity.getBbHeight() * 0.5F, 0.0F);
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        // Сдвиг вдоль оси цилиндра задаётся уже после масштаба: он остаётся в
        // координатах самой модели и уезжает вместе с ней.
        poseStack.translate(0.0F, 0.0F, -MODEL_AXIS_OFFSET);

        // Поворот вдоль полёта плюс прокрутка вокруг оси гильзы.
        //
        // Углы приезжают с сервера в SynchedEntityData и не выводятся из
        // скорости: скорость гильзы клиенту не синхронизируется, а выведенный
        // на клиенте угол смотрел бы не туда. Задаёт их сервер на вылете, ещё
        // до addFreshEntity, поэтому первый кадр уже показывает нужную позу.
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getRenderYaw(partialTick) - 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getRenderPitch(partialTick)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRenderRoll(partialTick)));

        var matrix = poseStack.last().pose();
        DirectRenderer.submit(model, (com.wf.gemrender.gltf.GltfAnimation) null, 0.0f,
                matrix, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF, DirectPass.LEVEL,
                com.wf.gemrender.texture.VariantUv.NONE);

        poseStack.popPose();
    }
}