package com.trd.main;

import com.trd.api.fluids.ModFluids;
import com.trd.api.metallurgy.system.Metal;
import com.trd.api.metallurgy.system.MetalUnits2;
import com.trd.api.metallurgy.system.MetallurgyRegistry;
import com.trd.block.basic.ModBlocks;
import com.trd.event.SlagItem;
import com.trd.item.ModItems;
import com.trd.item.industrial.energy.ModBatteryItem;
import com.trd.item.industrial.energy.WireCoilItem;
import com.trd.item.industrial.fluids.FluidContainerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Supplier;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MainRegistry.MOD_ID);

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, path);
    }

    // Первая вкладка BUILD (без withTabsBefore)
    public static final Supplier<CreativeModeTab> trd_BUILD_TAB = CREATIVE_MODE_TABS.register("trd_build_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MainRegistry.MOD_ID + ".trd_build_tab"))
                    .icon(() -> new ItemStack(ModBlocks.CONCRETE_HAZARD_NEW.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.CONCRETE.get());
                        output.accept(ModBlocks.CONCRETE_SLAB.get());
                        output.accept(ModBlocks.CONCRETE_STAIRS.get());
                        output.accept(ModBlocks.CONCRETE_OLD.get());
                        output.accept(ModBlocks.CONCRETE_OLD_SLAB.get());
                        output.accept(ModBlocks.CONCRETE_OLD_STAIRS.get());
                        output.accept(ModBlocks.CONCRETE_MOSSY.get());
                        output.accept(ModBlocks.CONCRETE_MOSSY_SLAB.get());
                        output.accept(ModBlocks.CONCRETE_MOSSY_STAIRS.get());
                        output.accept(ModBlocks.CONCRETE_HAZARD_NEW.get());
                        output.accept(ModBlocks.CONCRETE_HAZARD_NEW_SLAB.get());
                        output.accept(ModBlocks.CONCRETE_HAZARD_NEW_STAIRS.get());
                        output.accept(ModBlocks.CONCRETE_HAZARD_OLD.get());
                        output.accept(ModBlocks.CONCRETE_HAZARD_OLD_SLAB.get());
                        output.accept(ModBlocks.CONCRETE_HAZARD_OLD_STAIRS.get());
                        output.accept(ModBlocks.CONCRETE_TILE.get());
                        output.accept(ModBlocks.CONCRETE_TILE_SLAB.get());
                        output.accept(ModBlocks.CONCRETE_TILE_STAIRS.get());
                        output.accept(ModBlocks.CONCRETE_TILE_ALT.get());
                        output.accept(ModBlocks.CONCRETE_TILE_ALT_SLAB.get());
                        output.accept(ModBlocks.CONCRETE_TILE_ALT_STAIRS.get());
                        output.accept(ModBlocks.CONCRETE_TILE_ALT_BLUE.get());
                        output.accept(ModBlocks.CONCRETE_TILE_ALT_BLUE_SLAB.get());
                        output.accept(ModBlocks.CONCRETE_TILE_ALT_BLUE_STAIRS.get());
                        output.accept(ModBlocks.CONCRETE_STRIPPED.get());
                        output.accept(ModBlocks.CONCRETE_STRIPPED_SLAB.get());
                        output.accept(ModBlocks.CONCRETE_STRIPPED_STAIRS.get());
                        output.accept(ModBlocks.CONCRETE_REINFORCED.get());
                        output.accept(ModBlocks.CONCRETE_REINFORCED_SLAB.get());
                        output.accept(ModBlocks.CONCRETE_REINFORCED_STAIRS.get());
                        output.accept(ModBlocks.CONCRETE_REINFORCED_HEAVY.get());
                        output.accept(ModBlocks.CONCRETE_REINFORCED_HEAVY_SLAB.get());
                        output.accept(ModBlocks.CONCRETE_REINFORCED_HEAVY_STAIRS.get());
                        output.accept(ModBlocks.FIREBRICK_BLOCK.get());
                        output.accept(ModBlocks.FIREBRICK_SLAB.get());
                        output.accept(ModBlocks.FIREBRICK_STAIRS.get());
                        output.accept(ModBlocks.REINFORCEDBRICK_BLOCK.get());
                        output.accept(ModBlocks.REINFORCEDBRICK_SLAB.get());
                        output.accept(ModBlocks.REINFORCEDBRICK_STAIRS.get());
                        output.accept(ModBlocks.CONCRETE_CONSTRUCT_BLOCK.get());
                        output.accept(ModBlocks.CONCRETE_CONSTRUCT_BLOCK_SLAB.get());
                        output.accept(ModBlocks.CONCRETE_CONSTRUCT_BLOCK_STAIRS.get());
                        output.accept(ModBlocks.STEEL_CONSTRUCT_BLOCK.get());
                        output.accept(ModBlocks.STEEL_CONSTRUCT_BLOCK_SLAB.get());
                        output.accept(ModBlocks.STEEL_CONSTRUCT_BLOCK_STAIRS.get());
                        output.accept(ModBlocks.STEEL_CONSTRUCT_BLOCK_REINFORCED.get());
                        output.accept(ModBlocks.STEEL_CONSTRUCT_BLOCK_REINFORCED_SLAB.get());
                        output.accept(ModBlocks.STEEL_CONSTRUCT_BLOCK_REINFORCED_STAIRS.get());
                        output.accept(ModBlocks.SEQUOIA_PLANKS.get());
                        output.accept(ModBlocks.SEQUOIA_SLAB.get());
                        output.accept(ModBlocks.SEQUOIA_STAIRS.get());
                        output.accept(ModBlocks.WASTE_PLANKS.get());
                        output.accept(ModBlocks.WASTE_PLANKS_STAIRS.get());
                        output.accept(ModBlocks.WASTE_PLANKS_SLAB.get());

                        output.accept(ModBlocks.CONCRETE_LINE.get());
                        output.accept(ModBlocks.CONCRETE_CUT.get());
                        output.accept(ModBlocks.CONCRETE_RAIL.get());
                        output.accept(ModBlocks.CONCRETE_NET.get());
                        output.accept(ModBlocks.CONCRETE_REBAR.get());
                        output.accept(ModBlocks.CONCRETE_VENT.get());
                        output.accept(ModBlocks.ARMORED_GLASS.get());

                        output.accept(ModBlocks.MORY_BLOCK.get());
                        output.accept(ModBlocks.ANTON_CHIGUR.get());

                        output.accept(ModBlocks.MINERAL_BLOCK2.get());
                        output.accept(ModBlocks.MINERAL_TILE.get());
                        output.accept(ModBlocks.DOLOMITE_TILE.get());
                        output.accept(ModBlocks.TILE_LIGHT.get());
                        output.accept(ModBlocks.SULFUR_TILE.get());
                        output.accept(ModBlocks.SULFUR_BRICKS.get());
                        output.accept(ModBlocks.DECO_STEEL.get());
                        output.accept(ModBlocks.DECO_STEEL_DARK.get());
                        output.accept(ModBlocks.DECO_STEEL_SMOG.get());
                        output.accept(ModBlocks.DECO_LEAD.get());
                        output.accept(ModBlocks.DECO_BEAM.get());
                        output.accept(ModBlocks.BEAM_BLOCK.get());

                        output.accept(ModBlocks.DECO_BARREL.get());
                        output.accept(ModBlocks.STEEL_PROPS.get());
                        output.accept(ModBlocks.BARBED_WIRE.get());
                        output.accept(ModBlocks.WASTE_FENCE.get());
                        output.accept(ModBlocks.WASTE_FENCE_GATE.get());
                        output.accept(ModBlocks.WASTE_PRESSURE_PLATE.get());
                        output.accept(ModBlocks.ROUND_LAMP.get());
                        output.accept(ModBlocks.WIRE_FENCE.get());
                        output.accept(ModBlocks.WIRE_FENCE_ALT.get());

                        output.accept(ModBlocks.STEEL_DOOR.get());
                        output.accept(ModBlocks.SEQUOIA_DOOR.get());

                    })
                    .build());

    // Вкладка TECH – должна быть после BUILD
    public static final Supplier<CreativeModeTab> trd_TECH_TAB = CREATIVE_MODE_TABS.register("trd_tech_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MainRegistry.MOD_ID + ".trd_tech_tab"))
                    .icon(() -> new ItemStack(ModBlocks.FUEL_TANK_BIG.get()))
                    .withTabsBefore(id("trd_build_tab"))
                    .displayItems((parameters, output) -> {

                        //ИНСТРУМЕНТЫ
                        output.accept(ModItems.SCREWDRIVER.get());
                        output.accept(ModItems.BEAM_PLACER.get());
                        output.accept(ModItems.POKER.get());
                        output.accept(ModItems.HAMMER.get());

                        output.accept(ModItems.BELT.get());
                        // Катушка: пустая и заряженная версии в креативной вкладке
                        output.accept(ModItems.WIRE_COIL.get());
                        ItemStack fullCoil = new ItemStack(ModItems.WIRE_COIL.get());
                        WireCoilItem.setWires(fullCoil, WireCoilItem.MAX_WIRES);
                        output.accept(fullCoil);

                        output.accept(ModItems.INFINITE_FLUID_BARREL.get());
                        output.accept(ModItems.FLUID_IDENTIFIER.get());

                        //КИНЕТИКА
                        output.accept(ModBlocks.HAND_CRANK_BLOCK.get());

                        output.accept(ModBlocks.SHAFT_LIGHT_IRON.get());
                        output.accept(ModBlocks.SHAFT_MEDIUM_IRON.get());

                        output.accept(ModBlocks.SHAFT_LIGHT_DURALUMIN.get());
                        output.accept(ModBlocks.SHAFT_MEDIUM_DURALUMIN.get());

                        output.accept(ModBlocks.SHAFT_LIGHT_STEEL.get());
                        output.accept(ModBlocks.SHAFT_MEDIUM_STEEL.get());

                        output.accept(ModBlocks.SHAFT_LIGHT_TITANIUM.get());
                        output.accept(ModBlocks.SHAFT_MEDIUM_TITANIUM.get());

                        output.accept(ModBlocks.SHAFT_LIGHT_TUNGSTEN_CARBIDE.get());
                        output.accept(ModBlocks.SHAFT_MEDIUM_TUNGSTEN_CARBIDE.get());

                        output.accept(ModItems.BEVEL_GEAR.get());
                        output.accept(ModItems.GEAR1_STEEL.get());
                        output.accept(ModItems.GEAR2_STEEL.get());

                        output.accept(ModItems.PULLEY.get());
                        output.accept(ModItems.FLYWHEEL_LIGHT.get());
                        output.accept(ModItems.COPPER_ROTOR.get());

                        output.accept(ModBlocks.BEARING_BLOCK.get());
                        output.accept(ModBlocks.MOTOR_ELECTRO.get());
                        output.accept(ModBlocks.CLUTCH.get());
                        output.accept(ModBlocks.TACHOMETER.get());
                        output.accept(ModBlocks.STEAM_ENGINE.get());
                        output.accept(ModBlocks.DROBITEL.get());
                        output.accept(ModItems.BLADE.get());
                        output.accept(ModItems.STATOR_ITEM.get());
                        output.accept(ModItems.COPPER_COIL.get());
                        // ─── Станок ───
                        output.accept(ModBlocks.STANOK.get());
                        output.accept(ModItems.PRESS_CARRIAGE.get());
                        output.accept(ModItems.WIRE_CARRIAGE.get());
                        output.accept(ModItems.FREZA_CARRIAGE.get());

                        //ЭНЕРГОСЕТЬ
                        output.accept(ModBlocks.MACHINE_BATTERY.get());
                        output.accept(ModItems.ENERGY_CELL.get());

                        output.accept(ModBlocks.WIRE_COATED.get());
                        output.accept(ModBlocks.PAINTABLE_WIRE.get());
                        output.accept(ModBlocks.CONNECTOR.get());
                        output.accept(ModBlocks.MEDIUM_CONNECTOR.get());
                        output.accept(ModBlocks.LARGE_CONNECTOR.get());

                        output.accept(ModItems.CREATIVE_BATTERY.get());
                        List<DeferredItem<Item>> batteriesToAdd = List.of(
                                ModItems.BATTERY,
                                ModItems.BATTERY_ADVANCED,
                                ModItems.BATTERY_LITHIUM,
                                ModItems.BATTERY_TRIXITE);
                        for (DeferredItem<Item> batteryRegObj : batteriesToAdd) {
                            Item item = batteryRegObj.get();
                            if (item instanceof ModBatteryItem batteryItem) {
                                output.accept(new ItemStack(batteryItem));
                                ItemStack chargedStack = new ItemStack(batteryItem);
                                ModBatteryItem.setEnergy(chargedStack, batteryItem.getCapacity());
                                output.accept(chargedStack);
                            }
                        }

                        output.accept(ModBlocks.SWITCH.get());
                        output.accept(ModBlocks.CONVERTER_BLOCK.get());

                        output.accept(ModBlocks.ELECTRO_FURNACE.get());

                        output.accept(ModBlocks.CENTRIFUGE_MOTOR.get());
                        output.accept(ModBlocks.CENTRIFUGE_CONUS.get());
                        output.accept(ModBlocks.CENTRIFUGE_CYLINDER.get());

                        //ЖИДКОСТИ
                        output.accept(ModBlocks.CORRUPTED_BARREL.get());
                        output.accept(ModBlocks.LEAKING_BARREL.get());
                        output.accept(ModBlocks.IRON_BARREL.get());
                        output.accept(ModBlocks.STEEL_BARREL.get());
                        output.accept(ModBlocks.LEAD_BARREL.get());
                        output.accept(ModBlocks.FUEL_TANK_SMALL.get());
                        output.accept(ModBlocks.FUEL_TANK_BIG.get());

                        output.accept(ModItems.PROTECTOR_STEEL.get());
                        output.accept(ModItems.PROTECTOR_LEAD.get());
                        output.accept(ModItems.PROTECTOR_TUNGSTEN.get());

                        output.accept(ModBlocks.OPTIC_MICROSCOPE.get());
                        output.accept(ModItems.PIPETTE.get());
                        output.accept(ModItems.PIPETTE_IDUSTRIAL.get());
                        output.accept(ModItems.FLUID_TANK_IRON.get());

                        output.accept(ModBlocks.BRONZE_FLUID_PIPE.get());
                        output.accept(ModBlocks.STEEL_FLUID_PIPE.get());
                        output.accept(ModBlocks.LEAD_FLUID_PIPE.get());
                        output.accept(ModBlocks.TUNGSTEN_FLUID_PIPE.get());

                        output.accept(ModBlocks.PAINTABLE_PIPE.get());

                        output.accept(ModBlocks.CHEMICAL_PLANT_REACTION_CHAMBER.get());
                        output.accept(ModBlocks.CHEMICAL_PLANT_PORT.get());
                        output.accept(ModBlocks.CHEMICAL_PLANT_HEATER.get());

                        output.accept(ModBlocks.BOILER.get());

                        output.accept(ModBlocks.LOW_PRESSURE_STEAM_CONDENSER.get());
                        output.accept(ModBlocks.WATER_PUMP.get());

                        output.accept(ModItems.VISHELASHIVATEL_ITEM.get());

                        //ПРОЧЕЕ
                        output.accept(ModBlocks.SMALL_SMELTER.get());

                        output.accept(ModBlocks.HEATER.get());
                        output.accept(ModItems.SMELTER_ITEM.get());
                        output.accept(ModItems.CC_MACHINE_ITEM.get());
                        output.accept(ModBlocks.COCCER_OVEN.get());
                        output.accept(ModBlocks.CASTING_POT.get());
                        output.accept(ModBlocks.CASTING_DESCENT.get());
                        output.accept(ModItems.MOLD_EMPTY.get());
                        output.accept(ModItems.MOLD_NUGGET.get());
                        output.accept(ModItems.MOLD_PLATE.get());
                        output.accept(ModItems.MOLD_INGOT.get());
                        output.accept(ModItems.MOLD_BLOCK.get());
                        output.accept(ModItems.MOLD_PICKAXE.get());

                        output.accept(ModBlocks.STEEL_STORAGE.get());

                        output.accept(ModBlocks.CONVEYOR_VSTAVSHIK.get());
                        output.accept(ModBlocks.CONVEYOR_IZVLEKATEL.get());
                        output.accept(ModBlocks.CONVEYOR.get());
                        output.accept(ModBlocks.CONVEYOR_ELEVATOR.get());
                        output.accept(ModBlocks.SORTIROVSHIK.get());
                    })
                    .build());

    public static final Supplier<CreativeModeTab> trd_WEAPONS_TAB = CREATIVE_MODE_TABS.register("trd_weapons_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MainRegistry.MOD_ID + ".trd_weapons_tab"))
                    .icon(() -> new ItemStack(ModItems.CAST_PICKAXE_IRON.get()))
                    .withTabsBefore(id("trd_tech_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CAST_PICKAXE_IRON.get());
                        output.accept(ModItems.CAST_PICKAXE_STEEL.get());

                        output.accept(ModBlocks.DET_MINER.get());
                        output.accept(ModBlocks.AVIABOMB_MINE.get());
                        output.accept(ModItems.DETONATOR.get());
                        output.accept(ModItems.RANGE_DETONATOR.get());

                        output.accept(ModItems.MACHINEGUN.get());

                        output.accept(ModItems.AMMO_TURRET.get());
                        output.accept(ModItems.AMMO_TURRET_HOLLOW.get());
                        output.accept(ModItems.AMMO_TURRET_PIERCING.get());
                        output.accept(ModItems.AMMO_TURRET_FIRE.get());
                        output.accept(ModItems.AMMO_TURRET_RADIO.get());

                        output.accept(ModItems.MISSILE_100MM.get());
                        output.accept(ModItems.MISSILE_100MM_HE.get());
                        output.accept(ModItems.MISSILE_100MM_FIRE.get());
                    })
                    .build());

    public static final Supplier<CreativeModeTab> trd_RECOURSES_TAB = CREATIVE_MODE_TABS.register("trd_recourses_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MainRegistry.MOD_ID + ".trd_recourses_tab"))
                    .icon(() -> {
                        var steel = ResourceRegistry.getMainUnit("steel");
                        return new ItemStack(steel != null ? steel : ModItems.FIREBRICK.get());
                    })
                    .withTabsBefore(id("trd_weapons_tab"))
                    .displayItems((parameters, output) -> {

                        // Основные единицы (слитки/кристаллы/гранулы)
                        for (ResourceRegistry.ResourceEntry entry : ResourceRegistry.getAll()) {
                            if (entry.mainUnit != null) {
                                output.accept(entry.mainUnit.get());
                            }
                        }
                        // Мелкие единицы (самородки/осколки)
                        for (ResourceRegistry.ResourceEntry entry : ResourceRegistry.getAll()) {
                            if (entry.hasSmallUnit()) {
                                output.accept(entry.smallUnit.get());
                            }
                        }
                        // Блоки-комплекты металлов
                        for (ResourceRegistry.ResourceEntry entry : ResourceRegistry.getAll()) {
                            if (entry.blockItem != null) {
                                output.accept(entry.blockItem.get());
                            }
                        }
                        output.accept(ModBlocks.LIGNITE_BLOCK.get());

                        output.accept(ModItems.IRON_PLATE.get());
                        output.accept(ModItems.TITANIUM_PLATE.get());
                        output.accept(ModItems.STEEL_PLATE.get());
                        output.accept(ModItems.TUNGSTEN_PLATE.get());
                        output.accept(ModItems.LEAD_PLATE.get());
                        output.accept(ModItems.ALUMINUM_PLATE.get());
                        output.accept(ModItems.INDUSTRIAL_COPPER_PLATE.get());
                        output.accept(ModItems.GOLD_PLATE.get());

                        output.accept(ModItems.INDUSTRIAL_COPPER_WIRE.get());
                        output.accept(ModItems.GOLD_WIRE.get());
                        output.accept(ModItems.NEODYMIUM_WIRE.get());

                        output.accept(ModItems.CAST_PICKAXE_IRON_BASE.get());
                        output.accept(ModItems.CAST_PICKAXE_STEEL_BASE.get());

                        for (Metal metal : MetallurgyRegistry.getAllMetals()) {
                            ItemStack slagStack = SlagItem.createSlag(metal, MetalUnits2.UNITS_PER_INGOT);
                            output.accept(slagStack);
                        }
                        output.accept(ModItems.ROPE.get());
                        output.accept(ModItems.WOODEN_HANDLE.get());

                        output.accept(ModItems.FIRE_SMES.get());
                        output.accept(ModItems.DOLOMITE_SMES.get());
                        output.accept(ModItems.QUICKLIME.get());
                        output.accept(ModItems.FIREBRICK.get());
                        output.accept(ModItems.REINFORCEDBRICK.get());

                        output.accept(ModItems.CONGLOMERATE_CHUNK.get());
                        output.accept(ModItems.FRACTION_CHUNK.get());
                        output.accept(ModItems.METAL_PIECE.get());
                        output.accept(ModItems.HARD_ROCK.get());
                        output.accept(ModItems.DOLOMITE_CHUNK.get());
                        output.accept(ModItems.LIMESTONE_CHUNK.get());
                        output.accept(ModItems.BAUXITE_CHUNK.get());
                        output.accept(ModItems.ASBESTOS.get());
                        output.accept(ModItems.CINNABAR.get());
                        output.accept(ModItems.LIGNITE.get());
                        output.accept(ModItems.FLUORITE.get());
                        output.accept(ModItems.ALUMINA.get());
                        output.accept(ModItems.SODA_CRYSTAL.get());

                        output.accept(ModItems.SODA.get());
                        output.accept(ModItems.ALUMINUM_HYDROXIDE.get());
                        output.accept(ModItems.SEQUESTRUM.get());
                        output.accept(ModItems.SALT.get());
                        output.accept(ModItems.SULFUR.get());

                        output.accept(ModItems.CONGLOMERATE_POWDER.get());
                        output.accept(ModItems.DOLOMITE_POWDER.get());
                        output.accept(ModItems.LIMESTONE_POWDER.get());
                        output.accept(ModItems.BAUXITE_POWDER.get());

                        output.accept(ModItems.FUEL_ASH.get());
                        output.accept(ModItems.BLACK_ASH.get());
                        output.accept(ModItems.TRASH.get());

                        for (DeferredHolder<Item, ? extends Item> drop : ModFluids.getAllFluidDrops().values()) {
                            output.accept(drop.get());
                        }

                        // Предзаполненные контейнеры: автоматически выбираются только совместимые жидкости
                        // (по макс. коррозии/температуре самих пипеток/контейнера), чтобы предмет
                        // не растворялся сразу в руках игрока.
                        List<Fluid> fluids = ModFluids.getAllSourceFluids();
                        addAllFilledContainers(output, ModItems.PIPETTE.get(), fluids);
                        addAllFilledContainers(output, ModItems.PIPETTE_IDUSTRIAL.get(), fluids);
                        addAllFilledContainers(output, ModItems.FLUID_TANK_IRON.get(), fluids);

                    })
                    .build());

    public static final Supplier<CreativeModeTab> trd_NATURE_TAB = CREATIVE_MODE_TABS.register("trd_nature_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MainRegistry.MOD_ID + ".trd_nature_tab"))
                    .icon(() -> new ItemStack(ModBlocks.LIGNITE_ORE.get()))
                    .withTabsBefore(id("trd_recourses_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.ASBESOTS_ORE.get());
                        output.accept(ModBlocks.SALT_ORE.get());
                        output.accept(ModBlocks.LIGNITE_ORE.get());
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

                        output.accept(ModBlocks.DOLOMITE.get());
                        output.accept(ModBlocks.LIMESTONE.get());
                        output.accept(ModBlocks.SULFUR_CLUSTER.get());
                        output.accept(ModBlocks.BAUXITE.get());
                        output.accept(ModBlocks.SEQUOIA_BARK.get());
                        output.accept(ModBlocks.SEQUOIA_HEARTWOOD.get());
                        output.accept(ModBlocks.SEQUOIA_BIOME_MOSS.get());
                    })
                    .build());

    /** Добавляет предзаполненные контейнеры во вкладку — для каждой совместимой жидкости. */
    private static void addAllFilledContainers(CreativeModeTab.Output output, Item item, List<Fluid> fluids) {
        for (Fluid fluid : fluids) {
            ItemStack stack = FluidContainerItem.createFilled(item, fluid);
            if (!stack.isEmpty()) {
                output.accept(stack);
            }
        }
    }

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
