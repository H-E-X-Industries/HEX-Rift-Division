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
     * Смещение модели вдоль собственной оси цилиндра, в единицах до
     * {@link #MODEL_SCALE}.
     * <p>
     * В glTF цилиндр смещён вдоль своей оси: меш занимает Z от -0.031 до +0.069,
     * то есть центр геометрии стоит на +0.0188. Поскольку модель рисуется от
     * начала координат сущности, без этого сдвига половинка гильзы уезжала бы
     * в одну сторону от хитбокса.
     */
    private static final float MODEL_AXIS_OFFSET = 0.0188F;

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
     * считается целиком: сглаженная точка минус текущая позиция.
     */
    @Override
    public net.minecraft.world.phys.Vec3 getRenderOffset(GilseEntity entity, float partialTick) {
        if (!entity.level().isClientSide) {
            return net.minecraft.world.phys.Vec3.ZERO;
        }
        return new net.minecraft.world.phys.Vec3(
                net.minecraft.util.Mth.lerp(partialTick, entity.getServerPrevX(), entity.getX()) - entity.getX(),
                net.minecraft.util.Mth.lerp(partialTick, entity.getServerPrevY(), entity.getY()) - entity.getY(),
                net.minecraft.util.Mth.lerp(partialTick, entity.getServerPrevZ(), entity.getZ()) - entity.getZ()
        );
    }

    @Override
    public void render(GilseEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight) {
        var model = GemRenderModels.get(MODEL);
        if (model == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        // Сдвиг вдоль оси цилиндра задаётся ДО поворотов: PoseStack
        // домножает матрицу справа, поэтому translate здесь остаётся в
        // координатах самой модели и уезжает вместе с ней.
        poseStack.translate(0.0F, 0.0F, -MODEL_AXIS_OFFSET);

        // Поворот вдоль полёта плюс прокрутка вокруг оси гильзы.
        //
        // Углы приезжают с сервера в SynchedEntityData и не выводятся из
        // скорости: скорость гильзы клиенту не синхронизируется, а выведенный
        // на клиенте угол смотрел бы не туда. Задаёт их сервер один раз на
        // вылете и ещё раз при первом ударе, поэтому между тиками интерполировать
        // тут нечего и гильза не дёргается на падении.
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getRenderYaw(partialTick) - 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getRenderPitch(partialTick)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRenderSpin(partialTick)));

        // Хитбокс у сущности привязан к нижней грани (EntityDimensions
        // #makeBoundingBox отдаёт y..y+height), а модель рисуется от начала
        // координат. Без этого подъёма нижняя половина модели уходила под пол,
        // и гильза выглядела наполовину вросшей в землю. Сдвиг добавляется уже
        // после поворотов, то есть строго вверх в мировых координатах.
        poseStack.translate(0.0F, entity.getBbHeight() * 0.5F, 0.0F);

        var matrix = poseStack.last().pose();
        DirectRenderer.submit(model, (com.wf.gemrender.gltf.GltfAnimation) null, 0.0f,
                matrix, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF, DirectPass.LEVEL,
                com.wf.gemrender.texture.VariantUv.NONE);

        poseStack.popPose();
    }
}