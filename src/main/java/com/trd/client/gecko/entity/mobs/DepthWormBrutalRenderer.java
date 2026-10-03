package com.trd.client.gecko.entity.mobs;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.entity.mobs.depth_worm.DepthWormBrutalEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class DepthWormBrutalRenderer extends GeoEntityRenderer<DepthWormBrutalEntity> {
    public DepthWormBrutalRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DepthWormBrutalModel());
        this.shadowRadius = 0.45f; // Чуть крупнее тень
    }

    @Override
    protected void applyRotations(DepthWormBrutalEntity animatable, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);

        // Наклон при прицеливании/прыжке/насаживании
        if ((animatable.isPreparingJump() || animatable.isFlying() || animatable.isImpaling())
                && animatable.getTarget() != null) {
            LivingEntity target = animatable.getTarget();

            double dy = target.getEyeY() - animatable.getEyeY();
            double dx = target.getX() - animatable.getX();
            double dz = target.getZ() - animatable.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            // Более агрессивный наклон чем у обычного червя
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));
            pitch = Mth.clamp(pitch, -60.0F, 60.0F);

            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        }
    }
}
