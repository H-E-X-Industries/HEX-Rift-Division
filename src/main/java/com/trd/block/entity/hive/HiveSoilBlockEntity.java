package com.trd.block.entity.hive;

import com.trd.api.hive.HiveNetworkManager;
import com.trd.api.hive.HiveNetworkMember;
import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class HiveSoilBlockEntity extends BlockEntity implements HiveNetworkMember {
    private UUID networkId;

    public HiveSoilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HIVE_SOIL.get(), pos, state);
    }

    @Override
    public UUID getNetworkId() {
        return networkId;
    }

    @Override
    public void setNetworkId(UUID id) {
        this.networkId = id;
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (this.networkId != null) {
            tag.putUUID("NetworkId", this.networkId);
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.hasUUID("NetworkId")) {
            this.networkId = tag.getUUID("NetworkId");
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide && this.networkId != null) {
            HiveNetworkManager manager = HiveNetworkManager.get(this.level);
            if (manager != null) {
                manager.addNode(this.networkId, this.worldPosition, false);
            }
        }
    }
}
