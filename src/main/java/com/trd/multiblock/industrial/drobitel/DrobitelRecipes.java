package com.trd.multiblock.industrial.drobitel;

import com.trd.block.basic.ModBlocks;
import com.trd.item.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class DrobitelRecipes {

    public static void register() {
        // --- Базовые цепочки ---
        DrobitelBlockEntity.addRecipe(Items.PORKCHOP, ModBlocks.ANTON_CHIGUR.get().asItem(), 1);
        DrobitelBlockEntity.addRecipe(Items.COOKED_PORKCHOP, ModBlocks.MORY_BLOCK.get().asItem(), 1);
        DrobitelBlockEntity.addRecipe(Items.DEEPSLATE, Items.COBBLED_DEEPSLATE, 1);
        DrobitelBlockEntity.addRecipe(Items.COBBLED_DEEPSLATE, Items.COBBLESTONE, 1);
        DrobitelBlockEntity.addRecipe(Items.STONE, Items.COBBLESTONE, 1);
        DrobitelBlockEntity.addRecipe(Items.COBBLESTONE, Items.GRAVEL, 1);
        DrobitelBlockEntity.addRecipe(Items.GRAVEL, Items.SAND, 1);

        // --- Модовые блоки (известняк, боксит и т.д.) ---
        DrobitelBlockEntity.addRecipe(ModBlocks.LIMESTONE.get().asItem(),
                new ItemStack(ModItems.LIMESTONE_CHUNK.get(), 3),
                new ItemStack(Items.GRAVEL, 1));

        DrobitelBlockEntity.addRecipe(ModBlocks.BAUXITE.get().asItem(),
                new ItemStack(ModItems.BAUXITE_CHUNK.get(), 3),
                new ItemStack(Items.GRAVEL, 1));

        DrobitelBlockEntity.addRecipe(ModBlocks.DOLOMITE.get().asItem(),
                new ItemStack(ModItems.DOLOMITE_CHUNK.get(), 3),
                new ItemStack(Items.GRAVEL, 1));

        DrobitelBlockEntity.addRecipe(ModBlocks.SULFUR_CLUSTER.get().asItem(),
                new ItemStack(ModItems.SULFUR.get(), 3),
                new ItemStack(Items.GRAVEL, 1));

        // --- Модовые руды ---
        DrobitelBlockEntity.addRecipe(ModBlocks.LIGNITE_ORE.get().asItem(),
                new ItemStack(ModItems.LIGNITE.get(), 3),
                new ItemStack(Blocks.COBBLESTONE, 1));

        DrobitelBlockEntity.addRecipe(ModBlocks.SALT_ORE.get().asItem(),
                new ItemStack(ModItems.SALT.get(), 3),
                new ItemStack(Blocks.COBBLESTONE, 1));

        DrobitelBlockEntity.addRecipe(ModBlocks.ASBESOTS_ORE.get().asItem(),
                new ItemStack(ModItems.ASBESTOS.get(), 3),
                new ItemStack(Blocks.COBBLESTONE, 1));

        DrobitelBlockEntity.addRecipe(ModBlocks.SULFUR_ORE.get().asItem(),
                new ItemStack(ModItems.SULFUR.get(), 3),
                new ItemStack(Blocks.COBBLESTONE, 1));

        DrobitelBlockEntity.addRecipe(ModBlocks.SULFUR_ORE_DEEPSLATE.get().asItem(),
                new ItemStack(ModItems.SULFUR.get(), 3),
                new ItemStack(Blocks.COBBLED_DEEPSLATE, 1));

        DrobitelBlockEntity.addRecipe(ModBlocks.CINNABAR_ORE.get().asItem(),
                new ItemStack(ModItems.CINNABAR.get(), 3),
                new ItemStack(Blocks.COBBLESTONE, 1));

        DrobitelBlockEntity.addRecipe(ModBlocks.CINNABAR_ORE_DEEPSLATE.get().asItem(),
                new ItemStack(ModItems.CINNABAR.get(), 3),
                new ItemStack(Blocks.COBBLED_DEEPSLATE, 1));

        DrobitelBlockEntity.addRecipe(ModBlocks.SEQUESTRUM_ORE.get().asItem(),
                new ItemStack(ModItems.SEQUESTRUM.get(), 3),
                new ItemStack(Blocks.COBBLESTONE, 1));

        DrobitelBlockEntity.addRecipe(ModBlocks.SEQUESTRUM_ORE_DEEPSLATE.get().asItem(),
                new ItemStack(ModItems.SEQUESTRUM.get(), 3),
                new ItemStack(Blocks.COBBLED_DEEPSLATE, 1));

        DrobitelBlockEntity.addRecipe(ModBlocks.FLUORITE_ORE.get().asItem(),
                new ItemStack(ModItems.FLUORITE.get(), 3),
                new ItemStack(Blocks.COBBLESTONE, 1));

        DrobitelBlockEntity.addRecipe(ModBlocks.FLUORITE_ORE_DEEPSLATE.get().asItem(),
                new ItemStack(ModItems.FLUORITE.get(), 3),
                new ItemStack(Blocks.COBBLED_DEEPSLATE, 1));

        // --- Чанки → пыль ---
        DrobitelBlockEntity.addRecipe(ModItems.LIMESTONE_CHUNK.get(), ModItems.LIMESTONE_POWDER.get(), 1);
        DrobitelBlockEntity.addRecipe(ModItems.BAUXITE_CHUNK.get(), ModItems.BAUXITE_POWDER.get(), 1);
        DrobitelBlockEntity.addRecipe(ModItems.DOLOMITE_CHUNK.get(), ModItems.DOLOMITE_POWDER.get(), 1);

        // === ВАНИЛЬНЫЕ РУДЫ ===

        // Дешёвые / распространённые (4–5 единиц)
        DrobitelBlockEntity.addRecipe(Items.COAL_ORE,
                new ItemStack(Items.COAL, 4),
                new ItemStack(Items.COBBLESTONE, 1));

        DrobitelBlockEntity.addRecipe(Items.DEEPSLATE_COAL_ORE,
                new ItemStack(Items.COAL, 4),
                new ItemStack(Items.COBBLED_DEEPSLATE, 1));

        DrobitelBlockEntity.addRecipe(Items.COPPER_ORE,
                new ItemStack(Items.RAW_COPPER, 5),
                new ItemStack(Items.COBBLESTONE, 1));

        DrobitelBlockEntity.addRecipe(Items.DEEPSLATE_COPPER_ORE,
                new ItemStack(Items.RAW_COPPER, 5),
                new ItemStack(Items.COBBLED_DEEPSLATE, 1));

        DrobitelBlockEntity.addRecipe(Items.REDSTONE_ORE,
                new ItemStack(Items.REDSTONE, 4),
                new ItemStack(Items.COBBLESTONE, 1));

        DrobitelBlockEntity.addRecipe(Items.DEEPSLATE_REDSTONE_ORE,
                new ItemStack(Items.REDSTONE, 4),
                new ItemStack(Items.COBBLED_DEEPSLATE, 1));

        DrobitelBlockEntity.addRecipe(Items.NETHER_QUARTZ_ORE,
                new ItemStack(Items.QUARTZ, 4),
                new ItemStack(Items.NETHERRACK, 1));

        // Средние по ценности (2–3 единицы)
        DrobitelBlockEntity.addRecipe(Items.IRON_ORE,
                new ItemStack(Items.RAW_IRON, 3),
                new ItemStack(Items.COBBLESTONE, 1));

        DrobitelBlockEntity.addRecipe(Items.DEEPSLATE_IRON_ORE,
                new ItemStack(Items.RAW_IRON, 3),
                new ItemStack(Items.COBBLED_DEEPSLATE, 1));

        DrobitelBlockEntity.addRecipe(Items.GOLD_ORE,
                new ItemStack(Items.RAW_GOLD, 2),
                new ItemStack(Items.COBBLESTONE, 1));

        DrobitelBlockEntity.addRecipe(Items.DEEPSLATE_GOLD_ORE,
                new ItemStack(Items.RAW_GOLD, 2),
                new ItemStack(Items.COBBLED_DEEPSLATE, 1));

        DrobitelBlockEntity.addRecipe(Items.NETHER_GOLD_ORE,
                new ItemStack(Items.GOLD_NUGGET, 5),
                new ItemStack(Items.NETHERRACK, 1));

        DrobitelBlockEntity.addRecipe(Items.LAPIS_ORE,
                new ItemStack(Items.LAPIS_LAZULI, 3),
                new ItemStack(Items.COBBLESTONE, 1));

        DrobitelBlockEntity.addRecipe(Items.DEEPSLATE_LAPIS_ORE,
                new ItemStack(Items.LAPIS_LAZULI, 3),
                new ItemStack(Items.COBBLED_DEEPSLATE, 1));

        // Редкие / ценные (1–2 единицы)
        DrobitelBlockEntity.addRecipe(Items.DIAMOND_ORE,
                new ItemStack(Items.DIAMOND, 2),
                new ItemStack(Items.COBBLESTONE, 1));

        DrobitelBlockEntity.addRecipe(Items.DEEPSLATE_DIAMOND_ORE,
                new ItemStack(Items.DIAMOND, 2),
                new ItemStack(Items.COBBLED_DEEPSLATE, 1));

        DrobitelBlockEntity.addRecipe(Items.EMERALD_ORE,
                new ItemStack(Items.EMERALD, 2),
                new ItemStack(Items.COBBLESTONE, 1));

        DrobitelBlockEntity.addRecipe(Items.DEEPSLATE_EMERALD_ORE,
                new ItemStack(Items.EMERALD, 2),
                new ItemStack(Items.COBBLED_DEEPSLATE, 1));

        DrobitelBlockEntity.addRecipe(Items.ANCIENT_DEBRIS,
                new ItemStack(Items.NETHERITE_SCRAP, 2),
                new ItemStack(Items.GRAVEL, 1));
    }
}