package com.trd.block.entity;

import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.industrial.rotation.MillstoneBlockEntity;
import com.trd.main.MainRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MainRegistry.MOD_ID);

    public static final java.util.function.Supplier<net.minecraft.world.level.block.entity.BlockEntityType<com.trd.multiblock.system.MultiblockPartEntity>> MULTIBLOCK_PART = BLOCK_ENTITIES.register("multiblock_part", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(com.trd.multiblock.system.MultiblockPartEntity::new, com.trd.block.basic.ModBlocks.MULTIBLOCK_PART.get()).build(null));


    public static final java.util.function.Supplier<BlockEntityType<com.trd.block.entity.industrial.energy.ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE_BE = BLOCK_ENTITIES.register("electric_furnace", () -> BlockEntityType.Builder.of(com.trd.block.entity.industrial.energy.ElectricFurnaceBlockEntity::new, ModBlocks.ELECTRO_FURNACE.get()).build(null));

    public static final java.util.function.Supplier<net.minecraft.world.level.block.entity.BlockEntityType<com.trd.block.entity.industrial.fluids.FluidBarrelBlockEntity>> FLUID_BARREL_BE =
            BLOCK_ENTITIES.register("fluid_barrel_be", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                    com.trd.block.entity.industrial.fluids.FluidBarrelBlockEntity::new,
                    com.trd.block.basic.ModBlocks.CORRUPTED_BARREL.get(),
                    com.trd.block.basic.ModBlocks.LEAKING_BARREL.get(),
                    com.trd.block.basic.ModBlocks.IRON_BARREL.get(),
                    com.trd.block.basic.ModBlocks.STEEL_BARREL.get(),
                    com.trd.block.basic.ModBlocks.LEAD_BARREL.get(),
                    com.trd.block.basic.ModBlocks.DECO_BARREL.get()
            ).build(null));

    public static final java.util.function.Supplier<net.minecraft.world.level.block.entity.BlockEntityType<com.trd.block.entity.industrial.fluids.ValveBlockEntity>> VALVE_BE =
            BLOCK_ENTITIES.register("valve_be", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(com.trd.block.entity.industrial.fluids.ValveBlockEntity::new, com.trd.block.basic.ModBlocks.VALVE.get()).build(null));
    public static final java.util.function.Supplier<net.minecraft.world.level.block.entity.BlockEntityType<com.trd.block.entity.industrial.fluids.LowPressureSteamCondenserBlockEntity>> LOW_PRESSURE_STEAM_CONDENSER_BE = BLOCK_ENTITIES.register("low_pressure_steam_condenser_be", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(com.trd.block.entity.industrial.fluids.LowPressureSteamCondenserBlockEntity::new, com.trd.block.basic.ModBlocks.LOW_PRESSURE_STEAM_CONDENSER.get()).build(null));
    public static final java.util.function.Supplier<net.minecraft.world.level.block.entity.BlockEntityType<com.trd.block.entity.industrial.fluids.WaterPumpBlockEntity>> WATER_PUMP_BE = BLOCK_ENTITIES.register("water_pump_be", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(com.trd.block.entity.industrial.fluids.WaterPumpBlockEntity::new, com.trd.block.basic.ModBlocks.WATER_PUMP.get()).build(null));
    public static final java.util.function.Supplier<net.minecraft.world.level.block.entity.BlockEntityType<com.trd.block.entity.industrial.fluids.PaintablePipeBlockEntity>> PAINTABLE_PIPE_BE = BLOCK_ENTITIES.register("paintable_pipe_be", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(com.trd.block.entity.industrial.fluids.PaintablePipeBlockEntity::new, com.trd.block.basic.ModBlocks.PAINTABLE_PIPE.get()).build(null));
    public static final java.util.function.Supplier<net.minecraft.world.level.block.entity.BlockEntityType<com.trd.block.entity.industrial.fluids.FluidPipeBlockEntity>> FLUID_PIPE_BE = BLOCK_ENTITIES.register("fluid_pipe_be", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                    com.trd.block.entity.industrial.fluids.FluidPipeBlockEntity::new,
                    com.trd.block.basic.ModBlocks.BRONZE_FLUID_PIPE.get(),
                    com.trd.block.basic.ModBlocks.STEEL_FLUID_PIPE.get(),
                    com.trd.block.basic.ModBlocks.LEAD_FLUID_PIPE.get(),
                    com.trd.block.basic.ModBlocks.TUNGSTEN_FLUID_PIPE.get(),
                    com.trd.block.basic.ModBlocks.PAINTABLE_PIPE.get()
            ).build(null));

    public static final java.util.function.Supplier<net.minecraft.world.level.block.entity.BlockEntityType<com.trd.multiblock.industrial.fueltanks.FuelTankBlockEntity>> FUEL_TANK_BE =
            BLOCK_ENTITIES.register("fuel_tank_be", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                    com.trd.multiblock.industrial.fueltanks.FuelTankBlockEntity::new,
                    com.trd.block.basic.ModBlocks.FUEL_TANK_BIG.get()
            ).build(null));

    public static final java.util.function.Supplier<net.minecraft.world.level.block.entity.BlockEntityType<com.trd.multiblock.industrial.fueltanks.small.FuelTankSmallBlockEntity>> FUEL_TANK_SMALL_BE =
            BLOCK_ENTITIES.register("fuel_tank_small_be", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
                    com.trd.multiblock.industrial.fueltanks.small.FuelTankSmallBlockEntity::new,
                    com.trd.block.basic.ModBlocks.FUEL_TANK_SMALL.get()
            ).build(null));

    public static final java.util.function.Supplier<BlockEntityType<com.trd.block.entity.industrial.energy.MachineBatteryBlockEntity>> MACHINE_BATTERY_BE = BLOCK_ENTITIES.register("machine_battery_be", () -> { net.minecraft.world.level.block.Block[] validBlocks = ModBlocks.BATTERY_BLOCKS.stream().map(java.util.function.Supplier::get).toArray(net.minecraft.world.level.block.Block[]::new); return BlockEntityType.Builder.of(com.trd.block.entity.industrial.energy.MachineBatteryBlockEntity::new, validBlocks).build(null); });
    public static final java.util.function.Supplier<BlockEntityType<com.trd.block.entity.industrial.energy.WireBlockEntity>> WIRE_BE = BLOCK_ENTITIES.register("wire_be", () -> BlockEntityType.Builder.of(com.trd.block.entity.industrial.energy.WireBlockEntity::new, ModBlocks.WIRE_COATED.get()).build(null));
    public static final java.util.function.Supplier<BlockEntityType<com.trd.block.entity.industrial.energy.ConverterBlockEntity>> CONVERTER_BE = BLOCK_ENTITIES.register("converter_be", () -> BlockEntityType.Builder.of(com.trd.block.entity.industrial.energy.ConverterBlockEntity::new, ModBlocks.CONVERTER_BLOCK.get()).build(null));
    public static final java.util.function.Supplier<BlockEntityType<com.trd.block.entity.industrial.energy.SwitchBlockEntity>> SWITCH_BE = BLOCK_ENTITIES.register("switch_be", () -> BlockEntityType.Builder.of(com.trd.block.entity.industrial.energy.SwitchBlockEntity::new, ModBlocks.SWITCH.get()).build(null));
    public static final java.util.function.Supplier<BlockEntityType<com.trd.block.entity.industrial.energy.ConnectorBlockEntity>> CONNECTOR_BE = BLOCK_ENTITIES.register("connector", () -> BlockEntityType.Builder.of(com.trd.block.entity.industrial.energy.ConnectorBlockEntity::new, ModBlocks.CONNECTOR.get(), ModBlocks.MEDIUM_CONNECTOR.get(), ModBlocks.LARGE_CONNECTOR.get()).build(null));
    public static final java.util.function.Supplier<BlockEntityType<com.trd.block.entity.industrial.energy.PaintableWireBlockEntity>> PAINTABLE_WIRE_BE = BLOCK_ENTITIES.register("paintable_wire_be", () -> BlockEntityType.Builder.of(com.trd.block.entity.industrial.energy.PaintableWireBlockEntity::new, ModBlocks.PAINTABLE_WIRE.get()).build(null));

    public static final java.util.function.Supplier<BlockEntityType<MillstoneBlockEntity>> MILLSTONE = BLOCK_ENTITIES.register("millstone",
            () -> BlockEntityType.Builder.of(MillstoneBlockEntity::new, ModBlocks.MILLSTONE.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}

