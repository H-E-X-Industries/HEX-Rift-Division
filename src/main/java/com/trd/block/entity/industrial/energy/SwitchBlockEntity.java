package com.trd.block.entity.industrial.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import com.trd.api.energy.EnergyNetworkManager;
import com.trd.api.energy.IEnergyConnector;
import com.trd.block.basic.industrial.energy.SwitchBlock;
import com.trd.block.entity.ModBlockEntities;
import com.trd.capability.ModCapabilities;

import javax.annotation.Nullable;

public class SwitchBlockEntity extends BlockEntity implements IEnergyConnector {

    private final LazyOptional<IEnergyConnector> hbmConnector = LazyOptional.of(() -> this);

    /**
     * Предыдущий УРОВЕНЬ редстоун-сигнала (не "фронт").
     * Инициализируется фактическим значением при установке и загрузке чанка —
     * иначе фронты начинают теряться или срабатывать ложно.
     * В NBT пишется под старым ключом "isTriggered" для совместимости миров.
     */
    public boolean prevSignal = false;

    public SwitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SWITCH_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SwitchBlockEntity entity) {
        if (level.isClientSide) return;

        if (state.getValue(SwitchBlock.POWERED)) {
            // Самолечение: узел должен существовать И иметь сеть. Просто hasNode()
            // пропускал "залипшие" узлы без сети — их приходилось лечить перестановкой блока.
            EnergyNetworkManager.get((ServerLevel) level).ensureNodeConnected(pos);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Синхронизируем предыдущий уровень сигнала с фактическим: источник мог
        // измениться, пока чанк был выгружен (иначе первый фронт теряется).
        if (this.level != null && !this.level.isClientSide) {
            this.prevSignal = this.level.hasNeighborSignal(this.getBlockPos());
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ModCapabilities.ENERGY_CONNECTOR) {
            if (isValidSide(side)) {
                return hbmConnector.cast();
            }
        }
        return super.getCapability(cap, side);
    }

    private boolean isValidSide(@Nullable Direction side) {
        BlockState state = this.getBlockState();
        if (!(state.getBlock() instanceof SwitchBlock)) return false;
        if (side == null) return true;

        Direction facing = state.getValue(SwitchBlock.FACING);
        if (side.getAxis() == Direction.Axis.Y) return false;
        return side != facing && side != facing.getOpposite();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        hbmConnector.invalidate();
    }

    @Override
    public boolean canConnectEnergy(Direction side) {
        return isValidSide(side);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide) {
            EnergyNetworkManager.get((ServerLevel) this.level).removeNode(this.getBlockPos());
        }
        hbmConnector.invalidate();
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("isTriggered", prevSignal);
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        prevSignal = tag.getBoolean("isTriggered");
    }
}
