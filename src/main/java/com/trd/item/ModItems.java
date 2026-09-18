package com.trd.item;

import com.trd.main.MainRegistry;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = 
            DeferredRegister.createItems(MainRegistry.MOD_ID);

    // Простые предметы (без кастомной логики)
    public static final DeferredItem<Item> IRON_PLATE = ITEMS.register("iron_plate",
            () -> new Item(new Item.Properties()));
    
    public static final DeferredItem<Item> ALUMINUM_PLATE = ITEMS.register("aluminum_plate",
            () -> new Item(new Item.Properties()));
            
    public static final DeferredItem<Item> STEEL_PLATE = ITEMS.register("steel_plate",
            () -> new Item(new Item.Properties()));
            
    public static final DeferredItem<Item> INDUSTRIAL_COPPER_PLATE = ITEMS.register("industrial_copper_plate",
            () -> new Item(new Item.Properties()));
            
    public static final DeferredItem<Item> SEQUESTRUM = ITEMS.register("sequestrum",
            () -> new Item(new Item.Properties()));
            
    public static final DeferredItem<Item> SALT = ITEMS.register("salt",
            () -> new Item(new Item.Properties()));
            
    public static final DeferredItem<Item> SULFUR = ITEMS.register("sulfur",
            () -> new Item(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
