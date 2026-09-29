package com.trd.fx.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

/**
 * Рендер-тип частиц с аддитивным блендингом (искры взрыва/огня).
 *
 * <p>В 1.21.1 у {@link ParticleRenderType} исчез метод {@code end(Tesselator)}: движок сам
 * закрывает буфер и рисует его. Состояние GL теперь обязано выставляться целиком в
 * {@link #begin}, поэтому здесь дублируются и blend-функция, и depthMask.
 */
public class AdditiveParticleRenderType implements ParticleRenderType {

    public static final AdditiveParticleRenderType INSTANCE = new AdditiveParticleRenderType();

    private AdditiveParticleRenderType() {
    }

    @Override
    public String toString() {
        return "additive_particle";
    }

    @Override
    public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.disableCull();
        return tesselator.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE);
    }
}
