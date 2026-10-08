package com.trd.api.fuel;

import com.trd.block.basic.ModBlocks;
import com.trd.item.ModItems;
import com.trd.main.MainRegistry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Реестр топлива мода TONG: Rift Division.
 *
 * Для справки: 1 уголь = 1600 тиков (~80 секунд).
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public class ModFuels {

    // --- Регистрация (вызывается в статическом блоке) ---
    public static final Map<DeferredItem<Item>, Integer> ITEM_FUELS = new HashMap<>();
    public static final Map<DeferredBlock<Block>, Integer> BLOCK_FUELS = new HashMap<>();

    static {
        // ============ ПРЕДМЕТЫ ============
        ITEM_FUELS.put(ModItems.LIGNITE, 1100);
        ITEM_FUELS.put(ModItems.WOODEN_HANDLE, 1000);
        ITEM_FUELS.put(ModItems.FUEL_ASH, 200);
        ITEM_FUELS.put(ModItems.ROPE, 200);

        // ============ БЛОКИ ============
        BLOCK_FUELS.put(ModBlocks.LIGNITE_BLOCK, 9900);
        BLOCK_FUELS.put(ModBlocks.SEQUOIA_PLANKS, 300);
        BLOCK_FUELS.put(ModBlocks.SEQUOIA_BARK, 300);
        BLOCK_FUELS.put(ModBlocks.SEQUOIA_HEARTWOOD, 300);
    }

    // --- Быстрый lookup для событий (строится автоматически) ---
    private static volatile Map<Item, Integer> ITEM_LOOKUP;
    private static volatile Map<Item, Integer> BLOCK_AS_ITEM_LOOKUP;
    private static volatile Map<Block, Integer> BLOCK_LOOKUP;

    public static synchronized void init() {
        Map<Item, Integer> itemMap = new HashMap<>();
        for (var entry : ITEM_FUELS.entrySet()) {
            if (entry.getKey() != null && entry.getKey().isBound()) {
                itemMap.put(entry.getKey().get(), entry.getValue());
            }
        }
        ITEM_LOOKUP = Collections.unmodifiableMap(itemMap);

        Map<Item, Integer> blockItemMap = new HashMap<>();
        Map<Block, Integer> blockMap = new HashMap<>();
        for (var entry : BLOCK_FUELS.entrySet()) {
            if (entry.getKey() != null && entry.getKey().isBound()) {
                Block block = entry.getKey().get();
                blockMap.put(block, entry.getValue());
                Item asItem = block.asItem();
                if (asItem != Items.AIR) {
                    blockItemMap.put(asItem, entry.getValue());
                }
            }
        }
        BLOCK_AS_ITEM_LOOKUP = Collections.unmodifiableMap(blockItemMap);
        BLOCK_LOOKUP = Collections.unmodifiableMap(blockMap);
    }

    private static void ensureInitialized() {
        if (ITEM_LOOKUP == null || BLOCK_AS_ITEM_LOOKUP == null || BLOCK_LOOKUP == null) {
            init();
        }
    }

    // ==================== ТАБЛИЦА ВАНИЛЬНЫХ ПРЕДМЕТОВ ====================

    /**
     * Справочная таблица эффективности ванильных предметов.
     *
     * <pre>
     * ┌─────────────────────────────┬──────────┐
     * │ Предмет                     │ Тики     │
     * ├─────────────────────────────┼──────────┤
     * │ LAVA_BUCKET                 │ 20000    │
     * │ BLOCK_OF_COAL               │ 16000    │
     * │ DRIED_KELP_BLOCK            │ 4000     │
     * │ BLAZE_ROD                   │ 2400     │
     * │ COAL / CHARCOAL             │ 1600     │
     * │ BOAT                        │ 1200     │
     * │ SCAFFOLDING                 │ 400      │
     * │ WOODEN_PRESSURE_PLATE       │ 300      │
     * │ WOODEN_BUTTON               │ 100      │
     * │ STICK / SAPLING             │ 100      │
     * │ BOWL                        │ 100      │
     * │ CARPET                      │ 67       │
     * │ BAMBOO                      │ 50       │
     * └─────────────────────────────┴──────────┘
     * </pre>
     */

    // ==================== УТИЛИТЫ ====================

    /**
     * Быстрый lookup для события FurnaceFuelBurnTimeEvent.
     * O(1) вместо O(n).
     *
     * @param item предмет из стака
     * @return тики горения, или -1 если предмет не является топливом мода
     */
    public static int getBurnTimeForItem(Item item) {
        if (item == null) return -1;
        ensureInitialized();

        if (ITEM_LOOKUP != null) {
            Integer time = ITEM_LOOKUP.get(item);
            if (time != null) return time;
        }

        if (BLOCK_AS_ITEM_LOOKUP != null) {
            Integer time = BLOCK_AS_ITEM_LOOKUP.get(item);
            if (time != null) return time;
        }

        if (item instanceof BlockItem blockItem && BLOCK_LOOKUP != null) {
            Integer blockTime = BLOCK_LOOKUP.get(blockItem.getBlock());
            if (blockTime != null) return blockTime;
        }

        return -1; // не наше топливо — пусть NeoForge решает сам
    }

    public static int getBurnTime(DeferredItem<Item> itemRegistryObj) {
        return ITEM_FUELS.getOrDefault(itemRegistryObj, 0);
    }

    public static int getBlockBurnTime(DeferredBlock<Block> blockRegistryObj) {
        return BLOCK_FUELS.getOrDefault(blockRegistryObj, 0);
    }

    public static int getBurnTime(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int modTime = getBurnTimeForItem(stack.getItem());
        if (modTime > 0) return modTime;

        try {
            return Math.max(0, stack.getBurnTime(RecipeType.SMELTING));
        } catch (Exception e) {
            return 0;
        }
    }

    public static int getBurnTime(Item item) {
        if (item == null) return 0;
        int modTime = getBurnTimeForItem(item);
        if (modTime > 0) return modTime;

        try {
            return Math.max(0, item.getDefaultInstance().getBurnTime(RecipeType.SMELTING));
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean isFuel(DeferredItem<Item> itemRegistryObj) {
        return ITEM_FUELS.containsKey(itemRegistryObj);
    }

    public static boolean isBlockFuel(DeferredBlock<Block> blockRegistryObj) {
        return BLOCK_FUELS.containsKey(blockRegistryObj);
    }

    public static boolean isFuel(Item item) {
        return getBurnTime(item) > 0;
    }

    public static boolean isFuel(ItemStack stack) {
        return getBurnTime(stack) > 0;
    }

    public static boolean isBlockFuel(Block block) {
        if (block == null) return false;
        ensureInitialized();
        if (BLOCK_LOOKUP != null && BLOCK_LOOKUP.containsKey(block)) return true;
        return getBurnTime(block.asItem()) > 0;
    }

    /**
     * Возвращает строку эффективности относительно угля.
     * Используйте только после проверки isFuel() / isBlockFuel()!
     */
    public static String getEfficiencyComparedToCoal(int burnTime) {
        double ratio = (double) burnTime / 1600.0;
        return String.format(Locale.ROOT, "%.2f×Coal", ratio);
    }

    @SubscribeEvent
    public static void onFuelBurnTime(FurnaceFuelBurnTimeEvent event) {
        int burnTime = getBurnTimeForItem(event.getItemStack().getItem());
        if (burnTime >= 0) {
            event.setBurnTime(burnTime);
        }
    }
}
