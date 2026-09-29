package com.trd.block.entity.weapons;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import com.trd.item.industrial.energy.EnergyCellItem;
import com.trd.item.industrial.energy.ModBatteryItem;
import com.trd.item.weapons.ammo.IAmmoItem;
import com.trd.item.weapons.turrets.TurretChipItem;

/**
 * Слоты блочной турели: 0-8 патроны, 9 чип, 10 батарейка.
 *
 * <p>Сериализацию намеренно НЕ переопределяем: в 1.20.1 здесь был свой
 * {@code serializeNBT}/{@code deserializeNBT} на {@code ItemStack#save}/{@code #of},
 * которых в 1.21.1 больше нет. Родительский {@link ItemStackHandler} сам кодирует
 * стопки через item-кодек и требует {@code HolderLookup.Provider}, так что BlockEntity
 * вызывает {@code inventory.serializeNBT(provider)} сам — как во всех остальных
 * портированных контейнерах.
 */
public class TurretAmmoContainer extends ItemStackHandler {

    private static final int SLOT_COUNT = 11;
    private Runnable onContentsChanged;

    public TurretAmmoContainer() {
        super(SLOT_COUNT);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        // Слот 9 — чип
        if (slot == 9) {
            return stack.getItem() instanceof TurretChipItem;
        }
        // [ФИКС] Слот 10 — только батарейки / предметы с энергией
        if (slot == 10) {
            return stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM) != null
                    || stack.getItem() instanceof ModBatteryItem
                    || stack.getItem() instanceof EnergyCellItem;
        }
        // Слоты 0-8 — только патроны
        return stack.getItem() instanceof IAmmoItem;
    }

    public void setOnContentsChanged(Runnable callback) {
        this.onContentsChanged = callback;
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (onContentsChanged != null) {
            onContentsChanged.run();
        }
    }

    /**
     * В NeoForge {@code onContentsChanged} protected, а слотам меню нужно дёргать его
     * из анонимного подкласса {@code SlotItemHandler} — поэтому наружу.
     */
    public void notifySlotChanged(int slot) {
        onContentsChanged(slot);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    /**
     * Проверяет наличие патрона нужного калибра, но НЕ забирает его.
     */
    public IAmmoItem peekAmmo(String caliber) {
        for (int i = 0; i < 9; i++) { // [ИЗМЕНЕНО] Только слоты патронов 0-8
            ItemStack stack = getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof IAmmoItem ammo) {
                if (ammo.getCaliber().equals(caliber)) {
                    return ammo;
                }
            }
        }
        return null;
    }

    public IAmmoItem takeAmmoAndGet(String caliber) {
        for (int i = 0; i < 9; i++) { // [ИЗМЕНЕНО] Только слоты патронов 0-8
            ItemStack stack = getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof IAmmoItem ammo) {
                if (ammo.getCaliber().equals(caliber)) {
                    stack.shrink(1);
                    return ammo;
                }
            }
        }
        return null;
    }

    public int countAmmo(String caliber) {
        int count = 0;
        for (int i = 0; i < 9; i++) { // [ИЗМЕНЕНО] Только слоты патронов 0-8
            ItemStack stack = getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof IAmmoItem ammo) {
                if (ammo.getCaliber().equals(caliber)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    public boolean takeAmmo(String caliber) {
        for (int i = 0; i < 9; i++) { // [ИЗМЕНЕНО] Только слоты патронов 0-8
            ItemStack stack = getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof IAmmoItem ammo) {
                if (ammo.getCaliber().equals(caliber)) {
                    stack.shrink(1);
                    return true;
                }
            }
        }
        return false;
    }
}
