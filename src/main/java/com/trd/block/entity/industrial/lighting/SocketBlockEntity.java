package com.trd.block.entity.industrial.lighting;

import com.trd.api.components.LampComponent;
import com.trd.api.components.ModDataComponents;
import com.trd.api.energy.EnergyNetworkManager;
import com.trd.api.energy.IEnergyConnector;
import com.trd.api.energy.IEnergyReceiver;
import com.trd.block.basic.industrial.lighting.ElectricLampBlock;
import com.trd.block.basic.industrial.lighting.EmptySocketBlock;
import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Пасивний енергетичний BlockEntity для патронів та світильників.
 * Відповідає суворому обмеженню на продуктивність: НЕ має постійного BlockEntityTicker.
 * Оновлення енергії та затухання відбуваються на запитах мережі або за розкладом блоку.
 */
public class SocketBlockEntity extends BlockEntity implements IEnergyReceiver, IEnergyConnector {

    private long energyStored = 0L;
    private long lastReceivedTick = -1L;
    private float durability = 1.0f;
    private boolean broken = false;

    public SocketBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOCKET_BE.get(), pos, state);
    }

    public float getDurability() {
        return durability;
    }

    public void setDurability(float durability) {
        this.durability = Math.max(0.0f, Math.min(1.0f, durability));
        setChanged();
    }

    public boolean isBroken() {
        return broken;
    }

    public void setBroken(boolean broken) {
        this.broken = broken;
        setChanged();
    }

    public long getConsumption() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof ElectricLampBlock lamp) {
            return lamp.getConsumptionPerTick();
        }
        return 0L;
    }

    // ==================== IEnergyReceiver ====================

    @Override
    public long getEnergyStored() {
        return energyStored;
    }

    @Override
    public long getMaxEnergyStored() {
        // Буфер на 30 тактів роботи встановленої лампи
        return getConsumption() * 30L;
    }

    @Override
    public void setEnergyStored(long energy) {
        this.energyStored = Math.max(0L, Math.min(energy, getMaxEnergyStored()));
        setChanged();
    }

    @Override
    public long getReceiveSpeed() {
        // Швидкість прийому = 2x від споживання лампи
        return getConsumption() * 2L;
    }

    @Override
    public Priority getPriority() {
        // Освітлення має найнижчий пріоритет (LOW), щоб перебої мережі відбивалися на лампах
        return Priority.LOW;
    }

    @Override
    public boolean canReceive() {
        long consumption = getConsumption();
        return consumption > 0L && !isBroken() && energyStored < getMaxEnergyStored();
    }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        if (!canReceive()) {
            return 0L;
        }

        long consumption = getConsumption();
        long maxEnergy = getMaxEnergyStored();
        long canAccept = Math.min(maxEnergy - energyStored, Math.min(maxReceive, getReceiveSpeed()));

        if (canAccept <= 0L) {
            return 0L;
        }

        if (!simulate) {
            long currentTick = level != null ? level.getGameTime() : -1L;

            // Пасивне споживання енергії за минулі такти
            if (lastReceivedTick >= 0L && currentTick > lastReceivedTick) {
                long elapsed = currentTick - lastReceivedTick;
                energyStored = Math.max(0L, energyStored - (elapsed * consumption));
            }

            energyStored = Math.min(maxEnergy, energyStored + canAccept);
            lastReceivedTick = currentTick;
            setChanged();

            // Зміна стану світла виконується ЛИШЕ якщо лампа була вимкнена,
            // щоб уникнути високочастотних оновлень блоку щотика
            BlockState state = getBlockState();
            if (state.getBlock() instanceof ElectricLampBlock lampBlock) {
                boolean wasLit = state.getValue(ElectricLampBlock.LIT);
                boolean currentBroken = state.getValue(ElectricLampBlock.BROKEN) || this.broken;
                if (!wasLit && !currentBroken && energyStored >= consumption) {
                    level.setBlock(worldPosition, state.setValue(ElectricLampBlock.LIT, true), Block.UPDATE_CLIENTS);
                    level.scheduleTick(worldPosition, lampBlock, 20);
                }
            }
        }

        return canAccept;
    }

    // ==================== IEnergyConnector ====================

    @Override
    public boolean canConnectEnergy(@Nullable Direction side) {
        if (side == null) {
            return true;
        }
        BlockState state = getBlockState();
        if (state.hasProperty(BlockStateProperties.FACING)) {
            // Кабель не може підключатися з лицьового боку колби/патрона
            return side != state.getValue(BlockStateProperties.FACING);
        }
        return true;
    }

    @Nullable
    public IEnergyReceiver getEnergyReceiver(@Nullable Direction side) {
        return canConnectEnergy(side) ? this : null;
    }

    @Nullable
    public IEnergyConnector getEnergyConnector(@Nullable Direction side) {
        return canConnectEnergy(side) ? this : null;
    }

    // ==================== LIT STATE & TICKING ====================

    public void updateLitState() {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof ElectricLampBlock lampBlock)) return;

        boolean wasLit = state.getValue(ElectricLampBlock.LIT);
        boolean currentBroken = state.getValue(ElectricLampBlock.BROKEN) || this.broken;

        long consumption = lampBlock.getConsumptionPerTick();
        boolean shouldBeLit = !currentBroken && consumption > 0L && energyStored >= consumption;

        // Перемикаємо BlockState ЛИШЕ якщо фундаментальний статус дійсно змінився
        if (wasLit != shouldBeLit) {
            level.setBlock(worldPosition, state.setValue(ElectricLampBlock.LIT, shouldBeLit), Block.UPDATE_CLIENTS);
            if (shouldBeLit) {
                level.scheduleTick(worldPosition, lampBlock, 20);
            }
        }
    }

    public void onScheduledTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ElectricLampBlock lampBlock)) return;

        long currentTick = level.getGameTime();
        long consumption = getConsumption();

        if (lastReceivedTick >= 0L && currentTick > lastReceivedTick) {
            long elapsed = currentTick - lastReceivedTick;
            energyStored = Math.max(0L, energyStored - (elapsed * consumption));
            lastReceivedTick = currentTick;
            setChanged();
        }

        boolean wasLit = state.getValue(ElectricLampBlock.LIT);
        boolean currentBroken = state.getValue(ElectricLampBlock.BROKEN) || this.broken;

        boolean shouldBeLit = !currentBroken && consumption > 0L && energyStored >= consumption;

        if (wasLit != shouldBeLit) {
            level.setBlock(pos, state.setValue(ElectricLampBlock.LIT, shouldBeLit), Block.UPDATE_CLIENTS);
        }

        // Тіки продовжуються ТІЛЬКИ якщо лампа має живлення і горить
        if (shouldBeLit) {
            level.scheduleTick(pos, lampBlock, 20);
        }
    }

    // ==================== LIFECYCLE & NETWORK ====================

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            EnergyNetworkManager.get(serverLevel).addNode(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            EnergyNetworkManager.get(serverLevel).removeNode(worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel serverLevel) {
            EnergyNetworkManager.get(serverLevel).removeNode(worldPosition);
        }
    }

    // ==================== NBT PERSISTENCE ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putLong("Energy", energyStored);
        tag.putLong("LastReceivedTick", lastReceivedTick);
        tag.putFloat("Durability", durability);
        tag.putBoolean("Broken", broken);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        energyStored = tag.getLong("Energy");
        lastReceivedTick = tag.getLong("LastReceivedTick");
        durability = tag.contains("Durability") ? tag.getFloat("Durability") : 1.0f;
        broken = tag.getBoolean("Broken");
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        LampComponent comp = input.get(ModDataComponents.LAMP_DATA.get());
        if (comp != null) {
            this.durability = comp.durability();
            this.broken = comp.broken();
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(ModDataComponents.LAMP_DATA.get(), new LampComponent(this.durability, this.broken));
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, provider);
        return tag;
    }
}
