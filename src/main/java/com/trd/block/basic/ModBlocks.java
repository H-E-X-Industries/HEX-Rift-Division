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

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
