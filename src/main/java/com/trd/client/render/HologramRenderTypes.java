package com.trd.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * RenderType для полупрозрачных голограмм сборки без записи в буфер глубины (COLOR_WRITE).
 * Рисуется на стадии AFTER_TRANSLUCENT_BLOCKS поверх воды и блоков, не стирая воду позади голограммы,
 * при этом сохраняя корректный depth-test против непрозрачных блоков и воды перед голограммой.
 */
public final class HologramRenderTypes extends RenderType {

    private static final Function<ResourceLocation, RenderType> TRANSLUCENT_NO_DEPTH_WRITE = Util.memoize(texture -> {
        CompositeState state = CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false);

        return RenderType.create(
                "trd_hologram_translucent_no_depth",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                state
        );
    });

    private HologramRenderTypes() {
        super("trd_hologram_dummy", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }

    public static RenderType translucentNoDepthWrite(ResourceLocation texture) {
        return TRANSLUCENT_NO_DEPTH_WRITE.apply(texture);
    }
}
