package com.trd.block.entity.oil;

import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

public class OilReservoirBlockEntity extends BlockEntity {

    @Nullable
    private UUID veinId;

    public OilReservoirBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OIL_RESERVOIR.get(), pos, state);
    }

    public void setVeinId(UUID id) {
        this.veinId = id;
        setChanged();
    }

    @Nullable
    public UUID getVeinId() {
        return veinId;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (veinId != null) {
            tag.putUUID("VeinId", veinId);
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.hasUUID("VeinId")) {
            this.veinId = tag.getUUID("VeinId");
        }
    }
}
