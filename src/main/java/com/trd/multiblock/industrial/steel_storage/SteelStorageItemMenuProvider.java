package com.trd.multiblock.industrial.steel_storage;

import com.trd.menu.industrial.SteelStorageMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Открытие стального хранилища из предмета: {@link MenuProvider} без позиции блока,
 * поэтому клиент создаёт меню с пустым буфером и наполняет его содержимым из пакета.
 */
public class SteelStorageItemMenuProvider implements MenuProvider {

    private final SteelStorageItemInventory inventory;

    public SteelStorageItemMenuProvider(Player player, ItemStack stack) {
        this.inventory = new SteelStorageItemInventory(stack, player.level().registryAccess());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.trd.steel_storage");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SteelStorageMenu(id, inv, inventory.handler(), inventory::save);
    }
}
