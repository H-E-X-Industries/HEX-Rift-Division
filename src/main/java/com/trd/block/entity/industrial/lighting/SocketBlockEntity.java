package com.trd.block.entity.industrial.lighting;

import com.trd.api.components.LampComponent;
import com.trd.api.components.ModDataComponents;
import com.trd.api.energy.EnergyNetworkManager;
import com.trd.api.energy.IEnergyConnector;
import com.trd.api.energy.IEnergyReceiver;
import com.trd.block.basic.industrial.lighting.ElectricLampBlock;
import com.trd.block.basic.industrial.lighting.FluorescentLampBlock;
import com.trd.block.basic.industrial.lighting.LedLampBlock;
import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Енергетичний BlockEntity для патронів та світильників.
 * Підтримує фіксацію єдиного порту живлення, повну відсутність буфера накопичення
 * (миттєва реакція на розрив мережі) та швидке динамічне згасання (4–6 тіків).
 */
public class SocketBlockEntity extends BlockEntity implements IEnergyReceiver, IEnergyConnector {

    private long energyStored = 0L;
    private long lastReceivedTick = -1L;
    private float durability = 1.0f;
    private boolean broken = false;

    @Nullable
    private Direction activeConnectionSide = null;

    private int currentLightLevel = 0;
    private int warmupTicks = 0;
    private int cooldownTicks = 0;
    private int flickerTicks = 0;

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

    public int getCurrentLightLevel() {
        return currentLightLevel;
    }

    // ==================== SINGLE PORT CONNECTION ====================

    public Direction getActiveConnectionSide() {
        if (activeConnectionSide != null) {
            return activeConnectionSide;
        }
        BlockState state = getBlockState();
        Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.UP;

        if (facing != Direction.DOWN) {
            return Direction.DOWN;
        } else {
            return Direction.UP;
        }
    }

    public void setActiveConnectionSide(Direction side) {
        if (this.activeConnectionSide != side) {
            this.activeConnectionSide = side;
            setChanged();
            if (level instanceof ServerLevel serverLevel) {
                EnergyNetworkManager.get(serverLevel).removeNode(worldPosition);
                EnergyNetworkManager.get(serverLevel).addNode(worldPosition);
                serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    // ==================== IEnergyReceiver ====================

    @Override
    public long getEnergyStored() {
        return energyStored;
    }

    @Override
    public long getMaxEnergyStored() {
        // Ємність буфера суворо дорівнює споживанню за 1 такт (без акумуляції батареї)
        return Math.max(1L, getConsumption());
    }

    @Override
    public void setEnergyStored(long energy) {
        this.energyStored = Math.max(0L, Math.min(energy, getMaxEnergyStored()));
        setChanged();
    }

    @Override
    public long getReceiveSpeed() {
        return Math.max(1L, getConsumption());
    }

    @Override
    public Priority getPriority() {
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

        long maxEnergy = getMaxEnergyStored();
        long canAccept = Math.min(maxEnergy - energyStored, Math.min(maxReceive, getReceiveSpeed()));

        if (canAccept <= 0L) {
            return 0L;
        }

        if (!simulate) {
            energyStored = Math.min(maxEnergy, energyStored + canAccept);
            lastReceivedTick = level != null ? level.getGameTime() : -1L;
            setChanged();
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
            if (side == state.getValue(BlockStateProperties.FACING)) {
                return false;
            }
        }
        return side == getActiveConnectionSide();
    }

    @Nullable
    public IEnergyReceiver getEnergyReceiver(@Nullable Direction side) {
        return canConnectEnergy(side) ? this : null;
    }

    @Nullable
    public IEnergyConnector getEnergyConnector(@Nullable Direction side) {
        return canConnectEnergy(side) ? this : null;
    }

    // ==================== TICKER & THERMAL INERTIA ====================

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ElectricLampBlock lampBlock)) {
            return;
        }

        long consumption = lampBlock.getConsumptionPerTick();
        boolean isBrokenState = this.broken || (state.hasProperty(ElectricLampBlock.BROKEN) && state.getValue(ElectricLampBlock.BROKEN));
        boolean hasPower = !isBrokenState && consumption > 0L && this.energyStored >= consumption;

        // Повне видалення накопичувального буфера:
        // Енергія скидається щотакту, тому розрив дроту відразу припиняє живлення без затримки
        this.energyStored = 0L;
        setChanged();

        int maxLight = lampBlock.getMaxLightLevel();
        int targetLight = 0;

        if (state.getBlock() instanceof LedLampBlock) {
            // Світлодіодна (LED): миттєве включення та вимкнення (0 <-> 15)
            targetLight = hasPower ? maxLight : 0;
            this.warmupTicks = 0;
            this.cooldownTicks = 0;
            this.flickerTicks = 0;
            this.currentLightLevel = targetLight;
        } else if (state.getBlock() instanceof FluorescentLampBlock) {
            // Люмінесцентна (Fluorescent / CFL): стартерне мерехтіння на 1-му та 3-му тіках, миттєве вимкнення
            if (hasPower) {
                this.cooldownTicks = 0;
                this.warmupTicks = 0;
                this.flickerTicks++;
                if (this.flickerTicks == 1) {
                    targetLight = maxLight;
                    level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.8f);
                } else if (this.flickerTicks == 2) {
                    targetLight = 0;
                } else if (this.flickerTicks == 3) {
                    targetLight = maxLight;
                    level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.9f);
                } else if (this.flickerTicks == 4) {
                    targetLight = 0;
                } else {
                    targetLight = maxLight;
                }
                this.currentLightLevel = targetLight;
            } else {
                this.flickerTicks = 0;
                this.warmupTicks = 0;
                this.cooldownTicks = 0;
                this.currentLightLevel = 0;
            }
        } else {
            // Лампи розжарення (Carbon, Tungsten): розпал 2-3 тіки, динамічне остигання 4–6 тіків
            // (15 -> 10 -> 5 -> 0, вольфрамова: 12 -> 8 -> 4 -> 0, вугільна: 8 -> 5 -> 2 -> 0)
            if (hasPower) {
                this.cooldownTicks = 0;
                this.flickerTicks = 0;
                this.warmupTicks++;
                if (this.warmupTicks == 1) {
                    this.currentLightLevel = Math.max(1, (maxLight + 1) / 3);
                } else if (this.warmupTicks == 2) {
                    this.currentLightLevel = Math.max(1, (maxLight * 2) / 3);
                } else {
                    this.currentLightLevel = maxLight;
                }
            } else {
                this.warmupTicks = 0;
                this.flickerTicks = 0;
                if (this.currentLightLevel > 0) {
                    this.cooldownTicks++;
                    if (this.cooldownTicks % 2 == 0) {
                        int step = Math.max(1, (maxLight + 2) / 3);
                        this.currentLightLevel = Math.max(0, this.currentLightLevel - step);
                    }
                    if (this.cooldownTicks >= 6) {
                        this.currentLightLevel = 0;
                    }
                } else {
                    this.cooldownTicks = 0;
                }
            }
        }

        // Оновлення BlockState (LIT і LIGHT_LEVEL) лише за реальної зміни значень
        boolean shouldBeLit = this.currentLightLevel > 0;
        boolean wasLit = state.getValue(ElectricLampBlock.LIT);
        int curLevel = state.hasProperty(ElectricLampBlock.LIGHT_LEVEL) ? state.getValue(ElectricLampBlock.LIGHT_LEVEL) : 0;

        if (wasLit != shouldBeLit || curLevel != this.currentLightLevel) {
            level.setBlock(pos, state.setValue(ElectricLampBlock.LIT, shouldBeLit)
                            .setValue(ElectricLampBlock.LIGHT_LEVEL, this.currentLightLevel),
                    Block.UPDATE_CLIENTS);
        }
    }

    public void updateLitState() {
        if (level instanceof ServerLevel serverLevel) {
            serverTick(serverLevel, worldPosition, getBlockState());
        }
    }

    public void onScheduledTick(ServerLevel level, BlockPos pos, BlockState state) {
        serverTick(level, pos, state);
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
        if (activeConnectionSide != null) {
            tag.putString("ActiveConnectionSide", activeConnectionSide.getName());
        }
        tag.putInt("LightLevel", currentLightLevel);
        tag.putInt("WarmupTicks", warmupTicks);
        tag.putInt("CooldownTicks", cooldownTicks);
        tag.putInt("FlickerTicks", flickerTicks);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        energyStored = tag.getLong("Energy");
        lastReceivedTick = tag.getLong("LastReceivedTick");
        durability = tag.contains("Durability") ? tag.getFloat("Durability") : 1.0f;
        broken = tag.getBoolean("Broken");
        if (tag.contains("ActiveConnectionSide")) {
            activeConnectionSide = Direction.byName(tag.getString("ActiveConnectionSide"));
        } else {
            activeConnectionSide = null;
        }
        currentLightLevel = tag.getInt("LightLevel");
        warmupTicks = tag.getInt("WarmupTicks");
        cooldownTicks = tag.getInt("CooldownTicks");
        flickerTicks = tag.getInt("FlickerTicks");
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
