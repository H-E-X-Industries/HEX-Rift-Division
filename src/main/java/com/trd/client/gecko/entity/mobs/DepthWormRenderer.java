package com.trd.client.gecko.entity.mobs;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.entity.mobs.depth_worm.DepthWormEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class DepthWormRenderer extends GeoEntityRenderer<DepthWormEntity> {
    public DepthWormRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DepthWormModel());
        this.shadowRadius = 0.3f;
    }

    @Override
    protected void applyRotations(DepthWormEntity animatable, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);

        if (animatable.isAttacking() && animatable.getTarget() != null) {
            LivingEntity target = animatable.getTarget();
            // Вычисляем угол наклона к цели
            double dy = target.getEyeY() - animatable.getEyeY();
            double dx = target.getX() - animatable.getX();
            double dz = target.getZ() - animatable.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            // Переводим в градусы и ограничиваем наклон
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));
            pitch = Mth.clamp(pitch, -45.0F, 45.0F);

            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        }
    }
}
