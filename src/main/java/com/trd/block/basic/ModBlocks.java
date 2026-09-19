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

    public static void register(net.neoforged.bus.api.IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
