package com.trd.block.basic;

import com.trd.item.ModItems;
import com.trd.main.MainRegistry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.trd.api.rotation.ShaftDiameter;
import com.trd.api.rotation.ShaftMaterial;
import com.trd.multiblock.system.IMultiblockController;
import com.trd.multiblock.system.MultiblockBlockItem;
import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MainRegistry.MOD_ID);

    // Пример простых блоков (без кастомной логики)
    public static final DeferredBlock<Block> ASBESOTS_ORE = registerBlock("asbestos_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));
    
    public static final DeferredBlock<Block> SEQUOIA_BARK = registerBlock("sequoia_bark",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)));
    public static final DeferredBlock<Block> SEQUOIA_BARK_MOSSY = registerBlock("sequoia_bark_mossy",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)));
    public static final DeferredBlock<Block> SEQUOIA_BARK_DARK = registerBlock("sequoia_bark_dark",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)));
    public static final DeferredBlock<Block> SEQUOIA_BARK_LIGHT = registerBlock("sequoia_bark_light",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)));
    public static final DeferredBlock<Block> SEQUOIA_HEARTWOOD = registerBlock("sequoia_heartwood",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)));
    public static final DeferredBlock<Block> SEQUOIA_BIOME_MOSS = registerBlock("sequoia_biome_moss",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.MOSS_BLOCK)));
    public static final DeferredBlock<Block> SEQUOIA_ROOTS = registerBlock("sequoia_roots",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(0.5f, 4.0f).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> SEQUOIA_ROOTS_MOSSY = registerBlock("sequoia_roots_mossy",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(0.5f, 4.0f).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> SEQUOIA_LEAVES = registerBlock("sequoia_leaves",
            () -> new net.minecraft.world.level.block.LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_LEAVES).noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));
            
    public static final DeferredBlock<Block> LIGNITE_ORE = registerBlock("lignite_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.COAL_ORE)));
    public static final DeferredBlock<Block> LIGNITE_BLOCK = registerBlock("lignite_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.COAL_BLOCK)));

    public static final DeferredBlock<Block> SALT_ORE = registerBlock("salt_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));

    public static final DeferredBlock<Block> BAUXITE = registerBlock("bauxite",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)));

    public static final DeferredBlock<Block> DOLOMITE = registerBlock("dolomite",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)));

    public static final DeferredBlock<Block> LIMESTONE = registerBlock("limestone",
            () -> new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE)));
    public static final DeferredBlock<Block> SULFUR_CLUSTER = registerBlock("sulfur_cluster",
            () -> new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE)));

    // New Ores
    public static final DeferredBlock<Block> CINNABAR_ORE = registerBlock("cinnabar_ore",
            () -> new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> CINNABAR_ORE_DEEPSLATE = registerBlock("cinnabar_ore_deepslate",
            () -> new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DEEPSLATE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> FLUORITE_ORE = registerBlock("fluorite_ore",
            () -> new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> FLUORITE_ORE_DEEPSLATE = registerBlock("fluorite_ore_deepslate",
            () -> new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DEEPSLATE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> SEQUESTRUM_ORE = registerBlock("sequestrum_ore",
            () -> new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> SEQUESTRUM_ORE_DEEPSLATE = registerBlock("sequestrum_ore_deepslate",
            () -> new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DEEPSLATE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> SULFUR_ORE = registerBlock("sulfur_ore",
            () -> new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> SULFUR_ORE_DEEPSLATE = registerBlock("sulfur_ore_deepslate",
            () -> new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DEEPSLATE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONGLOMERATE = registerBlock("conglomerate",
            () -> new com.trd.block.basic.conglomerate.ConglomerateBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE)
                    .mapColor(net.minecraft.world.level.material.MapColor.STONE).strength(-1.0F, 3600000.0F).noOcclusion().isValidSpawn((state, getter, pos, entityType) -> false)));
    public static final DeferredBlock<Block> DEPLETED_CONGLOMERATE = registerBlock("depleted_conglomerate",
            () -> new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE)
                    .mapColor(net.minecraft.world.level.material.MapColor.STONE).strength(-1.0F, 3600000.0F).isValidSpawn((state, getter, pos, entityType) -> false)));

    public static java.util.List<DeferredBlock<Block>> BATTERY_BLOCKS = new java.util.ArrayList<>();

    public static final DeferredBlock<Block> MACHINE_BATTERY = registerBattery("machine_battery");

    public static final DeferredBlock<Block> CONVERTER_BLOCK = registerBlock("converter_block", () -> new com.trd.block.basic.industrial.energy.ConverterBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK)));
    public static final DeferredBlock<Block> WIRE_COATED = registerBlock("wire_coated", () -> new com.trd.block.basic.industrial.energy.WireBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).noOcclusion()));
    public static final DeferredBlock<Block> SWITCH = registerBlock("switch", () -> new com.trd.block.basic.industrial.energy.SwitchBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK)));
    public static final DeferredBlock<Block> CONNECTOR = registerBlock("connector", () -> new com.trd.block.basic.industrial.energy.ConnectorBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK), new com.trd.api.energy.ConnectorTier(16, 3, 0.03125f, 4, 6)));
    public static final DeferredBlock<Block> MEDIUM_CONNECTOR = registerBlock("medium_connector", () -> new com.trd.block.basic.industrial.energy.ConnectorBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK), new com.trd.api.energy.ConnectorTier(32, 7, 0.035f, 6, 8)));
    public static final DeferredBlock<Block> LARGE_CONNECTOR = registerBlock("large_connector", () -> new com.trd.block.basic.industrial.energy.ConnectorBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK), new com.trd.api.energy.ConnectorTier(200, 11, 0.055f, 8, 13)));
    public static final DeferredBlock<Block> PAINTABLE_WIRE = registerBlock("paintable_wire", () -> new com.trd.block.basic.industrial.energy.PaintableWireBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).noOcclusion()));
    public static final DeferredBlock<Block> ELECTRO_FURNACE = registerBlock("electro_furnace", () -> new com.trd.block.basic.industrial.ElectricFurnaceBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK)));

    public static final DeferredBlock<Block> MILLSTONE = registerBlock("millstone",
            () -> new com.trd.block.basic.industrial.MillstoneBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    // Fluid Barrels
    public static final DeferredBlock<net.minecraft.world.level.block.Block> CORRUPTED_BARREL = registerBlock("corrupted_barrel", () -> new com.trd.block.basic.industrial.fluids.FluidBarrelBlock(com.trd.api.fluids.system.BarrelTier.CORRUPTED, net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> LEAKING_BARREL = registerBlock("leaking_barrel", () -> new com.trd.block.basic.industrial.fluids.FluidBarrelBlock(com.trd.api.fluids.system.BarrelTier.LEAKING, net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> IRON_BARREL = registerBlock("iron_barrel", () -> new com.trd.block.basic.industrial.fluids.FluidBarrelBlock(com.trd.api.fluids.system.BarrelTier.IRON, net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> STEEL_BARREL = registerBlock("steel_barrel", () -> new com.trd.block.basic.industrial.fluids.FluidBarrelBlock(com.trd.api.fluids.system.BarrelTier.STEEL, net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> LEAD_BARREL = registerBlock("lead_barrel", () -> new com.trd.block.basic.industrial.fluids.FluidBarrelBlock(com.trd.api.fluids.system.BarrelTier.LEAD, net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> DECO_BARREL = registerBlock("deco_barrel", () -> new com.trd.block.basic.industrial.fluids.FluidBarrelBlock(com.trd.api.fluids.system.BarrelTier.IRON, net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));

    // Fluid Pipes
    public static final DeferredBlock<net.minecraft.world.level.block.Block> BRONZE_FLUID_PIPE = registerBlock("bronze_fluid_pipe", () -> new com.trd.block.basic.industrial.fluids.FluidPipeBlock(com.trd.api.fluids.system.PipeTier.BRONZE, net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> STEEL_FLUID_PIPE = registerBlock("steel_fluid_pipe", () -> new com.trd.block.basic.industrial.fluids.FluidPipeBlock(com.trd.api.fluids.system.PipeTier.STEEL, net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> LEAD_FLUID_PIPE = registerBlock("lead_fluid_pipe", () -> new com.trd.block.basic.industrial.fluids.FluidPipeBlock(com.trd.api.fluids.system.PipeTier.LEAD, net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> TUNGSTEN_FLUID_PIPE = registerBlock("tungsten_fluid_pipe", () -> new com.trd.block.basic.industrial.fluids.FluidPipeBlock(com.trd.api.fluids.system.PipeTier.TUNGSTEN, net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));

    // Conveyors
    public static final DeferredBlock<net.minecraft.world.level.block.Block> CONVEYOR = registerBlock("conveyor", () -> new com.trd.block.basic.industrial.ConveyorBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).noOcclusion()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> CONVEYOR_ELEVATOR = registerBlock("conveyor_elevator", () -> new com.trd.block.basic.industrial.ConveyorElevatorBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).noOcclusion()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> CONVEYOR_VSTAVSHIK = registerBlock("conveyor_vstavshik", () -> new com.trd.block.basic.industrial.ConveyorInserterBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK)));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> CONVEYOR_IZVLEKATEL = registerBlock("conveyor_izvlekatel", () -> new com.trd.block.basic.industrial.ConveyorExtractorBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK)));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> SORTIROVSHIK = registerBlock("sortirovshik", () -> new com.trd.block.basic.industrial.SortirovshikBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).noOcclusion()));

    public static final DeferredBlock<net.minecraft.world.level.block.Block> PIPE_SPOTS = BLOCKS.register("pipe_spots", () -> new com.trd.block.basic.industrial.fluids.FluidPipeBlock(com.trd.api.fluids.system.PipeTier.BRONZE, net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).noOcclusion().noCollission()));
    
    // Other Fluid Blocks
    public static final DeferredBlock<com.trd.block.basic.industrial.fluids.WaterPumpBlock> WATER_PUMP = registerMultiblock("water_pump", () -> new com.trd.block.basic.industrial.fluids.WaterPumpBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).noOcclusion()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> VALVE = registerBlock("valve", () -> new com.trd.block.basic.industrial.fluids.ValveBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> LOW_PRESSURE_STEAM_CONDENSER = registerBlock("low_pressure_steam_condenser", () -> new com.trd.block.basic.industrial.fluids.LowPressureSteamCondenserBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).strength(0.5F, 6.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> PAINTABLE_PIPE = registerBlock("paintable_pipe", () -> new com.trd.block.basic.industrial.fluids.PaintablePipeBlock(com.trd.api.fluids.system.PipeTier.BRONZE, net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).noOcclusion()));

    // Power Plant & Multiblocks
    public static final DeferredBlock<com.trd.multiblock.industrial.heaters.HeaterBlock> HEATER = registerMultiblock("heater", () -> new com.trd.multiblock.industrial.heaters.HeaterBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).noOcclusion()));
    public static final DeferredBlock<com.trd.multiblock.industrial.boiler.BoilerBlock> BOILER = registerMultiblock("boiler", () -> new com.trd.multiblock.industrial.boiler.BoilerBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).noOcclusion()));
    public static final DeferredBlock<com.trd.multiblock.industrial.steam_engine.SteamEngineBlock> STEAM_ENGINE = registerMultiblock("steam_engine", () -> new com.trd.multiblock.industrial.steam_engine.SteamEngineBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).noOcclusion()));
    public static final DeferredBlock<Block> STATOR_BLOCK = BLOCKS.register("stator", () -> new com.trd.block.basic.industrial.rotation.StatorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().strength(5.0f, 6.0f).requiresCorrectToolForDrops()));

    // Fuel Tanks
    public static final DeferredBlock<net.minecraft.world.level.block.Block> FUEL_TANK_BIG = BLOCKS.register("fuel_tank_big", () -> new com.trd.multiblock.industrial.fueltanks.FuelTankBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().noOcclusion()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> FUEL_TANK_SMALL = BLOCKS.register("fuel_tank_small", () -> new com.trd.multiblock.industrial.fueltanks.small.FuelTankSmallBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().noOcclusion()));


    // Kinetic / Rotation Blocks
    public static final java.util.List<DeferredBlock<Block>> ALL_SHAFTS = new java.util.ArrayList<>();

    public static final DeferredBlock<Block> BEARING_BLOCK = registerBlock("bearing",
            () -> new com.trd.block.basic.industrial.rotation.BearingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion().strength(5.0f, 6.0f).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CLUTCH = registerBlock("clutch",
            () -> new com.trd.block.basic.industrial.rotation.ClutchBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion().strength(5.0f, 6.0f).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> MOTOR_ELECTRO = registerBlock("motor_electro",
            () -> new com.trd.block.basic.industrial.rotation.MotorElectroBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion().strength(5.0f, 6.0f).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> TACHOMETER = registerBlock("tachometer",
            () -> new com.trd.block.basic.industrial.rotation.TachometerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion().strength(5.0f, 6.0f).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> HAND_CRANK_BLOCK = registerBlock("hand_crank",
            com.trd.block.basic.industrial.rotation.HandCrankBlock::new);

    public static final DeferredBlock<Block> SHAFT_LIGHT_IRON = registerShaft(ShaftMaterial.IRON, ShaftDiameter.LIGHT);
    public static final DeferredBlock<Block> SHAFT_MEDIUM_IRON = registerShaft(ShaftMaterial.IRON, ShaftDiameter.MEDIUM);
    public static final DeferredBlock<Block> SHAFT_HEAVY_IRON = registerShaft(ShaftMaterial.IRON, ShaftDiameter.HEAVY);

    public static final DeferredBlock<Block> SHAFT_LIGHT_DURALUMIN = registerShaft(ShaftMaterial.DURALUMIN, ShaftDiameter.LIGHT);
    public static final DeferredBlock<Block> SHAFT_MEDIUM_DURALUMIN = registerShaft(ShaftMaterial.DURALUMIN, ShaftDiameter.MEDIUM);
    public static final DeferredBlock<Block> SHAFT_HEAVY_DURALUMIN = registerShaft(ShaftMaterial.DURALUMIN, ShaftDiameter.HEAVY);

    public static final DeferredBlock<Block> SHAFT_LIGHT_STEEL = registerShaft(ShaftMaterial.STEEL, ShaftDiameter.LIGHT);
    public static final DeferredBlock<Block> SHAFT_MEDIUM_STEEL = registerShaft(ShaftMaterial.STEEL, ShaftDiameter.MEDIUM);
    public static final DeferredBlock<Block> SHAFT_HEAVY_STEEL = registerShaft(ShaftMaterial.STEEL, ShaftDiameter.HEAVY);

    public static final DeferredBlock<Block> SHAFT_LIGHT_TITANIUM = registerShaft(ShaftMaterial.TITANIUM, ShaftDiameter.LIGHT);
    public static final DeferredBlock<Block> SHAFT_MEDIUM_TITANIUM = registerShaft(ShaftMaterial.TITANIUM, ShaftDiameter.MEDIUM);
    public static final DeferredBlock<Block> SHAFT_HEAVY_TITANIUM = registerShaft(ShaftMaterial.TITANIUM, ShaftDiameter.HEAVY);

    public static final DeferredBlock<Block> SHAFT_LIGHT_TUNGSTEN_CARBIDE = registerShaft(ShaftMaterial.TUNGSTEN_CARBIDE, ShaftDiameter.LIGHT);
    public static final DeferredBlock<Block> SHAFT_MEDIUM_TUNGSTEN_CARBIDE = registerShaft(ShaftMaterial.TUNGSTEN_CARBIDE, ShaftDiameter.MEDIUM);
    public static final DeferredBlock<Block> SHAFT_HEAVY_TUNGSTEN_CARBIDE = registerShaft(ShaftMaterial.TUNGSTEN_CARBIDE, ShaftDiameter.HEAVY);

    private static DeferredBlock<Block> registerShaft(ShaftMaterial mat, ShaftDiameter dia) {
        String name = "shaft_" + dia.name + "_" + mat.name();
        DeferredBlock<Block> shaft = registerBlock(name, () -> new com.trd.block.basic.industrial.rotation.ShaftBlock(BlockBehaviour.Properties.of().strength(2.0f), mat, dia));
        ALL_SHAFTS.add(shaft);
        return shaft;
    }

    public static DeferredBlock<Block> getShaft(ShaftMaterial mat, ShaftDiameter dia) {
        if (mat == ShaftMaterial.IRON && dia == ShaftDiameter.LIGHT) return SHAFT_LIGHT_IRON;
        if (mat == ShaftMaterial.IRON && dia == ShaftDiameter.MEDIUM) return SHAFT_MEDIUM_IRON;
        if (mat == ShaftMaterial.IRON && dia == ShaftDiameter.HEAVY) return SHAFT_HEAVY_IRON;

        if (mat == ShaftMaterial.DURALUMIN && dia == ShaftDiameter.LIGHT) return SHAFT_LIGHT_DURALUMIN;
        if (mat == ShaftMaterial.DURALUMIN && dia == ShaftDiameter.MEDIUM) return SHAFT_MEDIUM_DURALUMIN;
        if (mat == ShaftMaterial.DURALUMIN && dia == ShaftDiameter.HEAVY) return SHAFT_HEAVY_DURALUMIN;

        if (mat == ShaftMaterial.STEEL && dia == ShaftDiameter.LIGHT) return SHAFT_LIGHT_STEEL;
        if (mat == ShaftMaterial.STEEL && dia == ShaftDiameter.MEDIUM) return SHAFT_MEDIUM_STEEL;
        if (mat == ShaftMaterial.STEEL && dia == ShaftDiameter.HEAVY) return SHAFT_HEAVY_STEEL;

        if (mat == ShaftMaterial.TITANIUM && dia == ShaftDiameter.LIGHT) return SHAFT_LIGHT_TITANIUM;
        if (mat == ShaftMaterial.TITANIUM && dia == ShaftDiameter.MEDIUM) return SHAFT_MEDIUM_TITANIUM;
        if (mat == ShaftMaterial.TITANIUM && dia == ShaftDiameter.HEAVY) return SHAFT_HEAVY_TITANIUM;

        if (mat == ShaftMaterial.TUNGSTEN_CARBIDE && dia == ShaftDiameter.LIGHT) return SHAFT_LIGHT_TUNGSTEN_CARBIDE;
        if (mat == ShaftMaterial.TUNGSTEN_CARBIDE && dia == ShaftDiameter.MEDIUM) return SHAFT_MEDIUM_TUNGSTEN_CARBIDE;
        if (mat == ShaftMaterial.TUNGSTEN_CARBIDE && dia == ShaftDiameter.HEAVY) return SHAFT_HEAVY_TUNGSTEN_CARBIDE;

        return SHAFT_LIGHT_IRON;
    }

    // Machines
    public static final DeferredBlock<Block> OPTIC_MICROSCOPE = registerBlock("optic_microscope",
            () -> new com.trd.block.basic.industrial.OpticMicroscopeBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    // Multiblock
    public static final DeferredBlock<Block> MULTIBLOCK_PART = BLOCKS.register("multiblock_part",
            () -> new com.trd.multiblock.system.MultiblockPartBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().isViewBlocking((state, getter, pos) -> false)));

    // Metallurgy & Casting
    public static final DeferredBlock<Block> SMELTER = BLOCKS.register("smelter",
            () -> new com.trd.multiblock.industrial.smelter.SmelterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().strength(3.0f, 10.0f)));

    public static final DeferredBlock<Block> CC_MACHINE = BLOCKS.register("cc_machine",
            () -> new com.trd.multiblock.industrial.ccmachine.CCMachineBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F, 6.0F).sound(net.minecraft.world.level.block.SoundType.NETHER_BRICKS).requiresCorrectToolForDrops().noOcclusion()));

    public static final DeferredBlock<Block> CASTING_POT = registerBlock("casting_pot",
            () -> new com.trd.block.basic.industrial.casting.CastingPotBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    public static final DeferredBlock<Block> CASTING_DESCENT = registerBlock("casting_descent",
            () -> new com.trd.block.basic.industrial.casting.CastingDescentBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    public static final DeferredBlock<Block> DROBITEL = BLOCKS.register("drobitel",
            () -> new com.trd.multiblock.industrial.drobitel.DrobitelBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().strength(3.0f, 10.0f)));

    public static final DeferredBlock<Block> CENTRIFUGE_MOTOR = registerBlock("centrifuge_motor",
            () -> new com.trd.multiblock.industrial.centrifuge.CentrifugeMotorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().strength(3.0f, 10.0f)));

    public static final DeferredBlock<com.trd.multiblock.industrial.centrifuge.conus.CentrifugeConusBlock> CENTRIFUGE_CONUS = registerMultiblock("centrifuge_conus",
            () -> new com.trd.multiblock.industrial.centrifuge.conus.CentrifugeConusBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().strength(3.0f, 10.0f)));

    public static final DeferredBlock<com.trd.multiblock.industrial.centrifuge.cylinder.CentrifugeCylinderBlock> CENTRIFUGE_CYLINDER = registerMultiblock("centrifuge_cylinder",
            () -> new com.trd.multiblock.industrial.centrifuge.cylinder.CentrifugeCylinderBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().strength(3.0f, 10.0f)));

    public static final DeferredBlock<Block> VISHELASHIVATEL = BLOCKS.register("vishelashivatel",
            () -> new com.trd.multiblock.industrial.vishelashivatel.VishelashivatelBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().strength(3.0f, 10.0f)));

    public static final DeferredBlock<com.trd.multiblock.industrial.coccer.CoccerOvenBlock> COCCER_OVEN = registerMultiblock("coccer_oven",
            () -> new com.trd.multiblock.industrial.coccer.CoccerOvenBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_BRICKS).strength(3.0f, 10.0f).noOcclusion()));

    public static final DeferredBlock<com.trd.multiblock.industrial.stanok.StanokBlock> STANOK = registerMultiblock("stanok",
            () -> new com.trd.multiblock.industrial.stanok.StanokBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().strength(3.0f, 10.0f)));

    public static final DeferredBlock<com.trd.multiblock.industrial.steel_storage.SteelStorageBlock> STEEL_STORAGE = registerMultiblock("steel_storage",
            () -> new com.trd.multiblock.industrial.steel_storage.SteelStorageBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().strength(2.5f, 6.0f)));

    public static final DeferredBlock<Block> MORY_BLOCK = registerBlock("mory_block",
            () -> new Block(BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> ANTON_CHIGUR = registerBlock("anton_chigur",
            () -> new Block(BlockBehaviour.Properties.of().strength(1.0F, 3.0F).sound(net.minecraft.world.level.block.SoundType.WOOD).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE = registerBlock("concrete",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0F, 54.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    // ═══════════════════════════════════════════════════════
    // БЕТОННАЯ ЛИНЕЙКА (строительные блоки)
    // ═══════════════════════════════════════════════════════

    public static final DeferredBlock<Block> CONCRETE_LINE = registerBlock("concrete_line",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0F, 54.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_CUT = registerBlock("concrete_cut",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0F, 54.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_RAIL = registerBlock("concrete_rail",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0F, 54.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_VENT = registerBlock("concrete_vent",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0F, 54.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_REBAR = registerBlock("concrete_rebar",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(10.0F, 90.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_MOSSY = registerBlock("concrete_mossy",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0F, 54.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_OLD = registerBlock("concrete_old",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.5F, 30.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_HAZARD_NEW = registerBlock("concrete_hazard_new",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0F, 54.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_HAZARD_OLD = registerBlock("concrete_hazard_old",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0F, 54.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_TILE = registerBlock("concrete_tile",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.5F, 60.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_TILE_ALT = registerBlock("concrete_tile_alt",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.5F, 60.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_TILE_ALT_BLUE = registerBlock("concrete_tile_alt_blue",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.5F, 60.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_STRIPPED = registerBlock("concrete_stripped",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0F, 48.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_REINFORCED = registerBlock("concrete_reinforced",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0F, 48.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_REINFORCED_HEAVY = registerBlock("concrete_reinforced_heavy",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0F, 48.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> CONCRETE_NET = registerBlock("concrete_net",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(5.5F, 135.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<net.minecraft.world.level.block.TintedGlassBlock> ARMORED_GLASS = registerBlock("armored_glass",
            () -> new net.minecraft.world.level.block.TintedGlassBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F, 24.0F)
                    .sound(net.minecraft.world.level.block.SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, entity) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));

    // Бетонные плиты и лестницы
    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> CONCRETE_STAIRS = registerBlock("concrete_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(CONCRETE.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(CONCRETE.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> CONCRETE_SLAB = registerBlock("concrete_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(CONCRETE.get())));

    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> CONCRETE_MOSSY_STAIRS = registerBlock("concrete_mossy_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(CONCRETE_MOSSY.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(CONCRETE_MOSSY.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> CONCRETE_MOSSY_SLAB = registerBlock("concrete_mossy_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(CONCRETE_MOSSY.get())));

    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> CONCRETE_OLD_STAIRS = registerBlock("concrete_old_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(CONCRETE_OLD.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(CONCRETE_OLD.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> CONCRETE_OLD_SLAB = registerBlock("concrete_old_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(CONCRETE_OLD.get())));

    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> CONCRETE_HAZARD_NEW_STAIRS = registerBlock("concrete_hazard_new_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(CONCRETE_HAZARD_NEW.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(CONCRETE_HAZARD_NEW.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> CONCRETE_HAZARD_NEW_SLAB = registerBlock("concrete_hazard_new_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(CONCRETE_HAZARD_NEW.get())));

    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> CONCRETE_HAZARD_OLD_STAIRS = registerBlock("concrete_hazard_old_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(CONCRETE_HAZARD_OLD.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(CONCRETE_HAZARD_OLD.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> CONCRETE_HAZARD_OLD_SLAB = registerBlock("concrete_hazard_old_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(CONCRETE_HAZARD_OLD.get())));

    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> CONCRETE_TILE_STAIRS = registerBlock("concrete_tile_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(CONCRETE_TILE.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(CONCRETE_TILE.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> CONCRETE_TILE_SLAB = registerBlock("concrete_tile_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(CONCRETE_TILE.get())));

    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> CONCRETE_TILE_ALT_STAIRS = registerBlock("concrete_tile_alt_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(CONCRETE_TILE_ALT.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(CONCRETE_TILE_ALT.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> CONCRETE_TILE_ALT_SLAB = registerBlock("concrete_tile_alt_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(CONCRETE_TILE_ALT.get())));

    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> CONCRETE_TILE_ALT_BLUE_STAIRS = registerBlock("concrete_tile_alt_blue_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(CONCRETE_TILE_ALT_BLUE.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(CONCRETE_TILE_ALT_BLUE.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> CONCRETE_TILE_ALT_BLUE_SLAB = registerBlock("concrete_tile_alt_blue_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(CONCRETE_TILE_ALT_BLUE.get())));

    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> CONCRETE_STRIPPED_STAIRS = registerBlock("concrete_stripped_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(CONCRETE_STRIPPED.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(CONCRETE_STRIPPED.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> CONCRETE_STRIPPED_SLAB = registerBlock("concrete_stripped_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(CONCRETE_STRIPPED.get())));

    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> CONCRETE_REINFORCED_STAIRS = registerBlock("concrete_reinforced_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(CONCRETE_REINFORCED.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(CONCRETE_REINFORCED.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> CONCRETE_REINFORCED_SLAB = registerBlock("concrete_reinforced_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(CONCRETE_REINFORCED.get())));

    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> CONCRETE_REINFORCED_HEAVY_STAIRS = registerBlock("concrete_reinforced_heavy_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(CONCRETE_REINFORCED_HEAVY.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(CONCRETE_REINFORCED_HEAVY.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> CONCRETE_REINFORCED_HEAVY_SLAB = registerBlock("concrete_reinforced_heavy_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(CONCRETE_REINFORCED_HEAVY.get())));

    // ═══════════════════════════════════════════════════════
    // СБОРНЫЕ / АРМИРОВАННЫЕ БЛОКИ
    // ═══════════════════════════════════════════════════════

    public static final DeferredBlock<Block> CONCRETE_CONSTRUCT_BLOCK = registerBlock("concrete_construct_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(15.0F, 270.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> CONCRETE_CONSTRUCT_BLOCK_STAIRS = registerBlock("concrete_construct_block_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(CONCRETE_CONSTRUCT_BLOCK.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(CONCRETE_CONSTRUCT_BLOCK.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> CONCRETE_CONSTRUCT_BLOCK_SLAB = registerBlock("concrete_construct_block_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(CONCRETE_CONSTRUCT_BLOCK.get())));

    public static final DeferredBlock<Block> STEEL_CONSTRUCT_BLOCK = registerBlock("steel_construct_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(10.0F, 60.0F).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> STEEL_CONSTRUCT_BLOCK_STAIRS = registerBlock("steel_construct_block_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(STEEL_CONSTRUCT_BLOCK.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(STEEL_CONSTRUCT_BLOCK.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> STEEL_CONSTRUCT_BLOCK_SLAB = registerBlock("steel_construct_block_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(STEEL_CONSTRUCT_BLOCK.get())));

    public static final DeferredBlock<Block> STEEL_CONSTRUCT_BLOCK_REINFORCED = registerBlock("steel_construct_block_reinforced",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(30.0F, 380.0F).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> STEEL_CONSTRUCT_BLOCK_REINFORCED_STAIRS = registerBlock("steel_construct_block_reinforced_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(STEEL_CONSTRUCT_BLOCK_REINFORCED.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(STEEL_CONSTRUCT_BLOCK_REINFORCED.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> STEEL_CONSTRUCT_BLOCK_REINFORCED_SLAB = registerBlock("steel_construct_block_reinforced_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(STEEL_CONSTRUCT_BLOCK_REINFORCED.get())));

    // ═══════════════════════════════════════════════════════
    // МИНЕРАЛЫ / КАМНИ / ПЛИТКА
    // ═══════════════════════════════════════════════════════

    public static final DeferredBlock<Block> MINERAL_BLOCK2 = registerBlock("mineral_block2",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0F, 9.0F).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> MINERAL_TILE = registerBlock("mineral_tile",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.5F, 10.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> DOLOMITE_TILE = registerBlock("dolomite_tile",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.0F, 9.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> TILE_LIGHT = registerBlock("tile_light",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(2.5F, 8.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> SULFUR_TILE = registerBlock("sulfur_tile",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(2.0F, 6.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> SULFUR_BRICKS = registerBlock("sulfur_bricks",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(2.5F, 7.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    // ═══════════════════════════════════════════════════════
    // МЕТАЛЛЫ / СТАЛЬ / БАЛКИ
    // ═══════════════════════════════════════════════════════

    public static final DeferredBlock<Block> DECO_STEEL = registerBlock("deco_steel",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(5.0F, 12.0F).sound(net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> DECO_STEEL_DARK = registerBlock("deco_steel_dark",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(5.0F, 12.0F).sound(net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> DECO_STEEL_SMOG = registerBlock("deco_steel_smog",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(5.0F, 12.0F).sound(net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> DECO_LEAD = registerBlock("deco_lead",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3.5F, 10.0F).sound(net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> DECO_BEAM = registerBlock("deco_beam",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.0F, 10.0F).sound(net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()));

    public static final DeferredBlock<com.trd.block.basic.deco.BeamBlock> BEAM_BLOCK = registerBlock("beam_block",
            () -> new com.trd.block.basic.deco.BeamBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0f, 12.0f).noOcclusion().requiresCorrectToolForDrops()));

    public static final DeferredBlock<com.trd.block.basic.deco.SteelPropsBlock> STEEL_PROPS = registerBlock("steel_props",
            () -> new com.trd.block.basic.deco.SteelPropsBlock(BlockBehaviour.Properties.of()
                    .strength(4.5F, 11.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));

    public static final DeferredBlock<com.trd.block.basic.deco.BarbedWireBlock> BARBED_WIRE = registerBlock("barbed_wire",
            () -> new com.trd.block.basic.deco.BarbedWireBlock(BlockBehaviour.Properties.of()
                    .strength(2F, 6.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops().noOcclusion().noCollission()));

    public static final DeferredBlock<com.trd.block.basic.deco.WireFenceBlock> WIRE_FENCE = registerBlock("wire_fence",
            () -> new com.trd.block.basic.deco.WireFenceBlock(BlockBehaviour.Properties.of()
                    .strength(2F, 6.0F).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().forceSolidOn()));

    public static final DeferredBlock<com.trd.block.basic.deco.WireFenceAltBlock> WIRE_FENCE_ALT = registerBlock("wire_fence_alt",
            () -> new com.trd.block.basic.deco.WireFenceAltBlock(BlockBehaviour.Properties.of()
                    .strength(2F, 6.0F).sound(net.minecraft.world.level.block.SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().forceSolidOn()));

    public static final DeferredBlock<com.trd.block.basic.deco.LampBlock> ROUND_LAMP = registerBlock("round_lamp",
            () -> new com.trd.block.basic.deco.LampBlock(BlockBehaviour.Properties.of()
                    .strength(6F, 60F)
                    .sound(net.minecraft.world.level.block.SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> 15)));

    // Невидимые блоки коллизии под лучом (ставятся BeamPlacerItem)
    public static final DeferredBlock<com.trd.block.basic.deco.BeamCollisionBlock> BEAM_COLLISION = BLOCKS.register("beam_collision",
            () -> new com.trd.block.basic.deco.BeamCollisionBlock(BlockBehaviour.Properties.of()
                    .strength(2.0f, 6.0f)
                    .noOcclusion()
                    .noLootTable()));

    // ═══════════════════════════════════════════════════════
    // ДЕРЕВО
    // ═══════════════════════════════════════════════════════

    public static final DeferredBlock<Block> SEQUOIA_PLANKS = registerBlock("sequoia_planks",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                    .strength(2.5f, 4.0f).requiresCorrectToolForDrops()));
    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> SEQUOIA_STAIRS = registerBlock("sequoia_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(SEQUOIA_PLANKS.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(SEQUOIA_PLANKS.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> SEQUOIA_SLAB = registerBlock("sequoia_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(SEQUOIA_PLANKS.get())));

    public static final DeferredBlock<Block> WASTE_PLANKS = registerBlock("waste_planks",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)));
    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> WASTE_PLANKS_STAIRS = registerBlock("waste_planks_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(WASTE_PLANKS.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(WASTE_PLANKS.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> WASTE_PLANKS_SLAB = registerBlock("waste_planks_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(WASTE_PLANKS.get())));

    public static final DeferredBlock<net.minecraft.world.level.block.FenceBlock> WASTE_FENCE = registerBlock("waste_fence",
            () -> new net.minecraft.world.level.block.FenceBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE)));
    public static final DeferredBlock<net.minecraft.world.level.block.FenceGateBlock> WASTE_FENCE_GATE = registerBlock("waste_fence_gate",
            () -> new net.minecraft.world.level.block.FenceGateBlock(net.minecraft.world.level.block.state.properties.WoodType.DARK_OAK, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE_GATE)));
    public static final DeferredBlock<net.minecraft.world.level.block.PressurePlateBlock> WASTE_PRESSURE_PLATE = registerBlock("waste_pressure_plate",
            () -> new net.minecraft.world.level.block.PressurePlateBlock(net.minecraft.world.level.block.state.properties.BlockSetType.DARK_OAK,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PRESSURE_PLATE)));

    // ═══════════════════════════════════════════════════════
    // ВОРОНКА ВОДОРОДНОЙ ГРАНАТЫ И КОПОТЬ-ТИНТЫ (ExplosionHydrogen / ExplosionFire)
    // ═══════════════════════════════════════════════════════

    public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> WASTE_LOG = registerBlock("waste_log",
            () -> new net.minecraft.world.level.block.RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_WOOD)
                    .sound(net.minecraft.world.level.block.SoundType.WOOD)));

    public static final DeferredBlock<WasteGrassBlock> WASTE_GRASS = registerBlock("waste_grass",
            () -> new WasteGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK)
                    .sound(net.minecraft.world.level.block.SoundType.GRASS)));

    public static final DeferredBlock<Block> BASALT_ROUGH = registerBlock("basalt_rough",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.DEEPSLATE)
                    .strength(0.5F, 6.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<ScorchedBasaltBlock> BASALT_SCORCHED = registerBlock("basalt_scorched",
            () -> new ScorchedBasaltBlock(BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)
                    .strength(0.5F, 6.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<CraterBasaltBlock> BASALT_SOFT = registerBlock("basalt_soft",
            () -> new CraterBasaltBlock(BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.DEEPSLATE)
                    .strength(0.5F, 6.0F).sound(net.minecraft.world.level.block.SoundType.NETHERRACK).requiresCorrectToolForDrops()));

    public static final DeferredBlock<CraterBasaltBlock> BASALT_SOFT_2 = registerBlock("basalt_soft_2",
            () -> new CraterBasaltBlock(BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.DEEPSLATE)
                    .strength(0.5F, 6.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<CraterBasaltBlock> BASALT_SOFT_3 = registerBlock("basalt_soft_3",
            () -> new CraterBasaltBlock(BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.DEEPSLATE)
                    .strength(0.5F, 6.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    public static final DeferredBlock<CraterBasaltBlock> BASALT_SOFT_4 = registerBlock("basalt_soft_4",
            () -> new CraterBasaltBlock(BlockBehaviour.Properties.of()
                    .mapColor(net.minecraft.world.level.material.MapColor.DEEPSLATE)
                    .strength(0.5F, 6.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops()));

    // Армированное бетонное стекло: водородная/огненная волна его НЕ ломает (см. ExplosionHydrogen.isGlass).
    public static final DeferredBlock<Block> CONCRETE_ARMED_GLASS = registerBlock("concrete_armed_glass",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));

    // БЛОКИ УЛЬЯ
    public static final DeferredBlock<Block> DEPTH_WORM_NEST = registerBlock("depth_worm_nest",
            () -> new com.trd.block.basic.necrosis.hive.DepthWormNestBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.MUD).sound(net.minecraft.world.level.block.SoundType.MUD)));
    public static final DeferredBlock<Block> HIVE_SOIL = registerBlock("hive_soil",
            () -> new com.trd.block.basic.necrosis.hive.HiveSoilBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.MUD).sound(net.minecraft.world.level.block.SoundType.MUD)));
    public static final DeferredBlock<Block> DEPTH_WORM_NEST_DEAD = registerBlock("depth_worm_nest_dead",
            () -> new com.trd.block.basic.necrosis.hive.DepthWormNestBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.MUD).sound(net.minecraft.world.level.block.SoundType.MUD)));
    public static final DeferredBlock<Block> HIVE_SOIL_DEAD = registerBlock("hive_soil_dead",
            () -> new com.trd.block.basic.necrosis.hive.HiveSoilBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.MUD).sound(net.minecraft.world.level.block.SoundType.MUD)));
    public static final DeferredBlock<Block> HIVE_ROOTS = registerBlock("hive_roots",
            () -> new com.trd.block.basic.necrosis.hive.HiveRootsBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPORE_BLOSSOM).noCollission().instabreak()));


    // ДВЕРИ
    public static final DeferredBlock<net.minecraft.world.level.block.DoorBlock> SEQUOIA_DOOR = registerBlock("sequoia_door",
            () -> new net.minecraft.world.level.block.DoorBlock(net.minecraft.world.level.block.state.properties.BlockSetType.DARK_OAK,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.DARK_OAK_DOOR).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion()));

    public static final DeferredBlock<net.minecraft.world.level.block.TrapDoorBlock> SEQUOIA_TRAPDOOR = registerBlock("sequoia_trapdoor",
            () -> new net.minecraft.world.level.block.TrapDoorBlock(net.minecraft.world.level.block.state.properties.BlockSetType.DARK_OAK,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.DARK_OAK_TRAPDOOR).sound(net.minecraft.world.level.block.SoundType.WOOD).noOcclusion()));

    public static final DeferredBlock<net.minecraft.world.level.block.DoorBlock> STEEL_DOOR = registerBlock("steel_door",
            () -> new net.minecraft.world.level.block.DoorBlock(net.minecraft.world.level.block.state.properties.BlockSetType.STONE,
                    BlockBehaviour.Properties.of().strength(15.0F, 160.0F).sound(net.minecraft.world.level.block.SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<com.trd.block.basic.industrial.chemistry.ChemicalPlantHeaterBlock> CHEMICAL_PLANT_HEATER = registerBlock("chemical_plant_heater",
            () -> new com.trd.block.basic.industrial.chemistry.ChemicalPlantHeaterBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F, 6.0F)
                    .sound(net.minecraft.world.level.block.SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<com.trd.block.basic.industrial.chemistry.ChemicalPlantReactionChamberBlock> CHEMICAL_PLANT_REACTION_CHAMBER = registerBlock("chemical_plant_reaction_chamber",
            () -> new com.trd.block.basic.industrial.chemistry.ChemicalPlantReactionChamberBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F, 6.0F)
                    .sound(net.minecraft.world.level.block.SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<com.trd.block.basic.industrial.chemistry.ChemicalPlantPortBlock> CHEMICAL_PLANT_PORT = registerBlock("chemical_plant_port",
            () -> new com.trd.block.basic.industrial.chemistry.ChemicalPlantPortBlock(BlockBehaviour.Properties.of()
                    .strength(2.0F, 6.0F)
                    .sound(net.minecraft.world.level.block.SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<Block> FIREBRICK_BLOCK = registerBlock("firebrick_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS)));
    public static final DeferredBlock<Block> REINFORCEDBRICK_BLOCK = registerBlock("reinforcedbrick_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS)));
    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> FIREBRICK_STAIRS = registerBlock("firebrick_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(FIREBRICK_BLOCK.get().defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(FIREBRICK_BLOCK.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> REINFORCEDBRICK_STAIRS = registerBlock("reinforcedbrick_stairs",
            () -> new net.minecraft.world.level.block.StairBlock(REINFORCEDBRICK_BLOCK.get().defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(REINFORCEDBRICK_BLOCK.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> FIREBRICK_SLAB = registerBlock("firebrick_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(FIREBRICK_BLOCK.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> REINFORCEDBRICK_SLAB = registerBlock("reinforcedbrick_slab",
            () -> new net.minecraft.world.level.block.SlabBlock(BlockBehaviour.Properties.ofFullCopy(REINFORCEDBRICK_BLOCK.get())));
    public static final DeferredBlock<Block> SMALL_SMELTER = registerBlock("small_smelter",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().strength(3.0f, 10.0f)));
    public static final DeferredBlock<Block> DET_MINER = registerBlock("det_miner",
            () -> new com.trd.block.basic.weapons.explosives.DetMinerBlock(
                    BlockBehaviour.Properties.of().strength(0.5F, 6.0F)
                            .sound(net.minecraft.world.level.block.SoundType.STONE)
                            .requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> AVIABOMB_MINE = registerBlock("aviabomb_mine",
            () -> new com.trd.block.basic.weapons.explosives.SideOBlock(
                    BlockBehaviour.Properties.of().strength(2.0F, 6.0F).sound(net.minecraft.world.level.block.SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));

    private static <T extends Block & IMultiblockController> DeferredBlock<T> registerMultiblock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        ModItems.ITEMS.register(name, () -> new MultiblockBlockItem(toReturn.get(), new Item.Properties()));
        return toReturn;
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    @SafeVarargs
    private static void registerExplosionTooltip(DeferredHolder<Block, ? extends Block>... blocks) {
        for (DeferredHolder<Block, ? extends Block> block : blocks) {
            com.trd.api.tooltip.ExplosionTooltipRegistry.register(block);
        }
    }

    private static DeferredBlock<Block> registerBattery(String name) {
        DeferredBlock<Block> batteryBlock = BLOCKS.register(name, () -> new com.trd.block.basic.industrial.energy.MachineBatteryBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).strength(5.0f).requiresCorrectToolForDrops().noOcclusion()));
        com.trd.item.ModItems.ITEMS.register(name, () -> new com.trd.item.industrial.energy.MachineBatteryBlockItem(batteryBlock.get(), new net.minecraft.world.item.Item.Properties()));
        BATTERY_BLOCKS.add(batteryBlock);
        return batteryBlock;
    }

    static {
        // ═══════════════════════════════════════════════════════
        // БЕТОННАЯ ЛИНЕЙКА
        // ═══════════════════════════════════════════════════════
        registerExplosionTooltip(
                CONCRETE, CONCRETE_STAIRS, CONCRETE_SLAB,
                CONCRETE_MOSSY, CONCRETE_MOSSY_STAIRS, CONCRETE_MOSSY_SLAB,
                CONCRETE_OLD, CONCRETE_OLD_STAIRS, CONCRETE_OLD_SLAB,
                CONCRETE_HAZARD_NEW, CONCRETE_HAZARD_NEW_STAIRS, CONCRETE_HAZARD_NEW_SLAB,
                CONCRETE_HAZARD_OLD, CONCRETE_HAZARD_OLD_STAIRS, CONCRETE_HAZARD_OLD_SLAB,
                CONCRETE_TILE, CONCRETE_TILE_STAIRS, CONCRETE_TILE_SLAB,
                CONCRETE_TILE_ALT, CONCRETE_TILE_ALT_STAIRS, CONCRETE_TILE_ALT_SLAB,
                CONCRETE_TILE_ALT_BLUE, CONCRETE_TILE_ALT_BLUE_STAIRS, CONCRETE_TILE_ALT_BLUE_SLAB,
                CONCRETE_STRIPPED, CONCRETE_STRIPPED_STAIRS, CONCRETE_STRIPPED_SLAB,
                CONCRETE_REINFORCED, CONCRETE_REINFORCED_STAIRS, CONCRETE_REINFORCED_SLAB,
                CONCRETE_REINFORCED_HEAVY, CONCRETE_REINFORCED_HEAVY_STAIRS, CONCRETE_REINFORCED_HEAVY_SLAB,
                CONCRETE_CONSTRUCT_BLOCK, CONCRETE_CONSTRUCT_BLOCK_SLAB, CONCRETE_CONSTRUCT_BLOCK_STAIRS,
                STEEL_CONSTRUCT_BLOCK, STEEL_CONSTRUCT_BLOCK_SLAB, STEEL_CONSTRUCT_BLOCK_STAIRS,
                STEEL_CONSTRUCT_BLOCK_REINFORCED, STEEL_CONSTRUCT_BLOCK_REINFORCED_SLAB, STEEL_CONSTRUCT_BLOCK_REINFORCED_STAIRS,
                CONCRETE_NET);

        // ═══════════════════════════════════════════════════════
        // КИРПИЧИ / ПРОЧЕЕ
        // ═══════════════════════════════════════════════════════
        registerExplosionTooltip(
                FIREBRICK_BLOCK, FIREBRICK_STAIRS, FIREBRICK_SLAB,
                REINFORCEDBRICK_BLOCK, REINFORCEDBRICK_STAIRS, REINFORCEDBRICK_SLAB,
                ARMORED_GLASS, STEEL_DOOR, ROUND_LAMP,
                CONCRETE_LINE, CONCRETE_RAIL, CONCRETE_CUT, CONCRETE_REBAR, CONCRETE_VENT);
    }

    // === ТУРЕЛИ ===

    /**
     * Ракетница «Тромбон» — мультиблок-контроллер 1×1×2. Регистрируется через
     * {@link #registerMultiblock}, потому что предмет-форма тут обычный BlockItem
     * и кастомный BlockItem не требуется.
     */
    public static final DeferredBlock<com.trd.block.basic.weapons.MissileTurretBlock> TROMBONE =
            registerMultiblock("trombone",
                    () -> new com.trd.block.basic.weapons.MissileTurretBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                                    .strength(5.0f).requiresCorrectToolForDrops().noOcclusion()));

    /**
     * Буфер турели регистрируется НЕ через {@link #registerBlock}: тому helper всегда
     * вешает обычный {@link BlockItem}, а здесь нужен свой {@code TurretLightPlacerBlockItem}
     * (GeoItem с кастомным рендерером). Поэтому item-часть регистрируем вручную —
     * так же, как это делает {@link #registerBattery}.
     */
    public static final DeferredBlock<com.trd.block.basic.weapons.TurretLightPlacerBlock> TURRET_LIGHT_PLACER =
            registerTurretPlacer();

    private static DeferredBlock<com.trd.block.basic.weapons.TurretLightPlacerBlock> registerTurretPlacer() {
        DeferredBlock<com.trd.block.basic.weapons.TurretLightPlacerBlock> block = BLOCKS.register(
                "turret_light_placer",
                () -> new com.trd.block.basic.weapons.TurretLightPlacerBlock(
                        BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                                .strength(5.0f, 30.0f).noOcclusion().requiresCorrectToolForDrops()));
        com.trd.item.ModItems.ITEMS.register("turret_light_placer",
                () -> new com.trd.item.weapons.turrets.TurretLightPlacerBlockItem(
                        block.get(), new Item.Properties()));
        com.trd.api.tooltip.MachineTooltipRegistry.registerBlock(block, "tooltip.trd.machine.turret_light.desc");
        return block;
    }

    // Визуальный прокси-блок для ракеты: MissileLightRenderer рендерит его
    // (модель .obj) в мире у сущности. Ставить руками нельзя.
    public static final DeferredBlock<Block> MISSILE_LIGHT = registerBlock("missile_light",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                    .strength(5.0f, 30.0f).noOcclusion().requiresCorrectToolForDrops()));

    public static void register(net.neoforged.bus.api.IEventBus eventBus) {
        BLOCKS.register(eventBus);

        ModItems.ITEMS.register("fuel_tank_big", () -> new com.trd.multiblock.system.FuelTankBlockItem(FUEL_TANK_BIG.get(), 2592000, new net.minecraft.world.item.Item.Properties()));
        ModItems.ITEMS.register("fuel_tank_small", () -> new com.trd.multiblock.system.FuelTankBlockItem(FUEL_TANK_SMALL.get(), 288000, new net.minecraft.world.item.Item.Properties()));

        com.trd.api.tooltip.MachineTooltipRegistry.registerBlock(TROMBONE, "tooltip.trd.machine.trombone.desc");
    }
}
