package com.trd.climate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.trd.item.tools.ScrewdriverItem;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Клиентский рендер динамической зелёной рамки будущего изолированного помещения.
 * Активируется, когда игрок держит отвертку с выбранным контроллером изоляции
 * и наводит прицел на другие блоки мира.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class ScrewdriverBoxRenderHandler {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        // Проверяем, держит ли игрок отвертку в режиме разметки
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        ItemStack screwdriver = null;

        if (mainHand.getItem() instanceof ScrewdriverItem && ScrewdriverItem.getLinkedPos(mainHand) != null) {
            screwdriver = mainHand;
        } else if (offHand.getItem() instanceof ScrewdriverItem && ScrewdriverItem.getLinkedPos(offHand) != null) {
            screwdriver = offHand;
        }

        if (screwdriver == null) {
            return;
        }

        BlockPos ctrlPos = ScrewdriverItem.getLinkedPos(screwdriver);
        if (ctrlPos == null) {
            return;
        }

        // Получаем блок, на который сейчас смотрит игрок
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr) || bhr.getType() == HitResult.Type.MISS) {
            return;
        }

        BlockPos targetPos = ScrewdriverItem.resolveInnerCorner(bhr.getBlockPos(), bhr.getDirection(), ctrlPos);

        int minX = Math.min(ctrlPos.getX(), targetPos.getX());
        int minY = Math.min(ctrlPos.getY(), targetPos.getY());
        int minZ = Math.min(ctrlPos.getZ(), targetPos.getZ());
        int maxX = Math.max(ctrlPos.getX(), targetPos.getX()) + 1;
        int maxY = Math.max(ctrlPos.getY(), targetPos.getY()) + 1;
        int maxZ = Math.max(ctrlPos.getZ(), targetPos.getZ()) + 1;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f pose = poseStack.last().pose();

        // 1. Отрисовка каркаса (зелёные линии)
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lineConsumer, minX, minY, minZ, maxX, maxY, maxZ,
                0.1f, 1.0f, 0.2f, 1.0f);
        bufferSource.endBatch(RenderType.lines());

        // 2. Отрисовка полупрозрачных граней куба (голографическая подсветка объёма)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float r = 0.1f;
        float g = 1.0f;
        float b = 0.2f;
        float a = 0.14f;

        // Нижняя грань (Y = minY)
        builder.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a);
        builder.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a);
        builder.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a);
        builder.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a);

        // Верхняя грань (Y = maxY)
        builder.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a);
        builder.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a);
        builder.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a);
        builder.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a);

        // Северная грань (Z = minZ)
        builder.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a);
        builder.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a);
        builder.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a);
        builder.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a);

        // Южная грань (Z = maxZ)
        builder.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a);
        builder.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a);
        builder.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a);
        builder.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a);

        // Западная грань (X = minX)
        builder.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a);
        builder.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a);
        builder.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a);
        builder.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a);

        // Восточная грань (X = maxX)
        builder.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a);
        builder.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a);
        builder.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a);
        builder.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a);

        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        poseStack.popPose();
    }
}
