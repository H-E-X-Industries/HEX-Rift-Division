package com.trd.multiblock.industrial.steel_storage;

import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Хранилище стального блока в виде предмета: содержимое лежит прямо в NBT стака,
 * в том же формате, что и у стоящего блока ({@code BLOCK_ENTITY_DATA.Inventory}),
 * поэтому подсказка и установка блока читают те же данные без конвертации.
 * <p>
 * Любое изменение слота сразу записывается обратно в предмет — иначе при закрытии
 * экрана по «К» часть вещей потерялась бы.
 */
public class SteelStorageItemInventory {

    private final ItemStack stack;
    private final HolderLookup.Provider registries;
    private final ItemStackHandler handler;
    /** Пока идёт чтение NBT, onContentsChanged писать в предмет нельзя. */
    private boolean loading = true;

    public SteelStorageItemInventory(ItemStack stack, HolderLookup.Provider registries) {
        this.stack = stack;
        this.registries = registries;
        this.handler = new ItemStackHandler(SteelStorageBlockEntity.SLOTS) {
            @Override
            protected void onContentsChanged(int slot) {
                if (!loading) {
                    save();
                }
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                // Тот же запрет, что и у стоящего блока: шалкер внутрь не кладём,
                // и само хранилище тоже (иначе предмет ложится в сам себя).
                if (!SteelStorageBlockEntity.isItemAllowed(stack)) return false;
                return super.isItemValid(slot, stack);
            }

            @Override
            public void setStackInSlot(int slot, ItemStack stack) {
                // Последний рубеж: слоты и меню проверяют предмет сами, а сюда
                // можно достучаться напрямую (автоматизация, capability). Правило
                // не применяется только при чтении старого NBT — иначе невалидный
                // предмет просто потерялся бы при загрузке мира.
                if (!loading && !SteelStorageBlockEntity.isItemAllowed(stack)) return;
                super.setStackInSlot(slot, stack);
            }
        };

        CompoundTag tag = getStoredTag(stack);
        if (tag != null) {
            CompoundTag invTag = tag.getCompound("Inventory");
            if (!invTag.contains("Size", Tag.TAG_INT) || invTag.getInt("Size") < SteelStorageBlockEntity.SLOTS) {
                invTag.putInt("Size", SteelStorageBlockEntity.SLOTS);
            }
            handler.deserializeNBT(registries, invTag);
        }
        loading = false;
    }

    public ItemStackHandler handler() {
        return handler;
    }

    /** Принудительно записывает содержимое в предмет (например, при закрытии меню). */
    public void save() {
        CompoundTag tag = new CompoundTag();
        // Пустое хранилище держим без NBT: зачем тащить в каждом предмете пустую
        // сетку на 91 слот, если подсказка и так умеет говорить «пусто».
        if (!isEmpty()) {
            tag.put("Inventory", handler.serializeNBT(registries));
        }
        // Именно BlockItem.setBlockEntityData, а не CustomData.of: у BLOCK_ENTITY_DATA
        // кодировщик строгий (CustomData.CODEC_WITH_ID) и требует строку "id". Без неё
        // падает и синхронизация пакета, и сохранение мира. setBlockEntityData сам
        // проставляет id и снимает компонент, если тег пуст.
        BlockItem.setBlockEntityData(stack, ModBlockEntities.STEEL_STORAGE_BE.get(), tag);
    }

    private boolean isEmpty() {
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Достаёт вложенный тег хранилища из предмета, если он там есть. */
    private static CompoundTag getStoredTag(ItemStack stack) {
        CustomData beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (beData != null && !beData.isEmpty()) {
            CompoundTag tag = beData.copyTag();
            if (tag.contains("Inventory")) {
                return tag;
            }
        }
        // Старый формат: всё лежало внутри CustomData -> BlockEntityTag
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag cTag = customData.copyTag();
            if (cTag.contains("BlockEntityTag")) {
                CompoundTag beTag = cTag.getCompound("BlockEntityTag");
                if (beTag.contains("Inventory")) {
                    return beTag;
                }
            }
        }
        return null;
    }
}
