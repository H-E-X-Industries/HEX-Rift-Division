package com.trd.climate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.trd.item.tools.ScrewdriverItem;
import com.trd.main.MainRegistry;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиентский рендер голографической подсветки отсканированного объема (Tier 2).
 * Подсвечивает проверенный объем светло-зеленым полупрозрачным цветом.
 * Для предотвращения эффекта "зеленой жижи" отрисовывается исключительно первый слой
 * внешних граней объема, обращенных к взгляду игрока (single-layer dynamic culling).
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class ScannerVisualRenderHandler {

    public record ExposedFace(int x, int y, int z, Direction direction) {}

    public static class ScanVisual {
        public final BlockPos controllerPos;
        public final LongSet interiorSet;
        public final List<ExposedFace> exposedFaces;
        public final boolean isSuccess;
        public final int volume;
        public final int clusterSize;
        public long startTime;
        public final long durationMs = 2000L; // 2 секунды показа

        public ScanVisual(BlockPos controllerPos, long[] interiorPositions, boolean isSuccess, int volume, int clusterSize) {
            this.controllerPos = controllerPos;
            this.isSuccess = isSuccess;
            this.volume = volume;
            this.clusterSize = clusterSize;
            this.startTime = System.currentTimeMillis();

            this.interiorSet = new LongOpenHashSet(interiorPositions);
            this.exposedFaces = new ArrayList<>();

            // Предварительный расчет только внешних граней (границы со стенами или непроверенными блоками).
            // Внутренние грани между блоками воздуха не генерируются вовсе!
            BlockPos.MutableBlockPos nPos = new BlockPos.MutableBlockPos();
            for (long pLong : interiorPositions) {
                int x = BlockPos.getX(pLong);
                int y = BlockPos.getY(pLong);
                int z = BlockPos.getZ(pLong);

                for (Direction dir : Direction.values()) {
                    nPos.set(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
                    if (!this.interiorSet.contains(nPos.asLong())) {
                        this.exposedFaces.add(new ExposedFace(x, y, z, dir));
                    }
                }
            }
        }
    }

    private static final Map<BlockPos, ScanVisual> ACTIVE_VISUALS = new ConcurrentHashMap<>();

    /**
     * Вызывается при получении сетевого пакета от сервера.
     */
    public static void onScanResult(BlockPos controllerPos, long[] interiorPositions, boolean isSuccess, int volume, int clusterSize) {
        if (interiorPositions == null || interiorPositions.length == 0) {
            ACTIVE_VISUALS.remove(controllerPos);
            return;
        }
        ACTIVE_VISUALS.put(controllerPos, new ScanVisual(controllerPos, interiorPositions, isSuccess, volume, clusterSize));
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || ACTIVE_VISUALS.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        Vec3 camPos = event.getCamera().getPosition();
        BlockPos camBlockPos = BlockPos.containing(camPos);

        Iterator<Map.Entry<BlockPos, ScanVisual>> it = ACTIVE_VISUALS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, ScanVisual> entry = it.next();
            ScanVisual visual = entry.getValue();

            long elapsed = now - visual.startTime;
            if (elapsed >= visual.durationMs) {
                it.remove();
                continue;
            }

            // Плавное затухание в последние 500 миллисекунд
            float fade = 1.0f;
            long fadeDuration = 500L;
            if (elapsed > visual.durationMs - fadeDuration) {
                fade = Math.max(0.0f, (visual.durationMs - elapsed) / (float) fadeDuration);
            }

            renderVisual(event.getPoseStack(), camPos, camBlockPos, visual, fade);
        }
    }

    private static void renderVisual(PoseStack poseStack, Vec3 camPos, BlockPos camBlockPos, ScanVisual visual, float fade) {
        if (visual.exposedFaces.isEmpty()) {
            return;
        }

        // Находится ли камера игрока внутри отсканированного объема воздуха
        boolean camInside = visual.interiorSet.contains(camBlockPos.asLong());

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f pose = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();

        // Цветовая гамма: светло-светло зеленый цвет для любого сканирования (успешного и неудачного)
        float r = 0.35f;
        float g = 1.0f;
        float b = 0.50f;
        float fillAlpha = 0.16f * fade;
        float lineAlpha = 0.60f * fade;

        // 1. Отрисовка полупрозрачных граней первого слоя
        BufferBuilder quadBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (ExposedFace face : visual.exposedFaces) {
            int x = face.x();
            int y = face.y();
            int z = face.z();
            Direction dir = face.direction();

            // Вектор от центра грани к камере игрока
            double cx = x + 0.5 + dir.getStepX() * 0.5;
            double cy = y + 0.5 + dir.getStepY() * 0.5;
            double cz = z + 0.5 + dir.getStepZ() * 0.5;

            double toCamX = camPos.x - cx;
            double toCamY = camPos.y - cy;
            double toCamZ = camPos.z - cz;

            double dot = toCamX * dir.getStepX() + toCamY * dir.getStepY() + toCamZ * dir.getStepZ();

            // ДИНАМИЧЕСКОЕ ОТСЕЧЕНИЕ:
            // Если игрок снаружи — рендерим только грани, смотрящие на него (dot > 0).
            // Если игрок внутри комнаты — рендерим грани стен, смотрящие внутрь комнаты (dot < 0).
            // Таким образом луч взгляда пересекает ВСЕГДА ТОЛЬКО ОДНУ грань! Никакой "зеленой жижи"!
            if (!camInside && dot <= 0.0) {
                continue;
            }
            if (camInside && dot >= 0.0) {
                continue;
            }

            addFaceVertices(quadBuilder, pose, x, y, z, dir, r, g, b, fillAlpha);
        }

        BufferUploader.drawWithShader(quadBuilder.buildOrThrow());

        // 2. Отрисовка сетки контуров (wireframe grid) для четкой визуализации блоков
        BufferBuilder lineBuilder = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (ExposedFace face : visual.exposedFaces) {
            int x = face.x();
            int y = face.y();
            int z = face.z();
            Direction dir = face.direction();

            double cx = x + 0.5 + dir.getStepX() * 0.5;
            double cy = y + 0.5 + dir.getStepY() * 0.5;
            double cz = z + 0.5 + dir.getStepZ() * 0.5;

            double toCamX = camPos.x - cx;
            double toCamY = camPos.y - cy;
            double toCamZ = camPos.z - cz;

            double dot = toCamX * dir.getStepX() + toCamY * dir.getStepY() + toCamZ * dir.getStepZ();

            if (!camInside && dot <= 0.0) {
                continue;
            }
            if (camInside && dot >= 0.0) {
                continue;
            }

            addFaceLines(lineBuilder, pose, x, y, z, dir, r, g, b, lineAlpha);
        }

        BufferUploader.drawWithShader(lineBuilder.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        poseStack.popPose();
    }

    private static void addFaceVertices(BufferBuilder builder, Matrix4f pose, int x, int y, int z, Direction dir, float r, float g, float b, float a) {
        float minX = x;
        float maxX = x + 1.0f;
        float minY = y;
        float maxY = y + 1.0f;
        float minZ = z;
        float maxZ = z + 1.0f;

        switch (dir) {
            case UP -> {
                builder.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a);
                builder.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a);
                builder.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a);
                builder.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a);
            }
            case DOWN -> {
                builder.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a);
                builder.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a);
                builder.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a);
                builder.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a);
            }
            case NORTH -> {
                builder.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a);
                builder.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a);
                builder.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a);
                builder.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a);
            }
            case SOUTH -> {
                builder.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a);
                builder.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a);
                builder.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a);
                builder.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a);
            }
            case WEST -> {
                builder.addVertex(pose, minX, minY, minZ).setColor(r, g, b, a);
                builder.addVertex(pose, minX, minY, maxZ).setColor(r, g, b, a);
                builder.addVertex(pose, minX, maxY, maxZ).setColor(r, g, b, a);
                builder.addVertex(pose, minX, maxY, minZ).setColor(r, g, b, a);
            }
            case EAST -> {
                builder.addVertex(pose, maxX, minY, minZ).setColor(r, g, b, a);
                builder.addVertex(pose, maxX, maxY, minZ).setColor(r, g, b, a);
                builder.addVertex(pose, maxX, maxY, maxZ).setColor(r, g, b, a);
                builder.addVertex(pose, maxX, minY, maxZ).setColor(r, g, b, a);
            }
        }
    }

    private static void addFaceLines(BufferBuilder builder, Matrix4f pose, int x, int y, int z, Direction dir, float r, float g, float b, float a) {
        float minX = x;
        float maxX = x + 1.0f;
        float minY = y;
        float maxY = y + 1.0f;
        float minZ = z;
        float maxZ = z + 1.0f;

        switch (dir) {
            case UP -> {
                addLine(builder, pose, minX, maxY, minZ, minX, maxY, maxZ, r, g, b, a);
                addLine(builder, pose, minX, maxY, maxZ, maxX, maxY, maxZ, r, g, b, a);
                addLine(builder, pose, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
                addLine(builder, pose, maxX, maxY, minZ, minX, maxY, minZ, r, g, b, a);
            }
            case DOWN -> {
                addLine(builder, pose, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
                addLine(builder, pose, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
                addLine(builder, pose, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
                addLine(builder, pose, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
            }
            case NORTH -> {
                addLine(builder, pose, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
                addLine(builder, pose, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
                addLine(builder, pose, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
                addLine(builder, pose, maxX, minY, minZ, minX, minY, minZ, r, g, b, a);
            }
            case SOUTH -> {
                addLine(builder, pose, minX, minY, maxZ, maxX, minY, maxZ, r, g, b, a);
                addLine(builder, pose, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
                addLine(builder, pose, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
                addLine(builder, pose, minX, maxY, maxZ, minX, minY, maxZ, r, g, b, a);
            }
            case WEST -> {
                addLine(builder, pose, minX, minY, minZ, minX, minY, maxZ, r, g, b, a);
                addLine(builder, pose, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
                addLine(builder, pose, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
                addLine(builder, pose, minX, maxY, minZ, minX, minY, minZ, r, g, b, a);
            }
            case EAST -> {
                addLine(builder, pose, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
                addLine(builder, pose, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
                addLine(builder, pose, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
                addLine(builder, pose, maxX, minY, maxZ, maxX, minY, minZ, r, g, b, a);
            }
        }
    }

    private static void addLine(BufferBuilder builder, Matrix4f pose, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        builder.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        builder.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
    }
}
