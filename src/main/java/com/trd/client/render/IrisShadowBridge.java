package com.trd.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * Мост между Iris (шейдерами) и нашими BlockEntityRenderers во время прохода теней.
 * 
 * В связке Iris + Sodium ванильный список видимых блочных сущностей (visibleBlockEntities)
 * не заполняется, из-за чего Iris по умолчанию не вызывает render() у BER.
 * Данный класс перехватывает функцию рендера BlockEntity в Iris (ShadowRenderingState.setBlockEntityRenderFunction)
 * и во время теневого прохода принудительно отрисовывает 3D-модели всех наших мультиблоков и кинетики
 * напрямую в буфер карты теней.
 */
public class IrisShadowBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("TRD-IrisShadowBridge");
    private static final Set<BlockEntity> TRACKED_ENTITIES = Collections.newSetFromMap(new WeakHashMap<>());
    
    private static boolean irisChecked = false;
    private static boolean irisPresent = false;
    private static Class<?> shadowRenderingStateClass = null;
    private static Class<?> blockEntityRenderFunctionClass = null;
    private static Field functionField = null;
    private static Method setFunctionMethod = null;
    private static Object activeProxy = null;
    private static Object originalFunction = null;

    static {
        init();
    }

    public static void track(BlockEntity be) {
        if (be != null) {
            synchronized (TRACKED_ENTITIES) {
                TRACKED_ENTITIES.add(be);
            }
            ensureHooked();
        }
    }

    public static void untrack(BlockEntity be) {
        if (be != null) {
            synchronized (TRACKED_ENTITIES) {
                TRACKED_ENTITIES.remove(be);
            }
        }
    }

    public static List<BlockEntity> getTrackedEntities() {
        synchronized (TRACKED_ENTITIES) {
            return new ArrayList<>(TRACKED_ENTITIES);
        }
    }

    public static void init() {
        if (irisChecked) return;
        irisChecked = true;

        try {
            shadowRenderingStateClass = Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState");
            blockEntityRenderFunctionClass = Class.forName("net.irisshaders.iris.shadows.ShadowRenderingState$BlockEntityRenderFunction");
            functionField = shadowRenderingStateClass.getDeclaredField("function");
            functionField.setAccessible(true);
            setFunctionMethod = shadowRenderingStateClass.getMethod("setBlockEntityRenderFunction", blockEntityRenderFunctionClass);
            irisPresent = true;
            ensureHooked();
            LOGGER.info("IrisShadowBridge: successfully initialized and connected to Iris shadow pipeline.");
        } catch (ClassNotFoundException e) {
            irisPresent = false;
            LOGGER.info("IrisShadowBridge: Iris is not installed, shadow bridge dormant.");
        } catch (Throwable t) {
            irisPresent = false;
            LOGGER.warn("IrisShadowBridge: Failed to hook into Iris", t);
        }
    }

    public static void ensureHooked() {
        if (!irisPresent || shadowRenderingStateClass == null) return;

        try {
            Object currentFn = functionField.get(null);
            if (currentFn == activeProxy && activeProxy != null) {
                return; // Уже установлен наш прокси
            }

            if (currentFn != null && currentFn != activeProxy) {
                originalFunction = currentFn;
            }

            activeProxy = Proxy.newProxyInstance(
                    blockEntityRenderFunctionClass.getClassLoader(),
                    new Class<?>[]{ blockEntityRenderFunctionClass },
                    (proxy, method, args) -> {
                        if ("renderBlockEntities".equals(method.getName())) {
                            int count = 0;
                            // 1. Вызываем исходный обработчик (если был)
                            if (originalFunction != null && originalFunction != proxy) {
                                try {
                                    Object res = method.invoke(originalFunction, args);
                                    if (res instanceof Number num) {
                                        count = num.intValue();
                                    }
                                } catch (Throwable ignored) {}
                            }

                            // 2. Дополнительно отрисовываем все наши мультиблоки и машины в буфер теней
                            try {
                                PoseStack poseStack = (PoseStack) args[2];
                                double camX = ((Number) args[4]).doubleValue();
                                double camY = ((Number) args[5]).doubleValue();
                                double camZ = ((Number) args[6]).doubleValue();
                                float tickDelta = ((Number) args[7]).floatValue();

                                Minecraft mc = Minecraft.getInstance();
                                if (mc.level != null) {
                                    BlockEntityRenderDispatcher dispatcher = mc.getBlockEntityRenderDispatcher();
                                    RenderBuffers buffers = (RenderBuffers) args[1];
                                    MultiBufferSource bufferSource = buffers.bufferSource();

                                    Set<BlockEntity> entitiesToRender = new HashSet<>(getTrackedEntities());

                                    // Сканируем загруженные чанки вокруг камеры, чтобы гарантированно поймать ВСЕ машины TRD
                                    int minChunkX = SectionPos.blockToSectionCoord((int) (camX - 128));
                                    int maxChunkX = SectionPos.blockToSectionCoord((int) (camX + 128));
                                    int minChunkZ = SectionPos.blockToSectionCoord((int) (camZ - 128));
                                    int maxChunkZ = SectionPos.blockToSectionCoord((int) (camZ + 128));

                                    for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                                        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                                            LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, false);
                                            if (chunk != null) {
                                                for (BlockEntity be : chunk.getBlockEntities().values()) {
                                                    if (be != null && !be.isRemoved()) {
                                                        var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
                                                        if (key != null && MainRegistry.MOD_ID.equals(key.getNamespace())) {
                                                            entitiesToRender.add(be);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    for (BlockEntity be : entitiesToRender) {
                                        if (be == null || be.isRemoved() || be.getLevel() != mc.level) continue;
                                        if (dispatcher.getRenderer(be) == null) continue;

                                        BlockPos pos = be.getBlockPos();
                                        double dx = pos.getX() - camX;
                                        double dy = pos.getY() - camY;
                                        double dz = pos.getZ() - camZ;

                                        // Отсекаем по дистанции теней (макс 192 блока)
                                        if (dx * dx + dy * dy + dz * dz > 192.0 * 192.0) continue;

                                        poseStack.pushPose();
                                        poseStack.translate(dx, dy, dz);
                                        try {
                                            dispatcher.render(be, tickDelta, poseStack, bufferSource);
                                            count++;
                                        } catch (Throwable ignored) {
                                        } finally {
                                            poseStack.popPose();
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                LOGGER.debug("Error in shadow render pass for custom block entities", t);
                            }

                            return count;
                        }
                        return method.invoke(proxy, args);
                    }
            );

            setFunctionMethod.invoke(null, activeProxy);
        } catch (Throwable t) {
            LOGGER.warn("IrisShadowBridge: Failed to update Iris shadow hook", t);
        }
    }

    @EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static class GameEventListener {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getGameTime() % 40 == 0) {
                ensureHooked();
            }
        }

        @SubscribeEvent
        public static void onLevelLoad(LevelEvent.Load event) {
            if (event.getLevel() != null && event.getLevel().isClientSide()) {
                ensureHooked();
            }
        }
    }
}
