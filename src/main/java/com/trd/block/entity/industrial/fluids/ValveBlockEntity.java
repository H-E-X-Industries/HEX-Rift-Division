package com.trd.block.entity.industrial.fluids;

import com.trd.api.fluids.system.FluidNetworkManager;
import com.trd.block.basic.industrial.fluids.ValveBlock;
import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import javax.annotation.Nullable;

/**
 * BlockEntity клапана.
 *
 * Наследуется от {@link FluidPipeBlockEntity}, потому что жидкостная сеть
 * (FluidNetworkManager.canConnectLogically) соединяет узлы только если ОБА
 * являются FluidPipeBlockEntity с одинаковым фильтром. Клапан ведёт себя как
 * труба, но становится узлом сети ТОЛЬКО когда открыт (POWERED == true).
 */
public class ValveBlockEntity extends FluidPipeBlockEntity {

    /**
     * Предыдущий УРОВЕНЬ редстоун-сигнала (не "фронт").
     * Инициализируется фактическим значением при установке и загрузке чанка —
     * иначе фронты начинают теряться или срабатывать ложно.
     * В NBT пишется под старым ключом "isTriggered" для совместимости миров.
     */
    public boolean prevSignal = false;

    public ValveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VALVE_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        // НЕ вызываем super.onLoad(): труба добавляет узел безусловно.
        // Клапан регистрирует узел только если он ОТКРЫТ.
        if (this.level != null && !this.level.isClientSide) {
            BlockState state = getBlockState();
            if (state.getBlock() instanceof ValveBlock && state.getValue(ValveBlock.POWERED)) {
                FluidNetworkManager manager = FluidNetworkManager.get((ServerLevel) this.level);
                manager.ensureNodeConnected(getBlockPos());
            }
            // Синхронизируем предыдущий уровень сигнала с фактическим: источник мог
            // измениться, пока чанк был выгружен (иначе первый фронт теряется).
            this.prevSignal = this.level.hasNeighborSignal(this.getBlockPos());
        }
    }

    /** Тик: если клапан открыт, но узел по какой-то причине отсутствует — восстанавливаем его. */
    public static void tick(Level level, BlockPos pos, BlockState state, ValveBlockEntity be) {
        if (level.isClientSide) return;
        if (state.getBlock() instanceof ValveBlock && state.getValue(ValveBlock.POWERED)) {
            // Самолечение: узел должен существовать И иметь сеть, иначе клапан
            // приходилось "лечить" перестановкой блока.
            FluidNetworkManager.get((ServerLevel) level).ensureNodeConnected(pos);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider); // сохраняет фильтр (FilterFluid) и HasFlowed
        tag.putBoolean("isTriggered", prevSignal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        prevSignal = tag.getBoolean("isTriggered");
    }
}
