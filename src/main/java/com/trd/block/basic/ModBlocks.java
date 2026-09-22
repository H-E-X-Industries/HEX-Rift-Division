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
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MainRegistry.MOD_ID);

    // Пример простых блоков (без кастомной логики)
    public static final DeferredBlock<Block> ASBESOTS_ORE = registerBlock("asbestos_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));
    
    public static final DeferredBlock<Block> LIGNITE_ORE = registerBlock("lignite_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.COAL_ORE)));

    public static final DeferredBlock<Block> SALT_ORE = registerBlock("salt_ore",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));

    public static final DeferredBlock<Block> BAUXITE = registerBlock("bauxite",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)));

    public static final DeferredBlock<Block> DOLOMITE = registerBlock("dolomite",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)));

    public static final DeferredBlock<Block> LIMESTONE = registerBlock("limestone",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)));

    public static final DeferredBlock<Block> SULFUR_CLUSTER = registerBlock("sulfur_cluster",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)));

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

    public static final DeferredBlock<net.minecraft.world.level.block.Block> PIPE_SPOTS = registerBlock("pipe_spots", () -> new com.trd.block.basic.industrial.fluids.FluidPipeBlock(com.trd.api.fluids.system.PipeTier.BRONZE, net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).noOcclusion().noCollission()));
    
    // Other Fluid Blocks
    public static final DeferredBlock<net.minecraft.world.level.block.Block> WATER_PUMP = registerBlock("water_pump", () -> new net.minecraft.world.level.block.Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> VALVE = registerBlock("valve", () -> new com.trd.block.basic.industrial.fluids.ValveBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> LOW_PRESSURE_STEAM_CONDENSER = registerBlock("low_pressure_steam_condenser", () -> new com.trd.block.basic.industrial.fluids.LowPressureSteamCondenserBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> PAINTABLE_PIPE = registerBlock("paintable_pipe", () -> new com.trd.block.basic.industrial.fluids.PaintablePipeBlock(com.trd.api.fluids.system.PipeTier.BRONZE, net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).noOcclusion()));

    // Fuel Tanks
    public static final DeferredBlock<net.minecraft.world.level.block.Block> FUEL_TANK_BIG = BLOCKS.register("fuel_tank_big", () -> new com.trd.multiblock.industrial.fueltanks.FuelTankBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().noOcclusion()));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> FUEL_TANK_SMALL = BLOCKS.register("fuel_tank_small", () -> new com.trd.multiblock.industrial.fueltanks.small.FuelTankSmallBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().noOcclusion()));


    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static DeferredBlock<Block> registerBattery(String name) {
        DeferredBlock<Block> batteryBlock = BLOCKS.register(name, () -> new com.trd.block.basic.industrial.energy.MachineBatteryBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BLOCK).strength(5.0f).requiresCorrectToolForDrops().noOcclusion()));
        com.trd.item.ModItems.ITEMS.register(name, () -> new com.trd.item.industrial.energy.MachineBatteryBlockItem(batteryBlock.get(), new net.minecraft.world.item.Item.Properties()));
        BATTERY_BLOCKS.add(batteryBlock);
        return batteryBlock;
    }

    public static final DeferredBlock<Block> MULTIBLOCK_PART = registerBlock("multiblock_part",
            () -> new com.trd.multiblock.system.MultiblockPartBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().isViewBlocking((state, getter, pos) -> false)));

    public static void register(net.neoforged.bus.api.IEventBus eventBus) {
        BLOCKS.register(eventBus);

        ModItems.ITEMS.register("fuel_tank_big", () -> new com.trd.multiblock.system.FuelTankBlockItem(FUEL_TANK_BIG.get(), 2592000, new net.minecraft.world.item.Item.Properties()));
        ModItems.ITEMS.register("fuel_tank_small", () -> new com.trd.multiblock.system.FuelTankBlockItem(FUEL_TANK_SMALL.get(), 288000, new net.minecraft.world.item.Item.Properties()));

    }
}
