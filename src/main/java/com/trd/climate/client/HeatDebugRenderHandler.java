package com.trd.climate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.trd.client.config.ModClientConfig;
import com.trd.client.config.ModDebugMode;
import com.trd.main.MainRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Отладка температурных полей (Heat Debug Overlay).
 * <p>
 * Включается общим режимом отладки мода ({@link com.trd.client.config.ModDebugMode}),
 * который переключается переназначаемой клавишей
 * {@link com.trd.client.config.ModKeyBindings#DEBUG_RENDER_KEY}. Отдельной клавиши
 * у климата нет намеренно — иначе она разъезжается с общим режимом отладки, когда
 * игрок переназначает клавишу в настройках.
 * <p>
 * Оверлей показывает <b>абсолютную температуру в точке</b>, посчитанную тем же кодом,
 * что и для термометра/игрока ({@link com.trd.climate.calculation.TemperatureCalculator#calculatePointTemp}),
 * поэтому его показания не могут разойтись с приборами.
 * <p>
 * <b>Поток рендера ничего не знает о мире.</b> Все расчёты идут в клиентском тике
 * ({@link HeatDebugController}); здесь только отрисовка готовых данных. Это и
 * быстрее, и безопаснее: обращения к блок-сущностям из потока рендера гоняются
 * с тиковым потоком и роняли игру при установке мультиблоков.
 * <p>
 * Закрашиваются <b>все открытые грани</b> блока (не только верхняя), а цифра с
 * температурой остаётся над блоком. Радиус и лимит пятен задаются в клиентских
 * настройках ({@link ModClientConfig}).
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class HeatDebugRenderHandler {

    /** Радиус, в пределах которого рисуется текст с температурой. */
    private static final double TEXT_MAX_DISTANCE_SQ = 12.0 * 12.0;
    /** Флаг, чтобы не спамить в лог одной и той же ошибкой. */
    private static boolean errorLogged = false;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!ModDebugMode.enabled) {
            return;
        }
        try {
            render(event);
        } catch (Throwable t) {
            // Оверлей не должен ронять игру
            if (!errorLogged) {
                errorLogged = true;
                com.trd.main.MainRegistry.LOGGER.error("[TRD] Ошибка отрисовки оверлея тепла:", t);
            }
        }
    }

    private static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        Map<BlockPos, Float> field = HeatDebugController.field();
        Map<BlockPos, Integer> faceMasks = HeatDebugController.faceMasks();
        if (field.isEmpty() || faceMasks.isEmpty()) {
            return;
        }

        ClientLevel level = mc.level;
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        double cullRadius = ModClientConfig.heatDebugRadius();
        double cullRadiusSq = cullRadius * cullRadius;

        PoseStack poseStack = event.getPoseStack();
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
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (Map.Entry<BlockPos, Float> entry : field.entrySet()) {
            BlockPos bpos = entry.getKey();
            Integer maskValue = faceMasks.get(bpos);
            if (maskValue == null) {
                continue;
            }
            int mask = maskValue;

            // Отсекаем пятна за пределами радиуса отображения
            double ddx = bpos.getX() - mc.player.getX();
            double ddy = bpos.getY() - mc.player.getY();
            double ddz = bpos.getZ() - mc.player.getZ();
            if (ddx * ddx + ddy * ddy + ddz * ddz > cullRadiusSq) {
                continue;
            }

            float temp = entry.getValue();
            float t = temperatureGradient(temp);
            float r, g, b;
            if (t < 0.5f) {
                r = 2.0f * t;
                g = 1.0f;
                b = 0.0f;
            } else {
                r = 1.0f;
                g = 2.0f * (1.0f - t);
                b = 0.0f;
            }
            float a = 0.55f;

            float x = bpos.getX();
            float y = bpos.getY();
            float z = bpos.getZ();
            float e = 0.002f; // небольшой отступ, чтобы грань не слипалась с блоком

            // Верхняя грань
            if ((mask & 1) != 0) {
                addQuad(builder, pose,
                        x, y + 1 + e, z,
                        x, y + 1 + e, z + 1,
                        x + 1, y + 1 + e, z + 1,
                        x + 1, y + 1 + e, z,
                        r, g, b, a);
            }
            // Нижняя
            if ((mask & 2) != 0) {
                addQuad(builder, pose,
                        x, y - e, z,
                        x + 1, y - e, z,
                        x + 1, y - e, z + 1,
                        x, y - e, z + 1,
                        r, g, b, a);
            }
            // +X
            if ((mask & 4) != 0) {
                addQuad(builder, pose,
                        x + 1 + e, y, z,
                        x + 1 + e, y, z + 1,
                        x + 1 + e, y + 1, z + 1,
                        x + 1 + e, y + 1, z,
                        r, g, b, a);
            }
            // −X
            if ((mask & 8) != 0) {
                addQuad(builder, pose,
                        x - e, y, z,
                        x - e, y, z + 1,
                        x - e, y + 1, z + 1,
                        x - e, y + 1, z,
                        r, g, b, a);
            }
            // +Z
            if ((mask & 16) != 0) {
                addQuad(builder, pose,
                        x, y, z + 1 + e,
                        x + 1, y, z + 1 + e,
                        x + 1, y + 1, z + 1 + e,
                        x, y + 1, z + 1 + e,
                        r, g, b, a);
            }
            // −Z
            if ((mask & 32) != 0) {
                addQuad(builder, pose,
                        x, y, z - e,
                        x + 1, y, z - e,
                        x + 1, y + 1, z - e,
                        x, y + 1, z - e,
                        r, g, b, a);
            }
        }

        BufferUploader.drawWithShader(builder.buildOrThrow());

        // Рамка вокруг всего мультиблока, на который смотрит игрок
        renderMultiblockOutline(mc, pose);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        poseStack.popPose();

        // Цифры с температурой — над блоком
        try (com.mojang.blaze3d.vertex.ByteBufferBuilder byteBufferBuilder = new com.mojang.blaze3d.vertex.ByteBufferBuilder(16384)) {
            MultiBufferSource.BufferSource textBufferSource = MultiBufferSource.immediate(byteBufferBuilder);

            for (Map.Entry<BlockPos, Float> entry : field.entrySet()) {
                BlockPos bpos = entry.getKey();
                float temp = entry.getValue();

                double distSq = camPos.distanceToSqr(bpos.getX() + 0.5, bpos.getY() + 1.0, bpos.getZ() + 0.5);
                if (distSq > TEXT_MAX_DISTANCE_SQ) {
                    continue; // Не захламляем экран вдали
                }

                String text = String.format(Locale.ROOT, "%.1f°C", temp);
                float t = temperatureGradient(temp);
                int red = (int) ((t < 0.5f ? 2.0f * t : 1.0f) * 255);
                int green = (int) ((t < 0.5f ? 1.0f : 2.0f * (1.0f - t)) * 255);
                int textColor = 0xFF000000 | (red << 16) | (green << 8);

                net.minecraft.client.renderer.debug.DebugRenderer.renderFloatingText(
                        poseStack,
                        textBufferSource,
                        text,
                        bpos.getX() + 0.5,
                        bpos.getY() + 1.25,
                        bpos.getZ() + 0.5,
                        textColor,
                        0.025f,
                        true,
                        0.0f,
                        true
                );
            }

            textBufferSource.endBatch();
        }
    }

    /**
     * Подсвечивает мультиблок целиком — рамкой вокруг всей конструкции,
     * когда игрок смотрит на любой её блок.
     * <p>
     * Используется готовый снимок источников из {@link HeatDebugController}:
     * поток рендера не обращается к миру вообще.
     * <p>
     * Вершины линий обязательно добавляются <b>с матрицей</b> ({@code addVertex(pose, ...)}):
     * иначе они остались бы в мировых координатах и оказались бы за пределами
     * экрана (в кадре матрица модели-вид уже содержит поворот камеры).
     */
    private static void renderMultiblockOutline(Minecraft mc, Matrix4f pose) {
        if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)) {
            return;
        }
        BlockPos looked = hit.getBlockPos();

        List<com.trd.climate.calculation.HeatSourceHelper.HeatSource> sources = HeatDebugController.sources();
        for (com.trd.climate.calculation.HeatSourceHelper.HeatSource source : sources) {
            if (source.cells().size() <= 1 || !source.cells().contains(looked)) {
                continue;
            }
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos c : source.cells()) {
                minX = Math.min(minX, c.getX());
                minY = Math.min(minY, c.getY());
                minZ = Math.min(minZ, c.getZ());
                maxX = Math.max(maxX, c.getX());
                maxY = Math.max(maxY, c.getY());
                maxZ = Math.max(maxZ, c.getZ());
            }

            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
            net.minecraft.client.renderer.RenderType renderType = net.minecraft.client.renderer.RenderType.lines();
            com.mojang.blaze3d.vertex.VertexConsumer consumer = bufferSource.getBuffer(renderType);

            float r = 1.0f, g = 0.85f, b = 0.2f, a = 0.9f;
            double x0 = minX - 0.05, y0 = minY - 0.05, z0 = minZ - 0.05;
            double x1 = maxX + 1.05, y1 = maxY + 1.05, z1 = maxZ + 1.05;
            renderBoxEdges(consumer, pose, x0, y0, z0, x1, y1, z1, r, g, b, a);

            bufferSource.endBatch(renderType);
            return;
        }
    }

    /**
     * Рисует рёбра прямоугольного параллелепипеда.
     */
    private static void renderBoxEdges(com.mojang.blaze3d.vertex.VertexConsumer c, Matrix4f pose,
                                       double x0, double y0, double z0,
                                       double x1, double y1, double z1,
                                       float r, float g, float b, float a) {
        line(c, pose, x0, y0, z0, x1, y0, z0, r, g, b, a);
        line(c, pose, x1, y0, z0, x1, y0, z1, r, g, b, a);
        line(c, pose, x1, y0, z1, x0, y0, z1, r, g, b, a);
        line(c, pose, x0, y0, z1, x0, y0, z0, r, g, b, a);
        line(c, pose, x0, y1, z0, x1, y1, z0, r, g, b, a);
        line(c, pose, x1, y1, z0, x1, y1, z1, r, g, b, a);
        line(c, pose, x1, y1, z1, x0, y1, z1, r, g, b, a);
        line(c, pose, x0, y1, z1, x0, y1, z0, r, g, b, a);
        line(c, pose, x0, y0, z0, x0, y1, z0, r, g, b, a);
        line(c, pose, x1, y0, z0, x1, y1, z0, r, g, b, a);
        line(c, pose, x1, y0, z1, x1, y1, z1, r, g, b, a);
        line(c, pose, x0, y0, z1, x0, y1, z1, r, g, b, a);
    }

    private static void line(com.mojang.blaze3d.vertex.VertexConsumer c, Matrix4f pose,
                             double x0, double y0, double z0,
                             double x1, double y1, double z1,
                             float r, float g, float b, float a) {
        c.addVertex(pose, (float) x0, (float) y0, (float) z0).setColor(r, g, b, a);
        c.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
    }

    /**
     * Добавляет квад в вершинный буфер по четырём углам (по часовой стрелке).
     * <p>
     * Углы задаются явно: если пытаться вывести грань из двух диагональных
     * углов блока, то у граней, параллельных оси Z, квад вырождается в линию
     * (и такая грань просто не рисуется).
     */
    private static void addQuad(BufferBuilder builder, Matrix4f pose,
                                float ax, float ay, float az,
                                float bx, float by, float bz,
                                float cx, float cy, float cz,
                                float dx, float dy, float dz,
                                float r, float g, float b, float a) {
        builder.addVertex(pose, ax, ay, az).setColor(r, g, b, a);
        builder.addVertex(pose, bx, by, bz).setColor(r, g, b, a);
        builder.addVertex(pose, cx, cy, cz).setColor(r, g, b, a);
        builder.addVertex(pose, dx, dy, dz).setColor(r, g, b, a);
    }

    /**
     * Цветовая шкала по абсолютной температуре: 0°C -> зелёный, 20°C -> жёлтый, 40°C+ -> красный.
     */
    private static float temperatureGradient(float temp) {
        return Math.min(1.0f, Math.max(0.0f, temp / 40.0f));
    }
}
