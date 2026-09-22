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
                        // Предметы (Ресурсы)
                        output.accept(ModItems.IRON_PLATE.get());
                        output.accept(ModItems.ALUMINUM_PLATE.get());
                        output.accept(ModItems.STEEL_PLATE.get());
                        output.accept(ModItems.INDUSTRIAL_COPPER_PLATE.get());
                        output.accept(ModItems.SEQUESTRUM.get());
                        output.accept(ModItems.SALT.get());
                        output.accept(ModItems.SULFUR.get());
                        output.accept(ModItems.BAUXITE_CHUNK.get());
                        output.accept(ModItems.BAUXITE_POWDER.get());
                        output.accept(ModItems.DOLOMITE_CHUNK.get());
                        output.accept(ModItems.DOLOMITE_POWDER.get());
                        output.accept(ModItems.LIMESTONE_CHUNK.get());
                        output.accept(ModItems.LIMESTONE_POWDER.get());
                    })
                    .build());

    public static final Supplier<CreativeModeTab> trd_NATURE_TAB = CREATIVE_MODE_TABS.register("trd_nature_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MainRegistry.MOD_ID + ".trd_nature_tab"))
                    .icon(() -> new ItemStack(ModBlocks.LIGNITE_ORE.get()))
                    .withTabsBefore(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "trd_recourses_tab"))
                    .displayItems((parameters, output) -> {
                        // Блоки (Природа / Руды)
                        output.accept(ModBlocks.ASBESOTS_ORE.get());
                        output.accept(ModBlocks.LIGNITE_ORE.get());
                        output.accept(ModBlocks.SALT_ORE.get());
                        output.accept(ModBlocks.BAUXITE.get());
                        output.accept(ModBlocks.DOLOMITE.get());
                        output.accept(ModBlocks.LIMESTONE.get());
                        output.accept(ModBlocks.SULFUR_CLUSTER.get());
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
                        output.accept(ModItems.WOODEN_FLUID_CONTAINER.get());
                        output.accept(ModItems.IRON_FLUID_CONTAINER.get());
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
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
