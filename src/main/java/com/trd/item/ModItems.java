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

    public static final DeferredItem<Item> BAUXITE_CHUNK = ITEMS.register("bauxite_chunk",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> BAUXITE_POWDER = ITEMS.register("bauxite_powder",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> DOLOMITE_CHUNK = ITEMS.register("dolomite_chunk",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> DOLOMITE_POWDER = ITEMS.register("dolomite_powder",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> LIMESTONE_CHUNK = ITEMS.register("limestone_chunk",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> LIMESTONE_POWDER = ITEMS.register("limestone_powder",
            () -> new Item(new Item.Properties()));

        public static final DeferredItem<Item> CREATIVE_BATTERY = ITEMS.register("battery_creative", () -> new com.trd.item.industrial.energy.ItemCreativeBattery(new Item.Properties()));
    public static final DeferredItem<Item> BATTERY = ITEMS.register("battery", () -> new com.trd.item.industrial.energy.ModBatteryItem(new Item.Properties(), 5000, 100, 100));
    public static final DeferredItem<Item> BATTERY_ADVANCED = ITEMS.register("battery_advanced", () -> new com.trd.item.industrial.energy.ModBatteryItem(new Item.Properties(), 20000, 500, 500));
    public static final DeferredItem<Item> BATTERY_LITHIUM = ITEMS.register("battery_lithium", () -> new com.trd.item.industrial.energy.ModBatteryItem(new Item.Properties(), 250000, 1000, 1000));
    public static final DeferredItem<Item> BATTERY_TRIXITE = ITEMS.register("battery_trixite", () -> new com.trd.item.industrial.energy.ModBatteryItem(new Item.Properties(), 5000000, 40000, 200000));
    public static final DeferredItem<Item> ENERGY_CELL = ITEMS.register("energy_cell_basic", () -> new com.trd.item.industrial.energy.EnergyCellItem(new Item.Properties().stacksTo(1), 1000000, 5000, 5000));
        public static final DeferredItem<Item> WIRE_COIL = ITEMS.register("wire_coil", () -> new com.trd.item.industrial.energy.WireCoilItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> WIRE_CARRIAGE = ITEMS.register("wire_carriage", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> INDUSTRIAL_COPPER_WIRE = ITEMS.register("industrial_copper_wire", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> GOLD_WIRE = ITEMS.register("gold_wire", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> NEODYMIUM_WIRE = ITEMS.register("neodymium_wire", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SCREWDRIVER = ITEMS.register("screwdriver", () -> new com.trd.item.tools.ScrewdriverItem(new Item.Properties().stacksTo(1)));
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
