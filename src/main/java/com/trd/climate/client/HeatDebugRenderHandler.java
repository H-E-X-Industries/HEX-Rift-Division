package com.trd.climate.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.trd.client.config.ModKeyBindings;
import com.trd.main.MainRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * F3-отладка температурных полей (Heat Debug Overlay).
 * При включенном экране F3 сканирует источники тепла (костры, печи, лаву и др.)
 * и поблочно окрашивает их верхние грани в градиент от зеленого к желтому и красному,
 * отображая точную температуру (+X.X°C) над каждым блоком в 3D пространстве.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class HeatDebugRenderHandler {

    private static final int SCAN_RADIUS_XZ = 16;
    private static final int SCAN_RADIUS_Y = 8;
    private static final double TEXT_MAX_DISTANCE_SQ = 12.0 * 12.0;

    private record HeatSource(BlockPos pos, double radius, float maxHeat) {}

    private static class BlockHeatInfo {
        float totalHeat = 0.0f;
        float maxHeatScale = 1.0f;
    }

    public static boolean heatDebugEnabled = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ModKeyBindings.HEAT_DEBUG_KEY.consumeClick()) {
            return;
        }
        if (isAnyOtherKeyPressed(ModKeyBindings.HEAT_DEBUG_KEY)) {
            return;
        }
        heatDebugEnabled = !heatDebugEnabled;
    }

    /** Проверяет, что не зажата ни одна другая клавиша — защита от комбинаций вида F3+H, если забинден F3. */
    private static boolean isAnyOtherKeyPressed(KeyMapping keyMapping) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        long window = mc.getWindow().getWindow();
        if (window == 0L) {
            return false;
        }
        int boundKey = keyMapping.getKey().getValue();
        if (boundKey == GLFW.GLFW_KEY_F3) {
            for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_MENU; key++) {
                if (key == boundKey) {
                    continue;
                }
                if (GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS) {
                    return true;
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        // Проверяем флаг включения отладки (управляется переназначаемой клавишей HEAT_DEBUG_KEY)
        if (!heatDebugEnabled) {
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

        // 1. Поиск активных источников тепла вокруг игрока
        List<HeatSource> sources = findNearbyHeatSources(level, playerPos);
        if (sources.isEmpty()) {
            return;
        }

        // 2. Расчет температуры для каждого затронутого блока с открытой верхней гранью
        Map<BlockPos, BlockHeatInfo> affectedBlocks = new HashMap<>();
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();

        for (HeatSource source : sources) {
            // Включаем сам блок источника тепла в расчет
            BlockHeatInfo srcInfo = affectedBlocks.computeIfAbsent(source.pos(), k -> new BlockHeatInfo());
            srcInfo.totalHeat = Math.max(srcInfo.totalHeat, source.maxHeat());
            srcInfo.maxHeatScale = Math.max(srcInfo.maxHeatScale, source.maxHeat());

            int rad = (int) Math.ceil(source.radius());
            Vec3 sourceCenter = new Vec3(source.pos().getX() + 0.5, source.pos().getY() + 0.5, source.pos().getZ() + 0.5);

            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    for (int dy = -rad; dy <= rad; dy++) {
                        checkPos.set(source.pos().getX() + dx, source.pos().getY() + dy, source.pos().getZ() + dz);
                        if (checkPos.equals(source.pos())) {
                            continue;
                        }
                        BlockState state = level.getBlockState(checkPos);

                        // Проверяем, что блок твердый, а над ним воздух или прозрачный блок
                        if (!state.isAir() && state.isSolidRender(level, checkPos)) {
                            BlockState aboveState = level.getBlockState(checkPos.above());
                            if (!aboveState.isSolidRender(level, checkPos.above())) {
                                Vec3 topCenter = new Vec3(checkPos.getX() + 0.5, checkPos.getY() + 1.0, checkPos.getZ() + 0.5);
                                double dist = sourceCenter.distanceTo(topCenter);

                                if (dist <= source.radius()) {
                                    // Проверка прямой видимости (рейкаст), чтобы тепло не проходило сквозь стены
                                    ClipContext clipContext = new ClipContext(
                                            sourceCenter,
                                            topCenter,
                                            ClipContext.Block.VISUAL,
                                            ClipContext.Fluid.NONE,
                                            CollisionContext.empty()
                                    );
                                    BlockHitResult hit = level.clip(clipContext);
                                    if (hit.getType() == HitResult.Type.MISS
                                            || hit.getBlockPos().equals(source.pos())
                                            || hit.getBlockPos().equals(checkPos)) {

                                        float factor = (float) (1.0 - (dist / source.radius()));
                                        float heat = source.maxHeat() * factor * factor;

                                        if (heat > 0.05f) {
                                            BlockPos immutablePos = checkPos.immutable();
                                            BlockHeatInfo info = affectedBlocks.computeIfAbsent(immutablePos, k -> new BlockHeatInfo());
                                            info.totalHeat += heat;
                                            info.maxHeatScale = Math.max(info.maxHeatScale, source.maxHeat());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (affectedBlocks.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();

        // 3. Отрисовка цветных тинтов на верхних гранях блоков
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

        for (Map.Entry<BlockPos, BlockHeatInfo> entry : affectedBlocks.entrySet()) {
            BlockPos bpos = entry.getKey();
            BlockHeatInfo info = entry.getValue();

            float t = Math.min(1.0f, info.totalHeat / Math.max(1.0f, info.maxHeatScale));

            // Градиент: t=0 -> Зеленый, t=0.5 -> Желтый, t=1.0 -> Красный
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

        // 4. Отрисовка 3D надписей с температурой над блоками
        try (com.mojang.blaze3d.vertex.ByteBufferBuilder byteBufferBuilder = new com.mojang.blaze3d.vertex.ByteBufferBuilder(16384)) {
            MultiBufferSource.BufferSource textBufferSource = MultiBufferSource.immediate(byteBufferBuilder);

            for (Map.Entry<BlockPos, BlockHeatInfo> entry : affectedBlocks.entrySet()) {
                BlockPos bpos = entry.getKey();
                BlockHeatInfo info = entry.getValue();

                double distSq = camPos.distanceToSqr(bpos.getX() + 0.5, bpos.getY() + 1.0, bpos.getZ() + 0.5);
                if (distSq > TEXT_MAX_DISTANCE_SQ) {
                    continue; // Не захламляем экран вдали
                }

                String text = String.format(Locale.ROOT, "+%.1f°C", info.totalHeat);
                float t = Math.min(1.0f, info.totalHeat / Math.max(1.0f, info.maxHeatScale));
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

    private static List<HeatSource> findNearbyHeatSources(ClientLevel level, BlockPos playerPos) {
        List<HeatSource> list = new ArrayList<>();
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();

        for (int x = -SCAN_RADIUS_XZ; x <= SCAN_RADIUS_XZ; x++) {
            for (int z = -SCAN_RADIUS_XZ; z <= SCAN_RADIUS_XZ; z++) {
                for (int y = -SCAN_RADIUS_Y; y <= SCAN_RADIUS_Y; y++) {
                    checkPos.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                    BlockState state = level.getBlockState(checkPos);

                    // Костры
                    if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
                        list.add(new HeatSource(checkPos.immutable(), 5.0, 15.0f));
                    } else if (state.is(Blocks.SOUL_CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
                        list.add(new HeatSource(checkPos.immutable(), 5.0, 10.5f));
                    }
                    // Печки
                    else if ((state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER))
                            && state.getValue(AbstractFurnaceBlock.LIT)) {
                        list.add(new HeatSource(checkPos.immutable(), 3.0, 12.0f));
                    }
                    // Лава
                    else if (state.is(Blocks.LAVA)) {
                        list.add(new HeatSource(checkPos.immutable(), 4.0, 20.0f));
                    }
                    // Огонь
                    else if (state.is(Blocks.FIRE)) {
                        list.add(new HeatSource(checkPos.immutable(), 2.5, 10.0f));
                    }
                }
            }
        }

        return list;
    }
}
