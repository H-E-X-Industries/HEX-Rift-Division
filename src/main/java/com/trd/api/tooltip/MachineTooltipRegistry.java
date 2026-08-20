package com.trd.api.tooltip;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public class MachineTooltipRegistry {
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private record Entry(RegistryObject<?> registryObject, boolean isBlock, String descKey) {}

    /** Для предметов (в т.ч. BlockItem с собственным RegistryObject) */
    public static void register(RegistryObject<? extends Item> item, String descTranslationKey) {
        ENTRIES.add(new Entry(item, false, descTranslationKey));
    }

    /** Для блоков, у которых нет отдельного RegistryObject<Item> (автоматически подхватит asItem) */
    public static void registerBlock(RegistryObject<? extends Block> block, String descTranslationKey) {
        ENTRIES.add(new Entry(block, true, descTranslationKey));
    }

    public static String getDescKey(Item item) {
        for (Entry entry : ENTRIES) {
            Item entryItem;
            if (entry.isBlock) {
                @SuppressWarnings("unchecked")
                RegistryObject<Block> blockObj = (RegistryObject<Block>) entry.registryObject;
                entryItem = blockObj.get().asItem();
            } else {
                @SuppressWarnings("unchecked")
                RegistryObject<Item> itemObj = (RegistryObject<Item>) entry.registryObject;
                entryItem = itemObj.get();
            }
            if (entryItem == item) {
                return entry.descKey;
            }
        }
        return null;
    }

    public static boolean has(Item item) {
        return getDescKey(item) != null;
    }
}