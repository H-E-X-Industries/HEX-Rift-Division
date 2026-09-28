package com.trd.block.basic.deco;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WireFenceAltBlock extends WireFenceBlock {

    public static final TagKey<Block> CONNECTS_TO_ALT = TagKey.create(Registries.BLOCK,
            new ResourceLocation("trd", "wire_fence_alt_connections"));

    private static final VoxelShape POST = box(7.0, 0.0, 7.0, 9.0, 16.0, 9.0);

    public WireFenceAltBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape postShape() {
        return POST;
    }

    @Override
    public boolean connectsTo(BlockState state, boolean isSticky, Direction direction) {
        if (isExceptionForConnection(state)) {
            return false;
        }
        Block block = state.getBlock();
        return block.getClass() == WireFenceAltBlock.class
                || block instanceof SteelPropsBlock
                || state.is(CONNECTS_TO_ALT)
                || state.canOcclude();
    }
}