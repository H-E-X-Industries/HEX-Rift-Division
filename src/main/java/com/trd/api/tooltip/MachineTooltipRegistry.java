package com.trd.api.tooltip;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class MachineTooltipRegistry {
    private static final List<Entry> ENTRIES = new ArrayList<>();

    /**
     * Предмет резолвится лениво: блоки и их BlockItem на момент регистрации
     * ещё не созданы (реестры наполняются после статических инициализаторов).
     */
    private record Entry(Supplier<Item> item, String descKey) {}

    /** Для предметов (в т.ч. BlockItem с собственным DeferredHolder) */
    public static void register(DeferredHolder<Item, ? extends Item> item, String descTranslationKey) {
        ENTRIES.add(new Entry(item::get, descTranslationKey));
    }

    /** Для блоков, у которых нет отдельного предмета (автоматически подхватит asItem) */
    public static void registerBlock(DeferredHolder<Block, ? extends Block> block, String descTranslationKey) {
        ENTRIES.add(new Entry(() -> block.get().asItem(), descTranslationKey));
    }

    public static String getDescKey(Item item) {
        for (Entry entry : ENTRIES) {
            if (entry.item().get() == item) {
                return entry.descKey;
            }
        }
        return null;
    }

    public static boolean has(Item item) {
        return getDescKey(item) != null;
    }
}
