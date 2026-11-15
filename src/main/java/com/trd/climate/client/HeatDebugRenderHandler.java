package com.trd.climate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.trd.climate.calculation.HeatSourceHelper;
import com.trd.client.config.ModClientConfig;
import com.trd.client.config.ModDebugMode;
import com.trd.main.MainRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
 * Включается общим режимом отладки мода ({@link ModDebugMode}),
 * который переключается переназначаемой клавишей
 * {@link com.trd.client.config.ModKeyBindings#DEBUG_RENDER_KEY}.
 * <p>
 * Оверлей показывает <b>абсолютную температуру в точке</b>, посчитанную тем же кодом,
 * что и для термометра/игрока ({@link com.trd.climate.calculation.TemperatureCalculator#calculatePointTemp}),
 * поэтому его показания не могут разойтись с приборами.
 * <p>
 * <b>Поток рендера ничего не знает о мире.</b> Все расчёты идут в клиентском тике
 * ({@link HeatDebugController}); здесь только отрисовка готовых данных.
 * <p>
 * Рендер:
 * <ul>
 *   <li>закрашиваются открытые грани блоков вокруг источников (цвет — плавный
 *       градиент зелёный → жёлтый → красный, без «вырвиглазных» порогов);</li>
 *   <li>блоки вне пирамиды видимости камеры отсекаются через {@link Frustum}
 *       (за спиной и за стеной по краю экрана ничего не рисуется);</li>
 *   <li>мультиблоки-источники обводятся рамкой по периметру — как ванильное
 *       выделение блока, только цветом температуры конструкции. Сами блоки
 *       мультиблока НЕ заливаются (иначе квады з-файтятся с его моделью);</li>
 *   <li>цифры с температурой рисуются только рядом с игроком.</li>
 * </ul>
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class HeatDebugRenderHandler {

    /** Радиус, в пределах которого рисуется текст с температурой. */
    private static final double TEXT_MAX_DISTANCE_SQ = 12.0 * 12.0;
    /** Прозрачность заливки граней. */
    private static final float FACE_ALPHA = 0.5f;
    /** Отступ заливки от грани блока, чтобы не слипалась с моделью. */
    private static final float FACE_EPSILON = 0.003f;
    /** Отступ рамки мультиблока от границ структуры. */
    private static final float OUTLINE_EPSILON = 0.01f;
    /**
     * Сколько блоков заливки уходит в один буфер. Общий {@code Tesselator}
     * рассчитан примерно на 1500 квадов; поле у лавового озера легко даёт
     * в несколько раз больше, буфер переполняется, и одно исключение навсегда
     * глушило весь оверлей.
     */
    private static final int FACE_BATCH = 1200;
    /** Флаг, чтобы не спамить в лог одной и той же ошибкой. */
    private static boolean errorLogged = false;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!ModDebugMode.enabled) {
            errorLogged = false;
            return;
        }
        try {
            render(event);
        } catch (Throwable t) {
            // Оверлей не должен ронять игру
            if (!errorLogged) {
                errorLogged = true;
                MainRegistry.LOGGER.error("[TRD] Ошибка отрисовки оверлея тепла:", t);
            }
        }
    }

    private static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        HeatDebugController.Snapshot snap = HeatDebugController.snapshot();
        Map<BlockPos, Float> field = snap.field;
        Map<BlockPos, Integer> faceMasks = snap.faceMasks;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        Frustum frustum = event.getFrustum();
        double cullRadiusSq = (double) ModClientConfig.heatDebugRadius() * ModClientConfig.heatDebugRadius();
        double playerX = mc.player.getX();
        double playerY = mc.player.getY();
        double playerZ = mc.player.getZ();

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

        // ── 1. Заливка открытых граней ──────────────────────────────
        // Свой буфер и порции: общий Tesselator рассчитан на ~1500 квадов,
        // а поле у лавового озера легко даёт несколько тысяч блоков. Раньше
        // переполнение бросало исключение, которое глушило весь оверлей
        // до перезапуска клиента.
        if (!field.isEmpty() && !faceMasks.isEmpty()) {
            ByteBufferBuilder faceBytes = new ByteBufferBuilder(FACE_BATCH * 6 * 16);
            BufferBuilder builder = new BufferBuilder(faceBytes, VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_COLOR);
            int batchCount = 0;

            for (Map.Entry<BlockPos, Integer> entry : faceMasks.entrySet()) {
                BlockPos bpos = entry.getKey();
                Float tempValue = field.get(bpos);
                if (tempValue == null) {
                    continue;
                }
                int mask = entry.getValue();
                if (mask == 0) {
                    continue;
                }

                double ddx = bpos.getX() + 0.5 - playerX;
                double ddy = bpos.getY() + 0.5 - playerY;
                double ddz = bpos.getZ() + 0.5 - playerZ;
                if (ddx * ddx + ddy * ddy + ddz * ddz > cullRadiusSq) {
                    continue;
                }
                if (frustum != null && !frustum.isVisible(new AABB(bpos))) {
                    continue;
                }

                float[] rgb = temperatureColor(tempValue);
                float r = rgb[0], g = rgb[1], b = rgb[2];
                float a = FACE_ALPHA;

                float x = bpos.getX();
                float y = bpos.getY();
                float z = bpos.getZ();
                float e = FACE_EPSILON;

                if ((mask & 1) != 0) {
                    addQuad(builder, pose, x, y + 1 + e, z, x, y + 1 + e, z + 1,
                            x + 1, y + 1 + e, z + 1, x + 1, y + 1 + e, z, r, g, b, a);
                }
                if ((mask & 2) != 0) {
                    addQuad(builder, pose, x, y - e, z, x + 1, y - e, z,
                            x + 1, y - e, z + 1, x, y - e, z + 1, r, g, b, a);
                }
                if ((mask & 4) != 0) {
                    addQuad(builder, pose, x + 1 + e, y, z, x + 1 + e, y, z + 1,
                            x + 1 + e, y + 1, z + 1, x + 1 + e, y + 1, z, r, g, b, a);
                }
                if ((mask & 8) != 0) {
                    addQuad(builder, pose, x - e, y, z, x - e, y, z + 1,
                            x - e, y + 1, z + 1, x - e, y + 1, z, r, g, b, a);
                }
                if ((mask & 16) != 0) {
                    addQuad(builder, pose, x, y, z + 1 + e, x + 1, y, z + 1 + e,
                            x + 1, y + 1, z + 1 + e, x, y + 1, z + 1 + e, r, g, b, a);
                }
                if ((mask & 32) != 0) {
                    addQuad(builder, pose, x, y, z - e, x + 1, y, z - e,
                            x + 1, y + 1, z - e, x, y + 1, z - e, r, g, b, a);
                }

                if (++batchCount >= FACE_BATCH) {
                    flushFaces(builder);
                    builder = new BufferBuilder(faceBytes, VertexFormat.Mode.QUADS,
                            DefaultVertexFormat.POSITION_COLOR);
                    batchCount = 0;
                }
            }

            if (batchCount > 0) {
                flushFaces(builder);
            } else {
                builder.build();
            }
            faceBytes.close();
        }

        // ── 2. Рамки мультиблоков и маркеры воздушных источников ────
        // Через тот же путь, что и рамка отвёртки: RenderType.lines()
        // несёт нормали и рисуется шейдером rendertype_lines. Старый путь
        // (Mode.LINES + шейдер position_color) отбрасывал линии молча.
        renderLines(poseStack, snap);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        poseStack.popPose();

        // ── 3. Цифры с температурой — над ближними блоками ─────────
        // Рисуется ПОСЛЕ popPose: renderFloatingText сам переводит мировые
        // координаты относительно камеры, поэтому сдвиг -camPos ему не нужен
        if (!field.isEmpty()) {
            try (com.mojang.blaze3d.vertex.ByteBufferBuilder byteBufferBuilder = new com.mojang.blaze3d.vertex.ByteBufferBuilder(16384)) {
                MultiBufferSource.BufferSource textBufferSource = MultiBufferSource.immediate(byteBufferBuilder);

                for (Map.Entry<BlockPos, Float> entry : field.entrySet()) {
                    BlockPos bpos = entry.getKey();
                    float temp = entry.getValue();

                    double distSq = camPos.distanceToSqr(bpos.getX() + 0.5, bpos.getY() + 1.0, bpos.getZ() + 0.5);
                    if (distSq > TEXT_MAX_DISTANCE_SQ) {
                        continue;
                    }
                    
                    // Не рисуем текст над блоками, над которыми стоит полный блок
                    BlockPos above = bpos.above();
                    BlockState aboveState = mc.level.getBlockState(above);
                    if (!aboveState.isAir() && aboveState.isSolidRender(mc.level, above)) {
                        continue;
                    }

                    String text = String.format(Locale.ROOT, "%.1f°C", temp);
                    float[] rgb = temperatureColor(temp);
                    int textColor = 0xFF000000
                            | ((int) (rgb[0] * 255) << 16)
                            | ((int) (rgb[1] * 255) << 8)
                            | (int) (rgb[2] * 255);

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
    }

    private static final int MAX_OUTLINED_MULTIBLOCKS = 16;
    /** Сколько маркеров воздушных фантомов рисовать (страховка). */
    private static final int MAX_HALO_MARKERS = 400;

    /**
     * Сбрасывает порцию заливки. Пустой буфер даёт {@code null} — это норма,
     * а не ошибка.
     */
    private static void flushFaces(BufferBuilder builder) {
        MeshData mesh = builder.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /**
     * Рамки мультиблоков и маркеры «воздушной кожи». Маркеры тоже
     * отсекаются фрустумом и радиусом — у озера лавы их сотни.
     */
    private static void renderLines(PoseStack poseStack, HeatDebugController.Snapshot snap) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        double playerX = mc.player.getX();
        double playerY = mc.player.getY();
        double playerZ = mc.player.getZ();
        double cullRadiusSq = (double) ModClientConfig.heatDebugRadius() * ModClientConfig.heatDebugRadius();

        MultiBufferSource.BufferSource bufferSource =
                com.trd.client.renderer.debug.DebugLineBuffers.source();
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());

        int drawn = 0;
        java.util.Set<BlockPos> drawnPositions = new java.util.HashSet<>();
        for (HeatSourceHelper.HeatSource source : snap.sources) {
            if (source.cells().size() <= 1 || drawn >= MAX_OUTLINED_MULTIBLOCKS) {
                continue;
            }
            addSourceOutline(poseStack, lines, source);
            drawnPositions.add(source.pos());
            drawn++;
        }
        HeatSourceHelper.HeatSource hovered = snap.hoveredMultiblock;
        if (hovered != null && hovered.cells().size() > 1 && !drawnPositions.contains(hovered.pos())) {
            addSourceOutline(poseStack, lines, hovered);
        }

        int markers = 0;
        for (Map.Entry<BlockPos, Float> entry : snap.haloCells.entrySet()) {
            if (markers >= MAX_HALO_MARKERS) {
                break;
            }
            BlockPos pos = entry.getKey();
            
            // Отсечка по радиусу и фрустуму — у озера лавы сотни маркеров
            double ddx = pos.getX() + 0.5 - playerX;
            double ddy = pos.getY() + 0.5 - playerY;
            double ddz = pos.getZ() + 0.5 - playerZ;
            if (ddx * ddx + ddy * ddy + ddz * ddz > cullRadiusSq) {
                continue;
            }
            
            float[] rgb = temperatureColor(entry.getValue());
            double h = 0.12;
            LevelRenderer.renderLineBox(poseStack, lines,
                    pos.getX() + 0.5 - h, pos.getY() + 0.5 - h, pos.getZ() + 0.5 - h,
                    pos.getX() + 0.5 + h, pos.getY() + 0.5 + h, pos.getZ() + 0.5 + h,
                    rgb[0], rgb[1], rgb[2], 0.45f);
            markers++;
        }

        bufferSource.endBatch(RenderType.lines());
    }

    /**
     * Рамка вокруг всей конструкции цветом её температуры. Сами блоки
     * конструкции не заливаются — квады з-файтились с её моделью.
     */
    private static void addSourceOutline(PoseStack poseStack, VertexConsumer lines,
                                         HeatSourceHelper.HeatSource source) {
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

        float[] rgb = temperatureColor(source.sourceTemp());
        float e = OUTLINE_EPSILON;
        LevelRenderer.renderLineBox(poseStack, lines,
                minX - e, minY - e, minZ - e,
                maxX + 1 + e, maxY + 1 + e, maxZ + 1 + e,
                rgb[0], rgb[1], rgb[2], 1.0f);
    }

    /**
     * Добавляет квад в вершинный буфер по четырём углам (по часовой стрелке).
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
     * Цвет температуры: единый плавный градиент без порогов.
     * <p>
     * Шкала нелинейная, иначе 40 °C выглядели бы как 500:
     * <ul>
     *   <li>≤ 0 °C — голубой (холод);</li>
     *   <li>0…40 °C — зелёный → жёлтый (комфорт и лёгкий жар);</li>
     *   <li>40…200 °C — жёлтый → красный (опасно);</li>
     *   <li>выше 200 °C — тот же красный, яркость НЕ накручивается:
     *       никакого «сверх-выделения» для раскалённых блоков.</li>
     * </ul>
     *
     * @return массив [r, g, b] в диапазоне 0..1
     */
    private static float[] temperatureColor(float temp) {
        if (temp <= 0.0f) {
            // Холод: от светло-голубого к насыщенно-голубому
            float c = Math.min(1.0f, -temp / 30.0f);
            return new float[]{0.4f * (1.0f - c), 0.7f, 1.0f};
        }
        float t;
        if (temp <= 40.0f) {
            t = temp / 80.0f;               // 0 .. 0.5
        } else if (temp <= 200.0f) {
            t = 0.5f + (temp - 40.0f) / 320.0f; // 0.5 .. 1.0
        } else {
            t = 1.0f;
        }
        float r, g;
        if (t < 0.5f) {
            r = 2.0f * t;
            g = 1.0f;
        } else {
            r = 1.0f;
            g = 2.0f * (1.0f - t);
        }
        return new float[]{r, g, 0.0f};
    }
}
