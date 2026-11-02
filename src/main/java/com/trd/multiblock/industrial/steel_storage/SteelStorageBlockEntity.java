package com.trd.multiblock.industrial.steel_storage;

import com.trd.block.entity.ModBlockEntities;
import com.trd.menu.industrial.SteelStorageMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SteelStorageBlockEntity extends BlockEntity implements MenuProvider {

    public static final int ROWS = 7;
    public static final int COLS = 13;
    public static final int SLOTS = ROWS * COLS; // 91

    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
            }
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (!isItemAllowed(stack)) return false;
            return super.isItemValid(slot, stack);
        }
    };

    /**
     * Что вообще можно класть внутрь: не шалкеры и не другие стальные хранилища.
     * <p>
     * Проверка вынесена отдельно, потому что её используют оба варианта хранилища —
     * и блок, и предмет (см. {@link SteelStorageItemInventory}). Без неё предмет-хранилище
     * можно было бы положить в самого себя, а внутри — в третий, и так до дыры в стеке.
     */
    public static boolean isItemAllowed(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (stack.getItem() instanceof BlockItem bi) {
            Block block = bi.getBlock();
            if (block instanceof ShulkerBoxBlock) return false;
            if (block instanceof SteelStorageBlock) return false;
        }
        return true;
    }

    public SteelStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STEEL_STORAGE_BE.get(), pos, state);
    }

    /**
     * Индекс извлекателя, чья сейчас очередь работать (0 или 1).
     * Используется только когда к хранилищу подключено ровно 2 извлекателя:
     * тогда они тасуют работу поочерёдно — один вынул 3, потом второй вынул 3.
     * При 1 или 3+ извлекателях значение игнорируется (условие определяется динамически).
     */
    private int extractorTurn = 0;

    /**
     * Все подключённые к этому хранилищу извлекатели (Mode.EXTRACTOR),
     * отсортированные по {@link BlockPos#asLong()} — даёт стабильный порядок
     * независимо от направления сканирования (Direction.values() — не отсортирован).
     */
    public java.util.List<BlockPos> getConnectedExtractors() {
        java.util.List<BlockPos> result = new java.util.ArrayList<>();
        if (level == null) return result;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            BlockPos p = worldPosition.relative(dir);
            if (level.getBlockEntity(p) instanceof com.trd.block.entity.industrial.conveyors.ConveyorBufferBlockEntity buf
                    && buf.getMode() == com.trd.block.entity.industrial.conveyors.ConveyorBufferBlockEntity.Mode.EXTRACTOR) {
                result.add(p);
            }
        }
        result.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
        return result;
    }

    /** Чередование работает только при ровно двух подключённых извлекателях. */
    public boolean isExtractorTurn(int extractorIndex) {
        return extractorIndex == extractorTurn;
    }

    /** Передаёт ход следующему по порядку извлекателю (циклично 0 ↔ 1). */
    public void advanceExtractorTurn() {
        extractorTurn = (extractorTurn + 1) % 2;
        setChanged();
    }


    public boolean isEmpty() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void dropContents() {
        if (level != null && !level.isClientSide) {
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("ExtractorTurn", extractorTurn);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            CompoundTag invTag = tag.getCompound("Inventory");
            if (invTag.contains("Size", Tag.TAG_INT)) {
                int oldSize = invTag.getInt("Size");
                if (oldSize < SLOTS) {
                    invTag.putInt("Size", SLOTS);
                }
            } else {
                invTag.putInt("Size", SLOTS);
            }
            inventory.deserializeNBT(registries, invTag);
        }
        if (tag.contains("ExtractorTurn")) {
            extractorTurn = tag.getInt("ExtractorTurn") % 2;
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.trd.steel_storage");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SteelStorageMenu(id, inv, this);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }
}
