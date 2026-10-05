package com.trd.client.gecko.entity.bullets;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.trd.entity.weapons.bullets.TurretBulletEntity;
import com.trd.main.MainRegistry;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.texture.VariantUv;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Пуля на glTF через GemRender с эффектами свечения в темноте, ореолом и трассирующим хвостом.
 * Ореол и хвост рендерятся поверх облаков через прямой аддитивный проход (AFTER_WEATHER).
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class TurretBulletGltfRenderer extends EntityRenderer<TurretBulletEntity> {

    private static final float MODEL_SCALE = 0.5F;

    private static final ResourceLocation HALO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/entity/bullet_halo.png");

    private static final ResourceLocation TAIL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/entity/tracer_tail.png");

    private static class PendingBulletFx {
        final Vec3 renderPos;
        final float speedBlocks;
        final float yawDeg;
        final float pitchDeg;
        final TurretBulletEntity.AmmoType ammoType;
        final boolean tracer;
        final float distToCam;
        final float cloudAlphaMultiplier;

        PendingBulletFx(Vec3 renderPos, float speedBlocks, float yawDeg, float pitchDeg,
                        TurretBulletEntity.AmmoType ammoType, boolean tracer,
                        float distToCam, float cloudAlphaMultiplier) {
            this.renderPos = renderPos;
            this.speedBlocks = speedBlocks;
            this.yawDeg = yawDeg;
            this.pitchDeg = pitchDeg;
            this.ammoType = ammoType;
            this.tracer = tracer;
            this.distToCam = distToCam;
            this.cloudAlphaMultiplier = cloudAlphaMultiplier;
        }
    }

    private static final List<PendingBulletFx> PENDING_FX = new ArrayList<>();

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
        if (entity.isRemoved()) {
            return false;
        }
        // Перестаём рендерить, если пуля вылетела за пределы прогруженных чанков
        if (!entity.level().hasChunkAt(entity.blockPosition())) {
            return false;
        }
        return frustum.isVisible(entity.getBoundingBox().inflate(1.5F));
    }

    @Override
    public Vec3 getRenderOffset(TurretBulletEntity entity, float partialTicks) {
        return entity.renderOffset(partialTicks);
    }

    private static int[] getAmmoColorRgb(TurretBulletEntity.AmmoType type, boolean tracer) {
        if (!tracer) {
            // Раскалённая обычная пуля: тёплый золотисто-янтарный накал
            return new int[]{255, 175, 60};
        }
        return switch (type) {
            case PIERCING -> new int[]{70, 200, 255};    // Неоново-голубой
            case HOLLOW -> new int[]{255, 60, 190};      // Насыщенный пурпурно-малиновый
            case INCENDIARY -> new int[]{255, 80, 20};   // Огненно-алый
            case RADIO -> new int[]{70, 255, 80};        // Радиоактивно-зелёный
            case HE, HE_SAVE -> new int[]{255, 160, 40}; // Взрывной янтарный
            default -> new int[]{255, 195, 60};          // Обычный трассер: золотой
        };
    }

    /**
     * Вычисляет долю эффекта (0.0..1.0), которая должна рендериться поверх облаков (в AFTER_WEATHER).
     * Если пуля долетела до облаков или скрылась за ними, возвращает 0.0 (рисуется до облаков).
     */
    public static float calculateCloudFade(ClientLevel level, Camera camera, Vec3 bulletPos) {
        if (level == null || camera == null) {
            return 1.0F;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCloudsType() == CloudStatus.OFF) {
            return 1.0F;
        }
        float cloudHeight = level.effects().getCloudHeight();
        if (Float.isNaN(cloudHeight)) {
            return 1.0F;
        }

        double camY = camera.getPosition().y;
        double bulletY = bulletPos.y;
        float fadeRange = 8.0F;

        if (camY < cloudHeight) {
            // Камера находится под облаками (земля/пещеры/воздух до Y=192)
            if (bulletY >= cloudHeight) {
                // Пуля долетела до облаков или скрылась в них — облака закрывают её
                return 0.0F;
            } else if (bulletY > cloudHeight - fadeRange) {
                // Плавное растворение при подлёте к нижней кромке облаков
                return (float) ((cloudHeight - bulletY) / fadeRange);
            } else {
                // Пуля строго под облаками (между камерой и облаками) — рендерим поверх облаков!
                return 1.0F;
            }
        } else if (camY > cloudHeight + 4.0) {
            // Камера находится над облаками (высокогорье/полёт на элитрах выше Y=196)
            if (bulletY <= cloudHeight + 4.0) {
                return 0.0F;
            } else if (bulletY < cloudHeight + 4.0 + fadeRange) {
                return (float) ((bulletY - (cloudHeight + 4.0)) / fadeRange);
            } else {
                return 1.0F;
            }
        }
        return 1.0F;
    }

    private static void renderQuad(VertexConsumer consumer, PoseStack.Pose pose,
                                   float x0, float y0, float z0, float u0, float v0,
                                   float x1, float y1, float z1, float u1, float v1,
                                   float x2, float y2, float z2, float u2, float v2,
                                   float x3, float y3, float z3, float u3, float v3,
                                   int r, int g, int b, int a) {
        // Лицевая сторона
        consumer.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setUv(u0, v0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(u2, v2)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
        consumer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a).setUv(u3, v3)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);

        // Обратная сторона (видимость с обоих ракурсов)
        consumer.addVertex(pose, x3, y3, z3).setColor(r, g, b, a).setUv(u3, v3)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, -1.0F, 0.0F);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(u2, v2)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, -1.0F, 0.0F);
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, -1.0F, 0.0F);
        consumer.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setUv(u0, v0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, -1.0F, 0.0F);
    }

    private static void renderHaloAndTracer(PoseStack poseStack, MultiBufferSource bufferSource,
                                           Camera camera, TurretBulletEntity.AmmoType ammoType,
                                           boolean tracer, float speedBlocks,
                                           float yawDeg, float pitchDeg,
                                           float distToCam, float alphaMultiplier) {
        if (alphaMultiplier <= 0.005F) {
            return;
        }

        int[] rgb = getAmmoColorRgb(ammoType, tracer);
        int colorR = rgb[0];
        int colorG = rgb[1];
        int colorB = rgb[2];

        // 1. ОРЕОЛ (HALO BILLBOARD)
        // Чтобы при выходе из ствола прямо перед носом игрока ореол не занимал весь экран,
        // масштабируем его вблизи камеры (начиная с небольшого размера у дула).
        float nearScale = Math.min(1.0F, Math.max(0.1F, distToCam * 0.45F));
        float haloRadius = (tracer ? 0.28F : 0.16F) * nearScale;
        int haloAlpha = Math.min(255, Math.max(0, (int) ((tracer ? 230 : 160) * alphaMultiplier)));

        poseStack.pushPose();
        if (camera != null) {
            poseStack.mulPose(camera.rotation());
        }

        VertexConsumer haloConsumer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(HALO_TEXTURE, false));
        renderQuad(haloConsumer, poseStack.last(),
                -haloRadius, -haloRadius, 0.0F, 0.0F, 1.0F,
                haloRadius, -haloRadius, 0.0F, 1.0F, 1.0F,
                haloRadius, haloRadius, 0.0F, 1.0F, 0.0F,
                -haloRadius, haloRadius, 0.0F, 0.0F, 0.0F,
                colorR, colorG, colorB, haloAlpha);
        poseStack.popPose();

        // 2. ХВОСТ ТРАССЕРА
        if (tracer) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg - 180.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(pitchDeg));

            float tailLength = Math.max(1.8F, Math.min(speedBlocks * 0.45F, 4.0F));
            float headHalfW = 0.034F;
            float tailHalfW = 0.005F;
            float zStart = 0.015F;
            float zEnd = zStart + tailLength;
            int tailAlpha = Math.min(255, Math.max(0, (int) (245 * alphaMultiplier)));
            int coreAlpha = Math.min(255, Math.max(0, (int) (255 * alphaMultiplier)));

            VertexConsumer tailConsumer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(TAIL_TEXTURE, false));

            // Внешний цветной хвост
            renderQuad(tailConsumer, poseStack.last(),
                    -headHalfW, 0.0F, zStart, 0.0F, 0.0F,
                    headHalfW, 0.0F, zStart, 1.0F, 0.0F,
                    tailHalfW, 0.0F, zEnd, 1.0F, 1.0F,
                    -tailHalfW, 0.0F, zEnd, 0.0F, 1.0F,
                    colorR, colorG, colorB, tailAlpha);

            renderQuad(tailConsumer, poseStack.last(),
                    0.0F, -headHalfW, zStart, 0.0F, 0.0F,
                    0.0F, headHalfW, zStart, 1.0F, 0.0F,
                    0.0F, tailHalfW, zEnd, 1.0F, 1.0F,
                    0.0F, -tailHalfW, zEnd, 0.0F, 1.0F,
                    colorR, colorG, colorB, tailAlpha);

            // Бело-жёлтый сердечник
            float coreHeadW = 0.015F;
            float coreTailW = 0.002F;
            float coreEnd = zStart + tailLength * 0.75F;

            renderQuad(tailConsumer, poseStack.last(),
                    -coreHeadW, 0.0F, zStart, 0.0F, 0.0F,
                    coreHeadW, 0.0F, zStart, 1.0F, 0.0F,
                    coreTailW, 0.0F, coreEnd, 1.0F, 1.0F,
                    -coreTailW, 0.0F, coreEnd, 0.0F, 1.0F,
                    255, 255, 240, coreAlpha);

            renderQuad(tailConsumer, poseStack.last(),
                    0.0F, -coreHeadW, zStart, 0.0F, 0.0F,
                    0.0F, coreHeadW, zStart, 1.0F, 0.0F,
                    0.0F, coreTailW, coreEnd, 1.0F, 1.0F,
                    0.0F, -coreTailW, coreEnd, 0.0F, 1.0F,
                    255, 255, 240, coreAlpha);

            poseStack.popPose();
        }
    }

    @Override
    public void render(TurretBulletEntity entity, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        if (entity.isRemoved() || !entity.level().hasChunkAt(entity.blockPosition())) {
            return;
        }

        boolean tracer = entity.isTracer();
        var model = TurretBulletVariants.modelFor(entity);
        if (model == null) {
            return;
        }

        Vec3 dir = entity.flightDirection();
        if (dir.lengthSqr() < 1.0E-8D) {
            dir = entity.getDeltaMovement();
            if (dir.lengthSqr() < 1.0E-8D) {
                return;
            }
        }

        double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yawDeg = (float) (Math.atan2(dir.x, dir.z) * (180.0D / Math.PI));
        float pitchDeg = (float) (Math.atan2(dir.y, horizontal) * (180.0D / Math.PI));
        float speedBlocks = (float) entity.getDeltaMovement().length();

        Camera camera = this.entityRenderDispatcher.camera;
        Vec3 camPos = camera != null ? camera.getPosition() : Vec3.ZERO;
        Vec3 offset = entity.renderOffset(partialTick);
        Vec3 bulletWorldPos = entity.position().add(offset);
        float distToCam = (float) bulletWorldPos.distanceTo(camPos);

        Minecraft mc = Minecraft.getInstance();
        float cloudFade = (camera != null && mc.level instanceof ClientLevel cl)
                ? calculateCloudFade(cl, camera, bulletWorldPos)
                : 1.0F;

        // Если пуля частично или полностью скрыта за облаками — рисуем эту часть
        // прямо сейчас (в обычной фазе сущностей), чтобы облака затем закрыли её
        float underCloudsAlpha = 1.0F - cloudFade;
        if (underCloudsAlpha > 0.01F) {
            renderHaloAndTracer(poseStack, bufferSource, camera, entity.getAmmoType(), tracer,
                    speedBlocks, yawDeg, pitchDeg, distToCam, underCloudsAlpha);
        }

        // Если пуля находится перед облаками — откладываем отрисовку до AFTER_WEATHER,
        // чтобы ореол и хвост легли поверх облаков
        if (cloudFade > 0.01F) {
            PENDING_FX.add(new PendingBulletFx(bulletWorldPos, speedBlocks, yawDeg, pitchDeg,
                    entity.getAmmoType(), tracer, distToCam, cloudFade));
        }

        // === 3. МОДЕЛЬ ПУЛИ ===
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg - 180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitchDeg));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.spin));

        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        // Самосвечение пули в темноте (не становится чёрной даже в тёмных пещерах)
        int bulletLight = tracer ? LightTexture.FULL_BRIGHT
                : LightTexture.pack(Math.max(LightTexture.block(packedLight), 13), Math.max(LightTexture.sky(packedLight), 13));
        int bulletTint = tracer ? 0xFFFFFFFF : 0xFFFFAA77;

        DirectRenderer.submit(model, (com.wf.gemrender.gltf.GltfAnimation) null, 0.0f,
                poseStack.last().pose(), bulletLight,
                OverlayTexture.NO_OVERLAY, bulletTint, DirectPass.LEVEL,
                tracer ? TurretBulletVariants.tracerVariant(model, entity) : VariantUv.NONE);

        poseStack.popPose();
    }

    private static void buildDirectQuad(VertexConsumer consumer, Matrix4f pose,
                                        float x0, float y0, float z0, float u0, float v0,
                                        float x1, float y1, float z1, float u1, float v1,
                                        float x2, float y2, float z2, float u2, float v2,
                                        float x3, float y3, float z3, float u3, float v3,
                                        int r, int g, int b, int a) {
        // Лицевая сторона
        consumer.addVertex(pose, x0, y0, z0).setUv(u0, v0).setColor(r, g, b, a);
        consumer.addVertex(pose, x1, y1, z1).setUv(u1, v1).setColor(r, g, b, a);
        consumer.addVertex(pose, x2, y2, z2).setUv(u2, v2).setColor(r, g, b, a);
        consumer.addVertex(pose, x3, y3, z3).setUv(u3, v3).setColor(r, g, b, a);

        // Обратная сторона (видимость с обоих ракурсов)
        consumer.addVertex(pose, x3, y3, z3).setUv(u3, v3).setColor(r, g, b, a);
        consumer.addVertex(pose, x2, y2, z2).setUv(u2, v2).setColor(r, g, b, a);
        consumer.addVertex(pose, x1, y1, z1).setUv(u1, v1).setColor(r, g, b, a);
        consumer.addVertex(pose, x0, y0, z0).setUv(u0, v0).setColor(r, g, b, a);
    }

    private static void renderPendingFxDirect(List<PendingBulletFx> fxList, PoseStack poseStack, Camera camera) {
        if (fxList.isEmpty() || camera == null || poseStack == null) {
            return;
        }

        Vec3 camPos = camera.getPosition();
        Tesselator tesselator = Tesselator.getInstance();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        // 1. Ореолы (Halo billboard) с текстурой HALO_TEXTURE
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, HALO_TEXTURE);

        BufferBuilder haloBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean hasHalos = false;

        for (PendingBulletFx fx : fxList) {
            float alphaMul = fx.cloudAlphaMultiplier;
            if (alphaMul <= 0.005F) continue;

            int[] rgb = getAmmoColorRgb(fx.ammoType, fx.tracer);
            float distToCam = fx.distToCam;
            float nearScale = Math.min(1.0F, Math.max(0.1F, distToCam * 0.45F));
            float haloRadius = (fx.tracer ? 0.28F : 0.16F) * nearScale;
            int haloAlpha = Math.min(255, Math.max(0, (int) ((fx.tracer ? 230 : 160) * alphaMul)));

            poseStack.pushPose();
            poseStack.translate(fx.renderPos.x - camPos.x, fx.renderPos.y - camPos.y, fx.renderPos.z - camPos.z);
            poseStack.mulPose(camera.rotation());

            Matrix4f m = poseStack.last().pose();
            buildDirectQuad(haloBuilder, m,
                    -haloRadius, -haloRadius, 0.0F, 0.0F, 1.0F,
                    haloRadius, -haloRadius, 0.0F, 1.0F, 1.0F,
                    haloRadius, haloRadius, 0.0F, 1.0F, 0.0F,
                    -haloRadius, haloRadius, 0.0F, 0.0F, 0.0F,
                    rgb[0], rgb[1], rgb[2], haloAlpha);

            poseStack.popPose();
            hasHalos = true;
        }

        MeshData haloMesh = haloBuilder.build();
        if (haloMesh != null && hasHalos) {
            BufferUploader.drawWithShader(haloMesh);
        }

        // 2. Хвосты трассеров с текстурой TAIL_TEXTURE
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TAIL_TEXTURE);

        BufferBuilder tailBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean hasTails = false;

        for (PendingBulletFx fx : fxList) {
            if (!fx.tracer) continue;
            float alphaMul = fx.cloudAlphaMultiplier;
            if (alphaMul <= 0.005F) continue;

            int[] rgb = getAmmoColorRgb(fx.ammoType, true);
            float speedBlocks = fx.speedBlocks;
            float tailLength = Math.max(1.8F, Math.min(speedBlocks * 0.45F, 4.0F));
            float headHalfW = 0.034F;
            float tailHalfW = 0.005F;
            float zStart = 0.015F;
            float zEnd = zStart + tailLength;
            int tailAlpha = Math.min(255, Math.max(0, (int) (245 * alphaMul)));
            int coreAlpha = Math.min(255, Math.max(0, (int) (255 * alphaMul)));

            poseStack.pushPose();
            poseStack.translate(fx.renderPos.x - camPos.x, fx.renderPos.y - camPos.y, fx.renderPos.z - camPos.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(fx.yawDeg - 180.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(fx.pitchDeg));

            Matrix4f m = poseStack.last().pose();

            // Внешний цветной хвост
            buildDirectQuad(tailBuilder, m,
                    -headHalfW, 0.0F, zStart, 0.0F, 0.0F,
                    headHalfW, 0.0F, zStart, 1.0F, 0.0F,
                    tailHalfW, 0.0F, zEnd, 1.0F, 1.0F,
                    -tailHalfW, 0.0F, zEnd, 0.0F, 1.0F,
                    rgb[0], rgb[1], rgb[2], tailAlpha);

            buildDirectQuad(tailBuilder, m,
                    0.0F, -headHalfW, zStart, 0.0F, 0.0F,
                    0.0F, headHalfW, zStart, 1.0F, 0.0F,
                    0.0F, tailHalfW, zEnd, 1.0F, 1.0F,
                    0.0F, -tailHalfW, zEnd, 0.0F, 1.0F,
                    rgb[0], rgb[1], rgb[2], tailAlpha);

            // Бело-жёлтый сердечник
            float coreHeadW = 0.015F;
            float coreTailW = 0.002F;
            float coreEnd = zStart + tailLength * 0.75F;

            buildDirectQuad(tailBuilder, m,
                    -coreHeadW, 0.0F, zStart, 0.0F, 0.0F,
                    coreHeadW, 0.0F, zStart, 1.0F, 0.0F,
                    coreTailW, 0.0F, coreEnd, 1.0F, 1.0F,
                    -coreTailW, 0.0F, coreEnd, 0.0F, 1.0F,
                    255, 255, 240, coreAlpha);

            buildDirectQuad(tailBuilder, m,
                    0.0F, -coreHeadW, zStart, 0.0F, 0.0F,
                    0.0F, coreHeadW, zStart, 1.0F, 0.0F,
                    0.0F, coreTailW, coreEnd, 1.0F, 1.0F,
                    0.0F, -coreTailW, coreEnd, 0.0F, 1.0F,
                    255, 255, 240, coreAlpha);

            poseStack.popPose();
            hasTails = true;
        }

        MeshData tailMesh = tailBuilder.build();
        if (tailMesh != null && hasTails) {
            BufferUploader.drawWithShader(tailMesh);
        }

        // Восстанавливаем GL-состояние
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            if (!PENDING_FX.isEmpty()) {
                renderPendingFxDirect(PENDING_FX, event.getPoseStack(), event.getCamera());
                PENDING_FX.clear();
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            if (!PENDING_FX.isEmpty()) {
                renderPendingFxDirect(PENDING_FX, event.getPoseStack(), event.getCamera());
                PENDING_FX.clear();
            }
        }
    }
}
