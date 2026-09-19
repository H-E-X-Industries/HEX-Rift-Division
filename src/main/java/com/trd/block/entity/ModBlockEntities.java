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

    public static final java.util.function.Supplier<BlockEntityType<com.trd.block.entity.industrial.energy.ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE_BE = BLOCK_ENTITIES.register("electric_furnace", () -> BlockEntityType.Builder.of(com.trd.block.entity.industrial.energy.ElectricFurnaceBlockEntity::new, ModBlocks.ELECTRO_FURNACE.get()).build(null));
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
