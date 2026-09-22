package com.trd.block.basic.industrial;

import com.trd.block.entity.industrial.conveyors.ConveyorBufferBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ConveyorExtractorBlock extends ConveyorBufferBlock {
    public static final com.mojang.serialization.MapCodec<ConveyorExtractorBlock> CODEC = simpleCodec(ConveyorExtractorBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return CODEC;
    }

    public ConveyorExtractorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConveyorBufferBlockEntity(pos, state, ConveyorBufferBlockEntity.Mode.EXTRACTOR);
    }
}