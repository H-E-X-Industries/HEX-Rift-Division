package com.trd.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.trd.main.MainRegistry;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Обход GemRender direct-пасса при включённом шейдерпаке Iris.
 * <p>
 * GemRender рисует glTF-модели мимо ванильного конвейера: собирает свою программу
 * ({@code #version 330 core}) и ставит GL-состояние через {@link GlStateManager}, у которого
 * внутри есть кеш состояния. Iris держит свои colortex/shadow-таргеты мимо
 * {@code RenderSystem}, из-за чего кеш {@code GlStateManager} может разойтись с реальным
 * состоянием GL. Тогда {@code _bindTexture}/{@code _disableBlend}/{@code _depthMask} внутри
 * GemRender молча ничего не делают, и модель уходит в кадр с чужой текстурой или с
 * включённым blending — то есть выглядит полупрозрачной.
 * <p>
 * Точки вмешательства:
 * <ul>
 *   <li>{@code AFTER_WEATHER} — последняя стадия, которую NeoForge диспатчит
 *       <em>внутри</em> {@code LevelRenderer#renderLevel}, то есть до mixin'а GemRender,
 *       который висит на {@code @RETURN}. Здесь снимаем состояние GL, сбрасываем кеш
 *       юнитов 0..2 и флашим очередь LEVEL сами, пока Iris ещё держит свой gbuffer-таргет.</li>
 *   <li>{@code AFTER_LEVEL} — диспатчится из {@code GameRenderer#renderLevel} уже
 *       <em>после</em> возврата, то есть после флаша GemRender. Служит только для
 *       диагностики: показывает, что GemRender оставил в GL.</li>
 * </ul>
 * Рычаги переключаются JVM-аргументами:
 * <pre>
 * -Dtrd.gemRenderOwnFlush=false   не флашить самому
 * -Dtrd.gemRenderPrimeUnits=false не сбрасывать кеш юнитов
 * -Dtrd.debugGemRender=false     не логировать состояние GL
 * </pre>
 *
 * @author trd
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public final class GemRenderShaderCompat {

    /** Сколько кадров подряд логировать состояние GL, когда шейдерпак включён. */
    private static final int DUMP_FRAMES = 2;

    /** Как часто опрашивать Iris, пока тот не поднимет шейдерпак (в кадрах). */
    private static final int PROBE_INTERVAL = 60;

    private static final boolean OWN_FLUSH = flag("trd.gemRenderOwnFlush", false);
    private static final boolean PRIME_UNITS = flag("trd.gemRenderPrimeUnits", true);
    private static final boolean DEBUG_GL = flag("trd.debugGemRender", true);

    private static boolean shaderPackInUse;
    private static boolean irisAbsent;
    private static int framesProbed;
    private static int dumpsLeft;

    private GemRenderShaderCompat() {
    }

    private static boolean flag(String property, boolean fallback) {
        String value = System.getProperty(property);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!shaderPackInUse) {
            if (irisAbsent) {
                return;
            }
            // Iris поднимает шейдерпак асинхронно, поэтому опрашиваем не один раз, а пока
            // пак не появился — иначе на первом же кадре мира проверка даст false.
            if (framesProbed++ % PROBE_INTERVAL == 0) {
                probeShaderPack();
            }
            if (!shaderPackInUse) {
                return;
            }
            MainRegistry.LOGGER.info(
                    "[GemRenderShaderCompat] shader pack active; ownFlush={} primeUnits={} debugGl={}",
                    OWN_FLUSH, PRIME_UNITS, DEBUG_GL);
        }

        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            if (DEBUG_GL && dumpsLeft > 0) {
                dumpGlState("pre-flush");
            }
            if (OWN_FLUSH) {
                if (PRIME_UNITS) {
                    primeTextureUnitCache();
                }
                DirectRenderer.flush(DirectPass.LEVEL);
            }
            if (DEBUG_GL && dumpsLeft > 0) {
                dumpsLeft--;
                dumpGlState("post-flush");
            }
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            // GemRender уже отфлашил сам на @RETURN — смотрим, что он оставил в GL.
            if (DEBUG_GL && dumpsLeft > 0) {
                dumpGlState("after-gemrender-flush");
            }
        }
    }

    /**
     * Iris — опциональная зависимость, поэтому определяем её рефлексией и не тянем в
     * зависимости.
     */
    private static void probeShaderPack() {
        boolean irisLoaded;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            irisLoaded = instance != null;
            if (irisLoaded) {
                shaderPackInUse = Boolean.TRUE.equals(
                        api.getMethod("isShaderPackInUse").invoke(instance));
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            irisLoaded = false;
        }
        if (irisLoaded && shaderPackInUse) {
            dumpsLeft = DUMP_FRAMES;
        }
        irisAbsent = !irisLoaded;
    }

    /**
     * Обнуляет привязанные текстуры на юнитах 0..2 так, чтобы кеш {@link GlStateManager}
     * разошёлся с GL. После этого {@code GlStateManager._bindTexture} внутри GemRender уже не
     * сможет решить, что нужная текстура «уже» привязана, и реально выполнит
     * {@code glBindTexture}.
     * <p>
     * Активный юнит тоже берём через {@code GlStateManager}, иначе его собственный кеш
     * активного юнита разошёлся бы с GL.
     */
    private static void primeTextureUnitCache() {
        for (int unit = 0; unit <= 2; unit++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
            GlStateManager._bindTexture(0);
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private static void dumpGlState(String label) {
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL20.GL_VIEWPORT, viewport);
        StringBuilder sb = new StringBuilder(384);
        sb.append("[GemRenderShaderCompat] ").append(label)
                .append(" fbo=").append(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING))
                .append(" viewport=").append(viewport[0]).append('x').append(viewport[3])
                .append(" program=").append(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM))
                .append(" blend=").append(GL11.glGetInteger(GL11.GL_BLEND))
                .append(" blendRGB=").append(GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB))
                .append('/').append(GL11.glGetInteger(GL14.GL_BLEND_DST_RGB))
                .append(" blendA=").append(GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA))
                .append('/').append(GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA))
                .append(" depthTest=").append(GL11.glGetInteger(GL11.GL_DEPTH_TEST))
                .append(" depthMask=").append(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK))
                .append(" depthFunc=").append(GL11.glGetInteger(GL11.GL_DEPTH_FUNC))
                .append(" cull=").append(GL11.glGetInteger(GL11.GL_CULL_FACE));

        for (int unit = 0; unit <= 2; unit++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
            sb.append(" | u").append(unit).append('=').append(textureInfo());
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        MainRegistry.LOGGER.info(sb.toString());
    }

    private static String textureInfo() {
        int id = GL11.glGetInteger(GL13.GL_TEXTURE_BINDING_2D);
        if (id == 0) {
            return "none";
        }
        String format = "0x" + Integer.toHexString(
                GL11.glGetTexLevelParameteri(GL13.GL_TEXTURE_2D, 0, GL13.GL_TEXTURE_INTERNAL_FORMAT));
        int width = GL11.glGetTexLevelParameteri(GL13.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL13.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        return id + "(" + format + " " + width + "x" + height + ")";
    }
}
