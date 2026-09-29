package com.trd.fx.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

/**
 * Рендер-тип частиц с увеличенной дальностью отрисовки (дым ударной волны, грибовидное облако).
 *
 * <p>Как и в {@link AdditiveParticleRenderType}, в 1.21.1 состояние GL выставляется целиком
 * в {@link #begin} — отдельного {@code end()} больше нет.
 */
public class LongRangeParticleRenderType implements ParticleRenderType {

    public static final LongRangeParticleRenderType INSTANCE = new LongRangeParticleRenderType();

    private LongRangeParticleRenderType() {
    }

    @Override
    public String toString() {
        return "long_range_particle";
    }

    @Override
    public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.disableCull();
        return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
    }
}
