package com.trd.menu.industrial;

import com.trd.block.entity.industrial.conveyors.SortirovshikBlockEntity;
import com.trd.menu.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Меню сортировщика.
 * Фантомные слоты фильтров НЕ регистрируются как обычные слоты — они рисуются и
 * обрабатываются экраном вручную (клики уходят пакетами на сервер), чтобы ванильная
 * логика кликов не трогала фантомные предметы.
 */
public class SortirovshikMenu extends AbstractContainerMenu {
    public final SortirovshikBlockEntity blockEntity;
    private final Level level;

    // Серверный конструктор
    public SortirovshikMenu(int id, Inventory playerInv, SortirovshikBlockEntity be) {
        super(ModMenuTypes.SORTIROVSHIK_MENU.get(), id);
        this.blockEntity = be;
        this.level = playerInv.player.level();

        addPlayerInventory(playerInv);
        addPlayerHotbar(playerInv);
    }

    // Клиентский конструктор
    public SortirovshikMenu(int id, Inventory playerInv, FriendlyByteBuf extraData) {
        this(id, playerInv, (SortirovshikBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public int getMode(int section) {
        return blockEntity.getMode(section);
    }

    public ItemStack getFilter(int index) {
        return blockEntity.getFilter(index);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return ContainerLevelAccess.create(level, blockEntity.getBlockPos()).evaluate((lvl, pos) -> {
            Block block = lvl.getBlockState(pos).getBlock();
            if (!(block instanceof com.trd.block.basic.industrial.SortirovshikBlock)) {
                return false;
            }
            return player.distanceToSqr((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D) <= 64.0D;
        }, true);
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 12 + l * 18, 89 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 12 + i * 18, 147));
        }
    }
}
