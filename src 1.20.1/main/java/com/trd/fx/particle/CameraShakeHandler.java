package com.trd.fx.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = com.trd.main.MainRegistry.MOD_ID, value = Dist.CLIENT)
public class CameraShakeHandler {

    private static float shakeIntensity = 0.0F;
    private static int shakeDuration = 0;

    private static float shakeOffsetX = 0.0F;
    private static float shakeOffsetY = 0.0F;
    private static float shakeOffsetRoll = 0.0F;

    private static float targetOffsetX = 0.0F;
    private static float targetOffsetY = 0.0F;
    private static float targetOffsetRoll = 0.0F;

    private static final float VISUAL_MULTIPLIER = 12.0F;
    private static final float GUI_MULTIPLIER = 2.5F;
    private static final float INTERPOLATION_SPEED = 0.4F;

    private static int updateCounter = 0;
    private static final int UPDATE_FREQUENCY = 2;

    public static void addShake(float intensity, int duration) {
        if (intensity > shakeIntensity) {
            shakeIntensity = intensity;
            shakeDuration = duration;
            generateNewTarget();
        } else if (shakeDuration > 0) {
            shakeDuration = Math.max(shakeDuration, duration);
            shakeIntensity = Math.min(shakeIntensity + intensity * 0.3F, 2.0F);
        } else {
            shakeIntensity = intensity;
            shakeDuration = duration;
            generateNewTarget();
        }
    }

    private static void generateNewTarget() {
        targetOffsetX = (float) (Math.random() - 0.5) * 2.0F * shakeIntensity * VISUAL_MULTIPLIER;
        targetOffsetY = (float) (Math.random() - 0.5) * 2.0F * shakeIntensity * VISUAL_MULTIPLIER;
        targetOffsetRoll = (float) (Math.random() - 0.5) * shakeIntensity * VISUAL_MULTIPLIER * 0.3F;
    }

    public static void tick() {
        if (shakeDuration > 0) {
            shakeDuration--;
            shakeIntensity *= 0.92F;

            updateCounter++;

            if (updateCounter >= UPDATE_FREQUENCY) {
                generateNewTarget();
                updateCounter = 0;
            }

            shakeOffsetX += (targetOffsetX - shakeOffsetX) * INTERPOLATION_SPEED;
            shakeOffsetY += (targetOffsetY - shakeOffsetY) * INTERPOLATION_SPEED;
            shakeOffsetRoll += (targetOffsetRoll - shakeOffsetRoll) * INTERPOLATION_SPEED;

            if (shakeIntensity < 0.01F) {
                shakeIntensity = 0.0F;
                shakeDuration = 0;
                shakeOffsetX = 0.0F;
                shakeOffsetY = 0.0F;
                shakeOffsetRoll = 0.0F;
                targetOffsetX = 0.0F;
                targetOffsetY = 0.0F;
                targetOffsetRoll = 0.0F;
                updateCounter = 0;
            }
        } else {
            shakeIntensity = 0.0F;
            shakeOffsetX *= 0.8F;
            shakeOffsetY *= 0.8F;
            shakeOffsetRoll *= 0.8F;

            if (Math.abs(shakeOffsetX) < 0.01F && Math.abs(shakeOffsetY) < 0.01F) {
                shakeOffsetX = 0.0F;
                shakeOffsetY = 0.0F;
                shakeOffsetRoll = 0.0F;
            }
        }
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (shakeIntensity > 0.0F || Math.abs(shakeOffsetX) > 0.01F) {
            event.setYaw(event.getYaw() + shakeOffsetX);
            event.setPitch(event.getPitch() + shakeOffsetY);
            event.setRoll(event.getRoll() + shakeOffsetRoll);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (shakeIntensity > 0.0F || Math.abs(shakeOffsetX) > 0.01F) {
            PoseStack poseStack = event.getGuiGraphics().pose();

            poseStack.pushPose();

            float guiOffsetX = shakeOffsetX * GUI_MULTIPLIER;
            float guiOffsetY = shakeOffsetY * GUI_MULTIPLIER;

            int screenWidth = event.getGuiGraphics().guiWidth();
            int screenHeight = event.getGuiGraphics().guiHeight();
            float centerX = screenWidth / 2.0F;
            float centerY = screenHeight / 2.0F;

            poseStack.translate(centerX, centerY, 0);

            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(shakeOffsetRoll * 0.2F));

            poseStack.translate(guiOffsetX, guiOffsetY, 0);

            poseStack.translate(-centerX, -centerY, 0);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (shakeIntensity > 0.0F || Math.abs(shakeOffsetX) > 0.01F) {
            PoseStack poseStack = event.getGuiGraphics().pose();
            poseStack.popPose();
        }
    }

    @SubscribeEvent
    public static void onRenderTick(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.START) {
            if (Minecraft.getInstance().level != null) {
                tick();
            }
        }
    }
}
