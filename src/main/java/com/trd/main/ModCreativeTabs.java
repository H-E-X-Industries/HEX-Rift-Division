package com.trd.main;

import com.trd.block.basic.ModBlocks;
import com.trd.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MainRegistry.MOD_ID);

    public static final Supplier<CreativeModeTab> trd_RECOURSES_TAB = CREATIVE_MODE_TABS.register("trd_recourses_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MainRegistry.MOD_ID + ".trd_recourses_tab"))
                    .icon(() -> new ItemStack(ModItems.STEEL_PLATE.get()))
                    .displayItems((parameters, output) -> {
                        // Plates
                        output.accept(ModItems.IRON_PLATE.get());
                        output.accept(ModItems.ALUMINUM_PLATE.get());
                        output.accept(ModItems.STEEL_PLATE.get());
                        output.accept(ModItems.INDUSTRIAL_COPPER_PLATE.get());
                        output.accept(ModItems.TUNGSTEN_PLATE.get());
                        output.accept(ModItems.LEAD_PLATE.get());
                        output.accept(ModItems.GOLD_PLATE.get());
                        output.accept(ModItems.TITANIUM_PLATE.get());

                        // Protectors
                        output.accept(ModItems.PROTECTOR_STEEL.get());
                        output.accept(ModItems.PROTECTOR_LEAD.get());
                        output.accept(ModItems.PROTECTOR_TUNGSTEN.get());

                        // Minerals & Resources
                        output.accept(ModItems.SEQUESTRUM.get());
                        output.accept(ModItems.SALT.get());
                        output.accept(ModItems.SULFUR.get());
                        output.accept(ModItems.LIGNITE.get());
                        output.accept(ModItems.FUEL_ASH.get());
                        output.accept(ModItems.ASBESTOS.get());
                        output.accept(ModItems.TRASH.get());
                        output.accept(ModItems.BLACK_ASH.get());
                        output.accept(ModItems.SODA_CRYSTAL.get());
                        output.accept(ModItems.BAUXITE_CHUNK.get());
                        output.accept(ModItems.BAUXITE_POWDER.get());
                        output.accept(ModItems.DOLOMITE_CHUNK.get());
                        output.accept(ModItems.DOLOMITE_POWDER.get());
                        output.accept(ModItems.LIMESTONE_CHUNK.get());
                        output.accept(ModItems.LIMESTONE_POWDER.get());
                        output.accept(ModItems.CINNABAR.get());
                        output.accept(ModItems.FLUORITE.get());
                        output.accept(ModItems.CONGLOMERATE_POWDER.get());
                        output.accept(ModItems.CONGLOMERATE_CHUNK.get());
                        output.accept(ModItems.FRACTION_CHUNK.get());
                        output.accept(ModItems.METAL_PIECE.get());
                        output.accept(ModItems.HARD_ROCK.get());

                        // Basic Components
                        output.accept(ModItems.WOODEN_HANDLE.get());
                        output.accept(ModItems.ROPE.get());
                        output.accept(ModItems.WIRE_CARRIAGE.get());
                        output.accept(ModItems.INDUSTRIAL_COPPER_WIRE.get());
                        output.accept(ModItems.GOLD_WIRE.get());
                        output.accept(ModItems.NEODYMIUM_WIRE.get());

                        // Metallurgy Raw Materials
                        output.accept(ModItems.ALUMINA.get());
                        output.accept(ModItems.SLAG.get());
                        output.accept(ModItems.LIQUID_METAL.get());

                        // Resource Registry (Metals: Ingots, Nuggets, Blocks)
                        for (ResourceRegistry.ResourceEntry entry : ResourceRegistry.getAll()) {
                            if (entry.mainUnit != null) output.accept(entry.mainUnit.get());
                            if (entry.hasSmallUnit()) output.accept(entry.smallUnit.get());
                            if (entry.hasBlock()) output.accept(entry.block.get());
                        }
                    })
                    .build());

    public static final Supplier<CreativeModeTab> trd_NATURE_TAB = CREATIVE_MODE_TABS.register("trd_nature_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MainRegistry.MOD_ID + ".trd_nature_tab"))
                    .icon(() -> new ItemStack(ModBlocks.LIGNITE_ORE.get()))
                    .withTabsBefore(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "trd_recourses_tab"))
                    .displayItems((parameters, output) -> {
                        // Р‘Р»РѕРєРё (РџСЂРёСЂРѕРґР° / Р СѓРґС‹)
                        output.accept(ModBlocks.ASBESOTS_ORE.get());
                        output.accept(ModBlocks.LIGNITE_ORE.get());
                        output.accept(ModBlocks.SALT_ORE.get());
                        output.accept(ModBlocks.BAUXITE.get());
                        output.accept(ModBlocks.DOLOMITE.get());
                        output.accept(ModBlocks.LIMESTONE.get());
                        output.accept(ModBlocks.SULFUR_CLUSTER.get());
                        output.accept(ModBlocks.CINNABAR_ORE.get());
                        output.accept(ModBlocks.CINNABAR_ORE_DEEPSLATE.get());
                        output.accept(ModBlocks.FLUORITE_ORE.get());
                        output.accept(ModBlocks.FLUORITE_ORE_DEEPSLATE.get());
                        output.accept(ModBlocks.SEQUESTRUM_ORE.get());
                        output.accept(ModBlocks.SEQUESTRUM_ORE_DEEPSLATE.get());
                        output.accept(ModBlocks.SULFUR_ORE.get());
                        output.accept(ModBlocks.SULFUR_ORE_DEEPSLATE.get());
                        output.accept(ModBlocks.CONGLOMERATE.get());
                        output.accept(ModBlocks.DEPLETED_CONGLOMERATE.get());
                    })
                    .build());

    public static final Supplier<CreativeModeTab> trd_TECH_TAB = CREATIVE_MODE_TABS.register("trd_tech_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MainRegistry.MOD_ID + ".trd_tech_tab"))
                    .icon(() -> new ItemStack(ModBlocks.MILLSTONE.get()))
                    .withTabsBefore(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "trd_nature_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.MILLSTONE.get());
                        output.accept(ModBlocks.MACHINE_BATTERY.get());
                        output.accept(ModBlocks.CONVERTER_BLOCK.get());
                        output.accept(ModBlocks.WIRE_COATED.get());
                        output.accept(ModBlocks.SWITCH.get());
                        output.accept(ModBlocks.CONNECTOR.get());
                        output.accept(ModBlocks.MEDIUM_CONNECTOR.get());
                        output.accept(ModBlocks.LARGE_CONNECTOR.get());
                        output.accept(ModBlocks.PAINTABLE_WIRE.get());
                        output.accept(ModBlocks.ELECTRO_FURNACE.get());
                        output.accept(ModItems.WIRE_COIL.get());
                        output.accept(ModItems.INDUSTRIAL_COPPER_WIRE.get());
                        output.accept(ModItems.ENERGY_CELL.get());
                        output.accept(ModItems.BATTERY.get());
                        output.accept(ModItems.BATTERY_ADVANCED.get());
                        output.accept(ModItems.BATTERY_LITHIUM.get());
                        output.accept(ModItems.BATTERY_TRIXITE.get());
                        output.accept(ModItems.CREATIVE_BATTERY.get());
                        output.accept(ModItems.SCREWDRIVER.get());
                        
                        // Conveyors
                        output.accept(ModBlocks.CONVEYOR.get());
                        output.accept(ModBlocks.CONVEYOR_ELEVATOR.get());
                        output.accept(ModBlocks.CONVEYOR_VSTAVSHIK.get());
                        output.accept(ModBlocks.CONVEYOR_IZVLEKATEL.get());
                        output.accept(ModBlocks.SORTIROVSHIK.get());
                        
                        // Fluid System
                        output.accept(ModItems.FLUID_IDENTIFIER.get());
                        output.accept(ModItems.INFINITE_FLUID_BARREL.get());
                        output.accept(ModItems.PIPETTE.get());
                        output.accept(ModItems.FLUID_TANK_IRON.get());
                        output.accept(ModBlocks.CORRUPTED_BARREL.get());
                        output.accept(ModBlocks.LEAKING_BARREL.get());
                        output.accept(ModBlocks.IRON_BARREL.get());
                        output.accept(ModBlocks.STEEL_BARREL.get());
                        output.accept(ModBlocks.LEAD_BARREL.get());
                        output.accept(ModBlocks.DECO_BARREL.get());
                        output.accept(ModBlocks.BRONZE_FLUID_PIPE.get());
                        output.accept(ModBlocks.STEEL_FLUID_PIPE.get());
                        output.accept(ModBlocks.LEAD_FLUID_PIPE.get());
                        output.accept(ModBlocks.TUNGSTEN_FLUID_PIPE.get());
                        output.accept(ModBlocks.WATER_PUMP.get());
                        output.accept(ModBlocks.VALVE.get());
                        output.accept(ModBlocks.LOW_PRESSURE_STEAM_CONDENSER.get());
                        output.accept(ModBlocks.PAINTABLE_PIPE.get());
                        output.accept(ModBlocks.FUEL_TANK_BIG.get());
                        output.accept(ModBlocks.FUEL_TANK_SMALL.get());
                        output.accept(ModBlocks.HEATER.get());
                        output.accept(ModBlocks.BOILER.get());
                        output.accept(ModBlocks.STEAM_ENGINE.get());

                        // Metallurgy & Continuous Casting
                        output.accept(ModItems.SMELTER_ITEM.get());
                        output.accept(ModItems.CC_MACHINE_ITEM.get());
                        output.accept(ModBlocks.CASTING_POT.get());
                        output.accept(ModBlocks.CASTING_DESCENT.get());
                        output.accept(ModItems.POKER.get());
                        output.accept(ModItems.MOLD_EMPTY.get());
                        output.accept(ModItems.MOLD_INGOT.get());
                        output.accept(ModItems.MOLD_NUGGET.get());
                        output.accept(ModItems.MOLD_BLOCK.get());
                        output.accept(ModItems.MOLD_PLATE.get());
                        output.accept(ModItems.MOLD_PICKAXE.get());
                        output.accept(ModItems.CAST_PICKAXE_IRON_BASE.get());
                        output.accept(ModItems.CAST_PICKAXE_STEEL_BASE.get());
                        output.accept(ModItems.CAST_PICKAXE_IRON.get());
                        output.accept(ModItems.CAST_PICKAXE_STEEL.get());

                        // Industrial Machines
                        output.accept(ModItems.DROBITEL_ITEM.get());
                        output.accept(ModItems.BLADE.get());
                        output.accept(ModBlocks.CENTRIFUGE_MOTOR.get());
                        output.accept(ModBlocks.CENTRIFUGE_CONUS.get());
                        output.accept(ModBlocks.CENTRIFUGE_CYLINDER.get());
                        output.accept(ModItems.VISHELASHIVATEL_ITEM.get());
                        output.accept(ModBlocks.MORY_BLOCK.get());
                        output.accept(ModBlocks.ANTON_CHIGUR.get());

                        // Optic Microscope
                        output.accept(ModBlocks.OPTIC_MICROSCOPE.get());

                        // Kinetic System
                        output.accept(ModBlocks.BEARING_BLOCK.get());
                        output.accept(ModBlocks.HAND_CRANK_BLOCK.get());
                        output.accept(ModBlocks.CLUTCH.get());
                        output.accept(ModBlocks.MOTOR_ELECTRO.get());
                        output.accept(ModBlocks.TACHOMETER.get());
                        output.accept(ModItems.BELT.get());
                        output.accept(ModItems.PULLEY.get());
                        output.accept(ModItems.BEVEL_GEAR.get());
                        output.accept(ModItems.GEAR1_STEEL.get());
                        output.accept(ModItems.GEAR2_STEEL.get());
                        output.accept(ModItems.FLYWHEEL_LIGHT.get());
                        output.accept(ModItems.COPPER_ROTOR.get());
                        output.accept(ModItems.STATOR_ITEM.get());
                        output.accept(ModItems.COPPER_COIL.get());
                        ModBlocks.ALL_SHAFTS.forEach(shaft -> output.accept(shaft.get()));

                        // Fluid Drops
                        for (net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.item.Item, ? extends net.minecraft.world.item.Item> drop : com.trd.api.fluids.ModFluids.getAllFluidDrops().values()) {
                            output.accept(drop.get());
                        }
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}

