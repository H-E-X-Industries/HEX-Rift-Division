package com.trd.fx.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;

public class AdditiveParticleRenderType implements ParticleRenderType {

    public static final AdditiveParticleRenderType INSTANCE = new AdditiveParticleRenderType();

    private AdditiveParticleRenderType() {
    }

    @Override
    public String toString() {
        return "additive_particle";
    }

    @Override
    public void begin(BufferBuilder buffer, TextureManager textureManager) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.disableCull();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
    }

    @Override
    public void end(Tesselator tesselator) {
        tesselator.end();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
