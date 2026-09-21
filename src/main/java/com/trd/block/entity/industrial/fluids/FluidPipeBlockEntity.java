package com.trd.block.entity.industrial.fluids;

import com.trd.api.fluids.system.FluidNetworkManager;
import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public class FluidPipeBlockEntity extends BlockEntity {

    public static final ModelProperty<Fluid> FLUID_PROP = new ModelProperty<>();
    private Fluid filterFluid = Fluids.EMPTY;
    private boolean hasFlowed = false;

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_PIPE_BE.get(), pos, state);
    }

    // Позволяет подклассам (напр. PaintablePipeBlockEntity) задать свой тип BE,
    // сохраняя всю логику жидкостной сети и коннектов.
    protected FluidPipeBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ==========================================
    // ЛОГИКА ЖИДКОСТНОЙ СЕТИ (БЕЗ ТИКОВ!)
    // ==========================================
    @Override
    public void onLoad() {
        super.onLoad();
        // Добавляем трубу в сеть только один раз при прогрузке чанка
        if (this.level != null && !this.level.isClientSide) {
            FluidNetworkManager manager = FluidNetworkManager.get((ServerLevel) this.level);
            if (!manager.hasNode(this.getBlockPos())) {
                manager.addNode(this.getBlockPos());
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        // Удаляем трубу из сети при разрушении блока или выгрузке чанка
        if (this.level != null && !this.level.isClientSide) {
            FluidNetworkManager.get((ServerLevel) this.level).removeNode(this.getBlockPos());
        }
    }

    // ==========================================
    // ЛОГИКА ФИЛЬТРА И РЕНДЕРА
    // ==========================================
    public void setFilterFluid(Fluid fluid) {
        this.filterFluid = fluid;
        this.setChanged();
        this.requestModelDataUpdate();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public Fluid getFilterFluid() {
        return filterFluid;
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder()
                .with(FLUID_PROP, this.filterFluid)
                .build();
    }

    public boolean hasFlowed() {
        return hasFlowed;
    }

    public void setHasFlowed(boolean hasFlowed) {
        if (this.hasFlowed != hasFlowed) {
            this.hasFlowed = hasFlowed;
            this.setChanged();
            if (this.level != null && !this.level.isClientSide) {
                // Отправляем пакет клиенту, чтобы он начал рисовать пузырьки
                this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    // ==========================================
    // СОХРАНЕНИЕ И ПАКЕТЫ (NBT)
    // ==========================================
    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        net.minecraft.resources.ResourceLocation fluidKey = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(filterFluid);
        tag.putString("FilterFluid", fluidKey == null ? "minecraft:empty" : fluidKey.toString());
        tag.putBoolean("HasFlowed", this.hasFlowed);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        net.minecraft.resources.ResourceLocation fluidKey = net.minecraft.resources.ResourceLocation.parse(tag.getString("FilterFluid"));
        this.filterFluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(fluidKey);
        if (this.filterFluid == null) this.filterFluid = net.minecraft.world.level.material.Fluids.EMPTY;
        this.hasFlowed = tag.getBoolean("HasFlowed");
        this.requestModelDataUpdate();
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, net.minecraft.core.HolderLookup.Provider provider) {
        super.onDataPacket(net, pkt, provider);
        // Принудительно заставляем клиент перерисовать точки при получении пакета
        if (this.level != null && this.level.isClientSide) {
            this.requestModelDataUpdate();
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }
}
