package com.trd.client.render;

import com.trd.block.basic.ModBlocks;
import com.trd.block.basic.industrial.fluids.FluidPipeBlock;
import com.trd.block.entity.industrial.fluids.FluidPipeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;

import java.util.ArrayList;
import java.util.List;

public class PipeBakedModel implements IDynamicBakedModel {
    private final BakedModel baseModel;

    public PipeBakedModel(BakedModel baseModel) {
        this.baseModel = baseModel;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData extraData, @Nullable RenderType renderType) {
        List<BakedQuad> quads = new ArrayList<>(baseModel.getQuads(state, side, rand, extraData, renderType));

        Fluid fluid = extraData.get(FluidPipeBlockEntity.FLUID_PROP);

        if (fluid != null && fluid != Fluids.EMPTY && state != null) {
            if (renderType == null || renderType == RenderType.cutout()) {
                BlockState spotsState = ModBlocks.PIPE_SPOTS.get().defaultBlockState()
                        .setValue(FluidPipeBlock.NORTH, state.getValue(FluidPipeBlock.NORTH))
                        .setValue(FluidPipeBlock.SOUTH, state.getValue(FluidPipeBlock.SOUTH))
                        .setValue(FluidPipeBlock.EAST, state.getValue(FluidPipeBlock.EAST))
                        .setValue(FluidPipeBlock.WEST, state.getValue(FluidPipeBlock.WEST))
                        .setValue(FluidPipeBlock.UP, state.getValue(FluidPipeBlock.UP))
                        .setValue(FluidPipeBlock.DOWN, state.getValue(FluidPipeBlock.DOWN))
                        .setValue(FluidPipeBlock.NONE, state.getValue(FluidPipeBlock.NONE));

                BakedModel spotsModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(spotsState);

                List<BakedQuad> spotQuads = spotsModel.getQuads(spotsState, side, rand, extraData, null);

                for (BakedQuad quad : spotQuads) {
                    quads.add(new BakedQuad(
                            quad.getVertices(),
                            1, // tintIndex
                            quad.getDirection(),
                            quad.getSprite(),
                            quad.isShade()
                    ));
                }
            }
        }
        return quads;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.solid(), RenderType.cutout());
    }

    @Override public boolean useAmbientOcclusion() { return baseModel.useAmbientOcclusion(); }
    @Override public boolean isGui3d() { return baseModel.isGui3d(); }
    @Override public boolean usesBlockLight() { return baseModel.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return baseModel.isCustomRenderer(); }
    @Override public TextureAtlasSprite getParticleIcon() { return baseModel.getParticleIcon(); }
    @Override public ItemOverrides getOverrides() { return baseModel.getOverrides(); }
}
