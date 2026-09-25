package com.trd.item;

import com.trd.main.MainRegistry;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import com.trd.block.basic.ModBlocks;

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
            
    public static final DeferredItem<Item> PROTECTOR_STEEL = ITEMS.register("protector_steel",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> PROTECTOR_LEAD = ITEMS.register("protector_lead",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> PROTECTOR_TUNGSTEN = ITEMS.register("protector_tungsten",
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

    // New items
    public static final DeferredItem<Item> CINNABAR = ITEMS.register("cinnabar", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> FLUORITE = ITEMS.register("fluorite", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> CONGLOMERATE_POWDER = ITEMS.register("conglomerate_powder", () -> new Item(new Item.Properties()));
    
    public static final DeferredItem<Item> CONGLOMERATE_CHUNK = ITEMS.register("conglomerate_chunk", () -> new com.trd.item.conglomerates.ConglomerateItem(new Item.Properties()));
    public static final DeferredItem<Item> FRACTION_CHUNK = ITEMS.register("fraction_chunk", () -> new com.trd.item.conglomerates.FractionChunkItem(new Item.Properties()));
    public static final DeferredItem<Item> METAL_PIECE = ITEMS.register("metal_piece", () -> new com.trd.item.conglomerates.MetalPieceItem(new Item.Properties()));

    // Fluid Containers
    public static final DeferredItem<Item> PIPETTE = ITEMS.register("pipette",
            () -> new com.trd.item.industrial.fluids.FluidContainerItem(new Item.Properties().stacksTo(1), 50, 80, 100));

    public static final DeferredItem<Item> HARD_ROCK = ITEMS.register("hard_rock",
            () -> new Item(new Item.Properties()));

    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item> FLUID_IDENTIFIER = ITEMS.register("fluid_identifier", () -> new com.trd.item.industrial.fluids.FluidIdentifierItem(new net.minecraft.world.item.Item.Properties().stacksTo(1)));
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item> INFINITE_FLUID_BARREL = ITEMS.register("infinite_fluid_barrel", () -> new com.trd.item.tools.InfiniteFluidBarrelItem(new net.minecraft.world.item.Item.Properties().stacksTo(1)));
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item> FLUID_TANK_IRON = ITEMS.register("fluid_tank_iron", () -> new com.trd.item.industrial.fluids.FluidContainerItem(new net.minecraft.world.item.Item.Properties().stacksTo(16), 1000, 80, 1050));

    // Cast Pickaxes
    public static final DeferredItem<Item> CAST_PICKAXE_IRON = ITEMS.register("cast_pickaxe_iron",
            () -> new com.trd.item.tools.cast_pickaxes.materials.CastPickaxeIronItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CAST_PICKAXE_STEEL = ITEMS.register("cast_pickaxe_steel",
            () -> new com.trd.item.tools.cast_pickaxes.materials.CastPickaxeSteelItem(new Item.Properties().stacksTo(1)));

    // Rotation / Kinetic Items
    public static final DeferredItem<Item> BELT = ITEMS.register("belt",
            () -> new com.trd.item.industrial.rotation.BeltItem(new Item.Properties()));
    public static final DeferredItem<Item> PULLEY = ITEMS.register("pulley",
            () -> new com.trd.item.industrial.rotation.PulleyItem(new Item.Properties(), 1, 12,
                    com.trd.api.rotation.ShaftMaterial.IRON,
                    com.trd.api.rotation.ShaftDiameter.LIGHT,
                    com.trd.api.rotation.ShaftDiameter.MEDIUM));

    public static final DeferredItem<Item> BEVEL_GEAR = ITEMS.register("bevel_gear",
            () -> new com.trd.item.industrial.rotation.BevelGearItem(new Item.Properties(), com.trd.api.rotation.ShaftMaterial.STEEL));

    public static final DeferredItem<Item> GEAR1_STEEL = ITEMS.register("gear1_steel",
            () -> new com.trd.item.industrial.rotation.GearItem(new Item.Properties(), 1, com.trd.api.rotation.ShaftMaterial.STEEL));

    public static final DeferredItem<Item> GEAR2_STEEL = ITEMS.register("gear2_steel",
            () -> new com.trd.item.industrial.rotation.GearItem(new Item.Properties(), 2, com.trd.api.rotation.ShaftMaterial.STEEL));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
