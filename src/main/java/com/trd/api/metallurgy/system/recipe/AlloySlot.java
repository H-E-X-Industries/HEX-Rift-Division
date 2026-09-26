package com.trd.api.metallurgy.system.recipe;

import net.minecraft.world.item.Item;

/**
 * Требование к одному слоту рецепта сплава.
 * Поддерживает альтернативные предметы (любой из списка подходит).
 */
public class AlloySlot {
    private final Item[] items;
    private final int count;

    public static final AlloySlot EMPTY = new AlloySlot((Item) null, 0);

    /** Слот с одним требуемым предметом (null = слот должен быть пуст) */
    public AlloySlot(Item item, int count) {
        this(item == null ? new Item[0] : new Item[]{item}, count);
    }

    /** Слот с альтернативами: подойдёт любой из перечисленных предметов */
    public AlloySlot(int count, Item... alternatives) {
        this(alternatives == null || alternatives.length == 0 ? new Item[0] : alternatives.clone(), count);
    }

    private AlloySlot(Item[] items, int count) {
        this.items = items;
        this.count = count;
    }

    /** Основной (первый) допустимый предмет или null, если слот должен быть пуст */
    public Item item() {
        return items.length > 0 ? items[0] : null;
    }

    public Item[] items() {
        return items.clone();
    }

    public int count() {
        return count;
    }

    /** Проверяет, подходит ли переданный предмет для этого слота */
    public boolean accepts(Item candidate) {
        if (items.length == 0) return candidate == null;
        for (Item it : items) {
            if (it == candidate) return true;
        }
        return false;
    }
}
