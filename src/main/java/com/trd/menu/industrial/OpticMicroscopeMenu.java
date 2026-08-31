package com.trd.menu.industrial;

import com.trd.block.basic.industrial.OpticMicroscopeBlock;
import com.trd.block.entity.industrial.OpticMicroscopeBlockEntity;
import com.trd.item.ModItems;
import com.trd.menu.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.SlotItemHandler;

public class OpticMicroscopeMenu extends AbstractContainerMenu {

    public final OpticMicroscopeBlockEntity blockEntity;
    private final ContainerData data;
    private final Level level;

    public OpticMicroscopeMenu(int containerId, Inventory inv, OpticMicroscopeBlockEntity be, ContainerData data) {
        super(ModMenuTypes.OPTIC_MICROSCOPE_MENU.get(), containerId);
        checkContainerDataCount(data, 4);

        this.blockEntity = be;
        this.data = data;
        this.level = inv.player.level();
        addDataSlots(data);

        // 0 — вход куска (63, 20)
        this.addSlot(new SlotItemHandler(be.getItemHandler(), 0, 63, 20));
        // 1 — выход куска (97, 20)
        this.addSlot(new SlotItemHandler(be.getItemHandler(), 1, 97, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }
        });
        // 2 — вход пипетки (70, 52)
        this.addSlot(new SlotItemHandler(be.getItemHandler(), 2, 70, 52) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.PIPETTE.get()) || stack.is(ModItems.PIPETTE_IDUSTRIAL.get());
            }
        });
        // 3 — выход пипетки (52, 52)
        this.addSlot(new SlotItemHandler(be.getItemHandler(), 3, 52, 52) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }
        });

        // Инвентарь игрока на 8-92
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 92 + row * 18));
            }
        }
        // Hotbar (8, 150)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 150));
        }
    }

    public OpticMicroscopeMenu(int containerId, Inventory inv, FriendlyByteBuf buf) {
        this(containerId, inv,
                (OpticMicroscopeBlockEntity) inv.player.level().getBlockEntity(buf.readBlockPos()),
                new SimpleContainerData(4));
    }

    /** Текущее количество жидкости в буфере (синхронизируется через data-слоты). */
    public FluidStack getFluid() {
        int amount = data.get(2);
        if (amount <= 0) return FluidStack.EMPTY;
        return new FluidStack(com.trd.api.fluids.ModFluids.SULFURIC_ACID_SOURCE.get(), amount);
    }

    public int getCapacity() {
        return data.get(3);
    }

    public int getFluidAmount() {
        return data.get(2);
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getMaxProgress() {
        return data.get(1);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index < 4) {
            // Из машины в инвентарь игрока
            if (!moveItemStackTo(stack, 4, 40, true)) return ItemStack.EMPTY;
        } else {
            // Из инвентаря в машину
            if (stack.is(ModItems.PIPETTE.get()) || stack.is(ModItems.PIPETTE_IDUSTRIAL.get())) {
                if (!moveItemStackTo(stack, 2, 3, false)) return ItemStack.EMPTY;
            } else if (stack.is(ModItems.CONGLOMERATE_CHUNK.get())) {
                if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return ContainerLevelAccess.create(level, blockEntity.getBlockPos()).evaluate((lvl, pos) ->
                lvl.getBlockState(pos).getBlock() instanceof OpticMicroscopeBlock
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0, true);
    }
}
