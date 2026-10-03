package com.trd.client.gecko.entity.bullets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.entity.weapons.bullets.TurretBulletEntity;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.texture.VariantUv;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Пуля на glTF через GemRender.
 * <p>
 * Разворот задаётся так же, как был у геколибовского рендерера: сначала yaw,
 * потом pitch, потом собственное вращение пули вокруг оси полёта. Углы берутся
 * с интерполяцией между прошлым и текущим тиком, иначе на высоком FPS пуля
 * дёргалась бы между тиками.
 * <p>
 * Модель и яркость выбираются по заряженному патрону:
 * <ul>
 *   <li>трассер — своя модель со шлейфом, своя текстура на тип патрона, и
 *       {@link LightTexture#FULL_BRIGHT}: он должен читаться на любом фоне, иначе
 *       в тёмном коридоре его просто не видно, а сам выстрел читается только по
 *       звуку;</li>
 *   <li>обычный боезаряд — своя модель с одной текстурой на все типы и обычное
 *       освещение точки.</li>
 * </ul>
 * Раньше трассер был единственным вариантом, и яркость {@code FULL_BRIGHT}
 * доставалась всем пулям сразу; теперь она достаётся именно тем, кто её
 * заслужил, — светящимся.
 */
public class TurretBulletGltfRenderer extends EntityRenderer<TurretBulletEntity> {

    /**
     * Увеличение модели пули.
     * <p>
     * Единица — модель в размере хитбокса, как и у гильзы. Половина у обеих
     * моделей: геометрия у них разная (обычная пуля шире трассирующей полосы
     * почти вдвое, 0.0375 против 0.025 блока), и в исходном размере на скорости
     * 240 м/с пуля читалась заметно крупнее настоящей — особенно обычная, у
     * которой после уменьшения остаётся около 0.019 блока в толщину и 0.047 в
     * длину, то есть примерно два сантиметра на двадцатимиллиметровом патроне.
     * <p>
     * Масштаб единый для трассирующей и обычной: уменьшить надо обе, а разница
     * между ними задана геометрией, и ручка одна.
     */
    private static final float MODEL_SCALE = 0.5F;

    public TurretBulletGltfRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(TurretBulletEntity entity) {
        return ResourceLocation.withDefaultNamespace("missingno");
    }

    @Override
    public boolean shouldShowName(TurretBulletEntity entity) {
        return false;
    }

    @Override
    public boolean shouldRender(TurretBulletEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(0.2F));
    }

    @Override
    public Vec3 getRenderOffset(TurretBulletEntity entity, float partialTicks) {
        // Позиция приходит с сервера дискретно — по пакету на тик, то есть
        // 20 раз в секунду. На скорости 6 блоков/тик это 20 крупных ступенек в
        // секунду, и без сглаживания пуля шла рывками, будто по точкам.
        //
        // Смещение считаем целиком: сглаженная точка минус текущая позиция
        // сущности. Раньше здесь вычиталась ещё и интерполированная xOld, но
        // на клиенте xOld каждый тик прижимается к текущей точке вызовом
        // setOldPosAndRot(), из-за чего обе величины совпадали и смещение
        // всегда выходило нулевым — то есть никакого сглаживания не было.
        // Сам якорь и его сброс при отсутствии пакетов живут в
        // TurretBulletEntity.
        return entity.renderOffset(partialTicks);
    }

    @Override
    public void render(TurretBulletEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        boolean tracer = entity.isTracer();
        var model = TurretBulletVariants.modelFor(entity);
        if (model == null) {
            return;
        }

        // Направление берём из вектора полёта, а не из углов сущности: углы
        // приходят отдельным пакетом поворота и заметно отстают от позиции.
        Vec3 dir = entity.flightDirection();
        if (dir.lengthSqr() < 1.0E-8D) {
            // Скорости ещё нет — fallback на углы сущности, иначе пуля не видна.
            dir = entity.getDeltaMovement();
            if (dir.lengthSqr() < 1.0E-8D) {
                return;
            }
        }

        double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yawDeg = (float) (Math.atan2(dir.x, dir.z) * (180.0D / Math.PI));
        float pitchDeg = (float) (Math.atan2(dir.y, horizontal) * (180.0D / Math.PI));

        poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg - 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitchDeg));
        // Собственное вращение пули вокруг оси полёта.
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.spin));

        poseStack.pushPose();
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        // Полоса атласа — только у трассера: у него своя ячейка на тип
        // патрона, и переключается она сменой UV, а не перезагрузкой модели.
        // У обычного боезаряда вариантов нет вовсе, и рисуется он целиком,
        // своей текстурой.
        DirectRenderer.submit(model, (com.wf.gemrender.gltf.GltfAnimation) null, 0.0f,
                poseStack.last().pose(), tracer ? LightTexture.FULL_BRIGHT : packedLight,
                OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, DirectPass.LEVEL,
                tracer ? TurretBulletVariants.tracerVariant(model, entity) : VariantUv.NONE);

        poseStack.popPose();
    }
}
