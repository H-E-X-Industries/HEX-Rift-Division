package com.trd.client.renderer.debug;



import com.trd.entity.weapons.turrets.TurretLightEntity;
import com.trd.entity.weapons.turrets.TurretLightLinkedEntity;
import com.trd.main.MainRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Отрисовка отладочной визуализации турелей (сетка сканирования, точка упреждения,
 * траектория) после отрисовки сущностей. Включается по F3.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class TurretDebugRenderEvent {

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // проверка флага вынесена в начало
        if (!TurretDebugKeyHandler.debugVisualizationEnabled) {
            return;
        }

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;

        if (level == null || mc.gameRenderer.getMainCamera() == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();

        // 1. Бортовые турели (сетка сканирования, точка упреждения, траектория)
        renderTurretDebug(poseStack, bufferSource, level, camPos.x, camPos.y, camPos.z);

        // 2. Тромбон и фронт взрывных волн
        WeaponsDebugRenderer.render(poseStack, bufferSource, level);

        bufferSource.endBatch(net.minecraft.client.renderer.RenderType.lines());
    }

    /** Обходит турели в радиусе 64 вокруг камеры и зовёт их отрисовку. */
    private static void renderTurretDebug(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                           Level level, double camX, double camY, double camZ) {
        Vec3 renderCenter = new Vec3(camX, camY, camZ);
        for (Entity entity : level.getEntities((Entity) null, new AABB(
                renderCenter.x - 64, renderCenter.y - 64, renderCenter.z - 64,
                renderCenter.x + 64, renderCenter.y + 64, renderCenter.z + 64),
                e -> e instanceof TurretLightEntity || e instanceof TurretLightLinkedEntity)) {

            if (entity instanceof TurretLightEntity turret && !turret.isRemoved()) {
                TurretDebugRenderer_Enhanced.renderTurretDebug(poseStack, bufferSource, turret, camX, camY, camZ);
            } else if (entity instanceof TurretLightLinkedEntity linked && !linked.isRemoved()) {
                TurretDebugRenderer_Enhanced.renderTurretDebug(poseStack, bufferSource, linked, camX, camY, camZ);
            }
        }
    }
}
