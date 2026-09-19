package com.trd.api.energy;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

/**
 * Провайдер Forge Energy для BlockEntity с упаковкой long в два int.
 *
 * ФИШКА: Позволяет работать с long энергией через стандартный Forge Energy API!
 * - Direction.DOWN = старшие биты (HIGH) - для значений выше 2 млрд
 * - Остальные стороны = младшие биты (LOW) - для обычных значений
 *
 * Таким образом другие моды могут взаимодействовать с огромными значениями энергии!
 */
public final class PackedEnergyCapabilityProvider {
    private final IEnergyStorage feLow;
    private final IEnergyStorage feHigh;

    public PackedEnergyCapabilityProvider(IEnergyConnector handler) {
        // LOW биты (0 - 2,147,483,647) - для большинства модов
        this.feLow = new LongEnergyWrapper(handler, LongEnergyWrapper.BitMode.LOW);

        // HIGH биты (множитель 2^32) - для огромных значений
        this.feHigh = new LongEnergyWrapper(handler, LongEnergyWrapper.BitMode.HIGH);
    }

    /**
     * Раздаёт capability в зависимости от стороны:
     * - DOWN = HIGH биты (для работы с большими значениями)
     * - Остальные = LOW биты (стандартная совместимость)
     */
    @Nullable
    public IEnergyStorage getCapability(@Nullable Direction side) {
        return side == Direction.DOWN ? feHigh : feLow;
    }

    /**
     * Получить LOW биты напрямую (для особых случаев)
     */
    public IEnergyStorage getLowBitsCapability() {
        return feLow;
    }

    /**
     * Получить HIGH биты напрямую (для особых случаев)
     */
    public IEnergyStorage getHighBitsCapability() {
        return feHigh;
    }
}
