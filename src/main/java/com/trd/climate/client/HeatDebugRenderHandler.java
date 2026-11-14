package com.trd.climate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.trd.api.climate.HeatSourceRegistry;
import com.trd.climate.calculation.HeatSourceHelper;
import com.trd.climate.calculation.TemperatureCalculator;
import com.trd.client.config.ModDebugMode;
import com.trd.main.MainRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
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
 * что и для термометра/игрока ({@link TemperatureCalculator#calculatePointTemp}),
 * поэтому его показания не могут разойтись с приборами. Раньше оверлей имел
 * собственную таблицу источников и собственную формулу, из-за чего показывал
 * «+31°C» там, где термометр честно мерил 20°C.
 * <p>
 * Тепловое поле пересчитывается раз в 5 тиков и кэшируется: расчёт не душит кадр.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class HeatDebugRenderHandler {

    private static final int SCAN_RADIUS_XZ = 16;
    private static final int SCAN_RADIUS_Y = 8;
    private static final int REBUILD_INTERVAL_TICKS = 5;
    private static final double TEXT_MAX_DISTANCE_SQ = 12.0 * 12.0;

    /** Кэш теплового поля: позиция точки измерения -> абсолютная температура в ней. */
    private static final Map<BlockPos, Float> cachedField = new HashMap<>();
    private static int lastRebuildTick = Integer.MIN_VALUE;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        // Проверяем флаг включения отладки (общий режим отладки мода, переключаемый
        // переназначаемой клавишей DEBUG_RENDER_KEY)
        if (!ModDebugMode.enabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        ClientLevel level = mc.level;
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        BlockPos playerPos = mc.player.blockPosition();

        // 1. Пересчёт теплового поля раз в REBUILD_INTERVAL_TICKS тиков
        if (mc.player.tickCount - lastRebuildTick >= REBUILD_INTERVAL_TICKS
                || lastRebuildTick == Integer.MIN_VALUE) {
            lastRebuildTick = mc.player.tickCount;
            rebuildField(level, playerPos);
        }

        if (cachedField.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();

        // 2. Отрисовка цветных тинтов на верхних гранях блоков
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

        for (Map.Entry<BlockPos, Float> entry : cachedField.entrySet()) {
            BlockPos bpos = entry.getKey();
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

            float x0 = bpos.getX();
            float x1 = x0 + 1.0f;
            BlockState bstate = level.getBlockState(bpos);
            float yOffset = (bstate.is(Blocks.CAMPFIRE) || bstate.is(Blocks.SOUL_CAMPFIRE)) ? 0.45f : 1.003f;
            float y = bpos.getY() + yOffset;
            float z0 = bpos.getZ();
            float z1 = z0 + 1.0f;

            builder.addVertex(pose, x0, y, z0).setColor(r, g, b, a);
            builder.addVertex(pose, x0, y, z1).setColor(r, g, b, a);
            builder.addVertex(pose, x1, y, z1).setColor(r, g, b, a);
            builder.addVertex(pose, x1, y, z0).setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        poseStack.popPose();

        // 3. Отрисовка 3D надписей с абсолютной температурой над блоками
        try (com.mojang.blaze3d.vertex.ByteBufferBuilder byteBufferBuilder = new com.mojang.blaze3d.vertex.ByteBufferBuilder(16384)) {
            MultiBufferSource.BufferSource textBufferSource = MultiBufferSource.immediate(byteBufferBuilder);

            for (Map.Entry<BlockPos, Float> entry : cachedField.entrySet()) {
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

                BlockState bstate = level.getBlockState(bpos);
                double textY = (bstate.is(Blocks.CAMPFIRE) || bstate.is(Blocks.SOUL_CAMPFIRE))
                        ? bpos.getY() + 0.85
                        : bpos.getY() + 1.25;

                net.minecraft.client.renderer.debug.DebugRenderer.renderFloatingText(
                        poseStack,
                        textBufferSource,
                        text,
                        bpos.getX() + 0.5,
                        textY,
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
     * Цветовая шкала по абсолютной температуре: 0°C -> зелёный, 20°C -> жёлтый, 40°C+ -> красный.
     */
    private static float temperatureGradient(float temp) {
        return Math.min(1.0f, Math.max(0.0f, temp / 40.0f));
    }

    /**
     * Полный пересчёт теплового поля вокруг игрока.
     * <p>
     * 1. Одним проходом находятся все источники тепла (реестр — ваниль и приборы мода).
     * 2. Для каждого блока с «полом» (сплошной блок, над ним воздух/прозрачный) в радиусе
     *    излучения источников считается абсолютная температура точки тем же кодом,
     *    что и у термометра: {@link TemperatureCalculator#calculatePointTemp}.
     */
    private static void rebuildField(ClientLevel level, BlockPos playerPos) {
        cachedField.clear();

        // Источники ищем в области отрисовки + максимальный радиус излучения,
        // чтобы источник за краем экрана всё равно грел видимые блоки.
        float maxSourceRadius = HeatSourceRegistry.maxRadius();
        List<HeatSourceHelper.HeatSource> sources = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int scanR = SCAN_RADIUS_XZ + (int) Math.ceil(maxSourceRadius);
        int scanY = SCAN_RADIUS_Y + (int) Math.ceil(maxSourceRadius);
        for (int x = -scanR; x <= scanR; x++) {
            for (int z = -scanR; z <= scanR; z++) {
                for (int y = -scanY; y <= scanY; y++) {
                    cursor.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                    HeatSourceHelper.HeatSource source = HeatSourceHelper.resolveAt(level, cursor);
                    if (source != null) {
                        sources.add(source);
                    }
                }
            }
        }

        if (sources.isEmpty()) {
            return;
        }

        // Для каждого источника окрашиваем блоки с «полом» в радиусе его излучения
        for (HeatSourceHelper.HeatSource source : sources) {
            int rad = (int) Math.ceil(source.radius());
            BlockPos srcPos = source.pos();

            // Сам блок источника — его температура видна на нём самом
            cachedField.put(srcPos, TemperatureCalculator.calculatePointTemp(level, srcPos, sources));

            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    for (int dy = -rad; dy <= rad; dy++) {
                        if (dx * dx + dy * dy + dz * dz > rad * rad) {
                            continue;
                        }
                        cursor.set(srcPos.getX() + dx, srcPos.getY() + dy, srcPos.getZ() + dz);
                        if (cursor.equals(srcPos)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(cursor);

                        // Показываем температуру на верхней грани «пола»
                        if (!state.isAir() && state.isSolidRender(level, cursor)) {
                            BlockState aboveState = level.getBlockState(cursor.above());
                            if (!aboveState.isSolidRender(level, cursor.above())) {
                                BlockPos floorPos = cursor.immutable();
                                if (!cachedField.containsKey(floorPos)) {
                                    float temp = TemperatureCalculator.calculatePointTemp(
                                            level, floorPos.above(), sources);
                                    cachedField.put(floorPos, temp);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
