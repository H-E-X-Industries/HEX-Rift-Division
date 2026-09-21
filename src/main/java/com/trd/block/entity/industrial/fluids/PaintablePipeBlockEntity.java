package com.trd.block.entity.industrial.fluids;

import com.trd.api.paint.IPaintableConduit;
import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity окрашиваемой трубы.
 * Наследует FluidPipeBlockEntity => полностью работает в жидкостной сети
 * (коннекты, фильтр по жидкости, индикатор), плюс хранит "мимик" — блок для покраски.
 */
public class PaintablePipeBlockEntity extends FluidPipeBlockEntity implements IPaintableConduit {

    private BlockState mimic = null;

    public PaintablePipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PAINTABLE_PIPE_BE.get(), pos, state);
    }

    @Override
    public BlockState getMimicState() {
        return this.mimic;
    }

    public void setMimic(BlockState state) {
        this.mimic = (state != null && state.isAir()) ? null : state;
        this.setChanged();
        this.requestModelDataUpdate();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (this.mimic != null) {
            tag.put("Mimic", NbtUtils.writeBlockState(this.mimic));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("Mimic")) {
            BlockState loaded = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("Mimic"));
            this.mimic = (loaded != null && loaded.isAir()) ? null : loaded;
        } else {
            this.mimic = null;
        }
        this.requestModelDataUpdate();
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) this.loadAdditional(tag, provider);
        if (this.level != null && this.level.isClientSide) {
            this.requestModelDataUpdate();
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }
    @Override
    public net.minecraft.resources.ResourceLocation getCoreTexture() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trd", "block/conduit_core_pipe");
    }
}
