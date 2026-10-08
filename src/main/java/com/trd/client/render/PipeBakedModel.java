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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PipeBakedModel implements IDynamicBakedModel {
    private final BakedModel baseModel;

    public PipeBakedModel(BakedModel baseModel) {
        this.baseModel = baseModel;
    }

    public static int getFluidColor(Fluid fluid) {
        if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) {
            return 0xFFFF5500;
        }
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) {
            return 0xFF3F76E4;
        }
        try {
            int tint = IClientFluidTypeExtensions.of(fluid.getFluidType())
                    .getTintColor(new FluidStack(fluid, 1000));
            return 0xFF000000 | tint;
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData extraData, @Nullable RenderType renderType) {
        List<BakedQuad> quads = new ArrayList<>(baseModel.getQuads(state, side, rand, extraData, renderType));

        // OBJ-модели не имеют cullface и отдают геометрию только при side == null
        if (side == null && state != null) {
            Fluid fluid = extraData.get(FluidPipeBlockEntity.FLUID_PROP);

            if (fluid != null && fluid != Fluids.EMPTY) {
                if (renderType == null || renderType == RenderType.cutout()) {
                    BlockState spotsState = ModBlocks.PIPE_SPOTS.get().defaultBlockState()
                            .setValue(FluidPipeBlock.NORTH, state.getValue(FluidPipeBlock.NORTH))
                            .setValue(FluidPipeBlock.SOUTH, state.getValue(FluidPipeBlock.SOUTH))
                            .setValue(FluidPipeBlock.EAST, state.getValue(FluidPipeBlock.EAST))
                            .setValue(FluidPipeBlock.WEST, state.getValue(FluidPipeBlock.WEST))
                            .setValue(FluidPipeBlock.UP, state.getValue(FluidPipeBlock.UP))
                            .setValue(FluidPipeBlock.DOWN, state.getValue(FluidPipeBlock.DOWN))
                            .setValue(FluidPipeBlock.NONE, state.getValue(FluidPipeBlock.NONE))
                            .setValue(FluidPipeBlock.WATERLOGGED, state.getValue(FluidPipeBlock.WATERLOGGED));

                    BakedModel spotsModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(spotsState);

                    List<BakedQuad> spotQuads = spotsModel.getQuads(spotsState, null, rand, extraData, null);
                    if (spotQuads.isEmpty() && renderType != null) {
                        spotQuads = spotsModel.getQuads(spotsState, null, rand, extraData, renderType);
                    }
                    if (spotQuads.isEmpty()) {
                        spotQuads = spotsModel.getQuads(spotsState, null, rand, extraData, RenderType.cutout());
                    }
                    if (spotQuads.isEmpty()) {
                        spotQuads = spotsModel.getQuads(spotsState, null, rand, extraData, RenderType.solid());
                    }

                    int tintColor = getFluidColor(fluid);
                    int r = (tintColor >> 16) & 0xFF;
                    int g = (tintColor >> 8) & 0xFF;
                    int b = tintColor & 0xFF;
                    int a = (tintColor >> 24) & 0xFF;
                    // Little-endian ABGR packed int
                    int abgr = (a << 24) | (b << 16) | (g << 8) | r;

                    for (BakedQuad quad : spotQuads) {
                        int[] vertices = quad.getVertices().clone();
                        for (int v = 0; v < 4; v++) {
                            vertices[v * 8 + 3] = abgr;
                        }
                        quads.add(new BakedQuad(
                                vertices,
                                -1, // tintIndex=-1: готовый цвет в вершинах, не перезаписывается BlockColors
                                quad.getDirection(),
                                quad.getSprite(),
                                quad.isShade(),
                                quad.hasAmbientOcclusion()
                        ));
                    }
                }
            }
        }
        return quads;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        if (level != null && pos != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FluidPipeBlockEntity pipeBE) {
                Fluid f = pipeBE.getFilterFluid();
                if (f != null && f != Fluids.EMPTY) {
                    return modelData.derive().with(FluidPipeBlockEntity.FLUID_PROP, f).build();
                }
            }
        }
        return baseModel.getModelData(level, pos, state, modelData);
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
