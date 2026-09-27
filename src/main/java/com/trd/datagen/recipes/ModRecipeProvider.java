package com.trd.datagen.recipes;

import com.trd.block.basic.ModBlocks;
import com.trd.item.ModItems;
import com.trd.main.MainRegistry;
import com.trd.main.ResourceRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends RecipeProvider {

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
        ResourceRegistry.init();
    }

    @Override
    protected void buildRecipes(RecipeOutput writer) {
        ResourceRecipeHelper.generateRecipes(writer, item -> this.has(item));

        // --- КРАФТЫ СМЕСЕЙ ---
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.FIRE_SMES.get(), 4)
                .requires(Items.CLAY_BALL, 3)
                .requires(ModItems.BAUXITE_POWDER.get())
                .unlockedBy("has_bauxite_powder", has(ModItems.BAUXITE_POWDER.get()))
                .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.DOLOMITE_SMES.get(), 4)
                .requires(ModItems.DOLOMITE_POWDER.get(), 3)
                .requires(Items.CLAY_BALL)
                .unlockedBy("has_dolomite_powder", has(ModItems.DOLOMITE_POWDER.get()))
                .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.LIGNITE_BLOCK.get(), 1)
                .requires(ModItems.LIGNITE.get(), 9)
                .unlockedBy("has_lignite", has(ModItems.LIGNITE.get()))
                .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.LIGNITE.get(), 9)
                .requires(ModBlocks.LIGNITE_BLOCK.get(), 1)
                .unlockedBy("has_lignite_block", has(ModBlocks.LIGNITE_BLOCK.get()))
                .save(writer);

        // --- ПЕРЕПЛАВКА В ПЕЧИ ---
        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(ModItems.FIRE_SMES.get()),
                        RecipeCategory.MISC,
                        ModItems.FIREBRICK.get(),
                        0.3f,
                        200)
                .unlockedBy("has_fire_smes", has(ModItems.FIRE_SMES.get()))
                .save(writer, ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "firebrick_from_smelting"));

        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(ModItems.LIMESTONE_POWDER.get()),
                        RecipeCategory.MISC,
                        ModItems.QUICKLIME.get(),
                        0.3f,
                        200)
                .unlockedBy("has_limestone_powder", has(ModItems.LIMESTONE_POWDER.get()))
                .save(writer, ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "quicklime_from_smelting"));

        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(ModItems.DOLOMITE_SMES.get()),
                        RecipeCategory.MISC,
                        ModItems.REINFORCEDBRICK.get(),
                        0.3f,
                        200)
                .unlockedBy("has_dolomite_smes", has(ModItems.DOLOMITE_SMES.get()))
                .save(writer, ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "dolomite_brick_from_smelting"));

        // --- ОБОРУДОВАНИЕ И МЕХАНИЗМЫ ---
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.DET_MINER.get())
                .pattern("III")
                .pattern("IDI")
                .pattern("III")
                .define('I', Items.IRON_INGOT)
                .define('D', Items.TNT)
                .unlockedBy("has_tnt", has(Items.TNT))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.MILLSTONE.get())
                .pattern("  I")
                .pattern("###")
                .pattern("@@@")
                .define('I', Items.STICK)
                .define('#', Blocks.SMOOTH_STONE_SLAB)
                .define('@', Blocks.SMOOTH_STONE)
                .unlockedBy("has_smooth_stone", has(Blocks.SMOOTH_STONE))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.SMALL_SMELTER.get())
                .pattern("###")
                .pattern("#I#")
                .pattern("@@@")
                .define('I', ModItems.DOLOMITE_SMES.get())
                .define('#', ModItems.FIREBRICK.get())
                .define('@', ModItems.REINFORCEDBRICK.get())
                .unlockedBy("has_reinforcedbrick", has(ModItems.REINFORCEDBRICK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.SMELTER.get())
                .pattern("###")
                .pattern("#I#")
                .pattern("@@@")
                .define('I', ModItems.DOLOMITE_SMES.get())
                .define('#', ModBlocks.FIREBRICK_BLOCK.get())
                .define('@', ModBlocks.REINFORCEDBRICK_BLOCK.get())
                .unlockedBy("has_reinforcedbrick_block", has(ModBlocks.REINFORCEDBRICK_BLOCK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.HEATER.get())
                .pattern("###")
                .pattern("@I@")
                .pattern("@@@")
                .define('I', Blocks.COPPER_BLOCK)
                .define('#', ModItems.FIREBRICK.get())
                .define('@', ModBlocks.REINFORCEDBRICK_BLOCK.get())
                .unlockedBy("has_reinforcedbrick_block", has(ModBlocks.REINFORCEDBRICK_BLOCK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.CASTING_DESCENT.get())
                .pattern("   ")
                .pattern("@ @")
                .pattern(" @ ")
                .define('@', ModItems.REINFORCEDBRICK.get())
                .unlockedBy("has_reinforcedbrick", has(ModItems.REINFORCEDBRICK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.POKER.get())
                .pattern("@@ ")
                .pattern(" @ ")
                .pattern(" @ ")
                .define('@', Items.IRON_INGOT)
                .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ROPE.get())
                .pattern(" @ ")
                .pattern(" @ ")
                .pattern(" @ ")
                .define('@', Items.STRING)
                .unlockedBy("has_string", has(Items.STRING))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.WOODEN_HANDLE.get())
                .pattern("  @")
                .pattern(" @ ")
                .pattern("@  ")
                .define('@', ItemTags.LOGS)
                .unlockedBy("has_logs", has(ItemTags.LOGS))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CAST_PICKAXE_IRON.get())
                .pattern(" @#")
                .pattern(" $@")
                .pattern("$  ")
                .define('@', ModItems.CAST_PICKAXE_IRON_BASE.get())
                .define('$', ModItems.WOODEN_HANDLE.get())
                .define('#', ModItems.ROPE.get())
                .unlockedBy("has_cast_pickaxe_iron_base", has(ModItems.CAST_PICKAXE_IRON_BASE.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CAST_PICKAXE_STEEL.get())
                .pattern(" @#")
                .pattern(" $@")
                .pattern("$  ")
                .define('@', ModItems.CAST_PICKAXE_STEEL_BASE.get())
                .define('$', ModItems.WOODEN_HANDLE.get())
                .define('#', ModItems.ROPE.get())
                .unlockedBy("has_cast_pickaxe_steel_base", has(ModItems.CAST_PICKAXE_STEEL_BASE.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.CASTING_POT.get())
                .pattern("   ")
                .pattern("$#$")
                .pattern("@@@")
                .define('$', ModItems.FIREBRICK.get())
                .define('@', ModItems.REINFORCEDBRICK.get())
                .define('#', ModItems.DOLOMITE_SMES.get())
                .unlockedBy("has_reinforcedbrick", has(ModItems.REINFORCEDBRICK.get()))
                .save(writer);

        // --- МОЛОТ (HAMMER) ДЛЯ КОВКИ ---
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.HAMMER.get())
                .pattern("III")
                .pattern(" S ")
                .pattern(" S ")
                .define('I', Items.IRON_INGOT)
                .define('S', Items.STICK)
                .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                .save(writer);

        // --- ФОРМЫ (MOLDS) ДЛЯ ЛИТЬЯ ---
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MOLD_NUGGET.get())
                .pattern("@@@")
                .pattern("@#@")
                .pattern("@@@")
                .define('@', ModItems.FIREBRICK.get())
                .define('#', ModItems.FIRE_SMES.get())
                .unlockedBy("has_firebrick", has(ModItems.FIREBRICK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MOLD_INGOT.get())
                .pattern("@@@")
                .pattern("###")
                .pattern("@@@")
                .define('@', ModItems.FIREBRICK.get())
                .define('#', ModItems.FIRE_SMES.get())
                .unlockedBy("has_firebrick", has(ModItems.FIREBRICK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MOLD_PICKAXE.get())
                .pattern("##@")
                .pattern("@@#")
                .pattern("@@#")
                .define('@', ModItems.FIREBRICK.get())
                .define('#', ModItems.FIRE_SMES.get())
                .unlockedBy("has_firebrick", has(ModItems.FIREBRICK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MOLD_BLOCK.get())
                .pattern("@#@")
                .pattern("###")
                .pattern("@#@")
                .define('@', ModItems.FIREBRICK.get())
                .define('#', ModItems.FIRE_SMES.get())
                .unlockedBy("has_firebrick", has(ModItems.FIREBRICK.get()))
                .save(writer);

        // === FIREBRICK BLOCKS, SLABS, STAIRS ===
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.FIREBRICK_BLOCK.get(), 1)
                .pattern("II")
                .pattern("II")
                .define('I', ModItems.FIREBRICK.get())
                .unlockedBy("has_firebrick", has(ModItems.FIREBRICK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.FIREBRICK_SLAB.get(), 6)
                .pattern("III")
                .define('I', ModBlocks.FIREBRICK_BLOCK.get())
                .unlockedBy("has_firebrick_block", has(ModBlocks.FIREBRICK_BLOCK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.FIREBRICK_STAIRS.get(), 4)
                .pattern("I  ")
                .pattern("II ")
                .pattern("III")
                .define('I', ModBlocks.FIREBRICK_BLOCK.get())
                .unlockedBy("has_firebrick_block", has(ModBlocks.FIREBRICK_BLOCK.get()))
                .save(writer);

        SingleItemRecipeBuilder.stonecutting(
                        Ingredient.of(ModBlocks.FIREBRICK_BLOCK.get()),
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.FIREBRICK_SLAB.get(), 2)
                .unlockedBy("has_firebrick_block", has(ModBlocks.FIREBRICK_BLOCK.get()))
                .save(writer, ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "firebrick_slab_from_stonecutting"));

        SingleItemRecipeBuilder.stonecutting(
                        Ingredient.of(ModBlocks.FIREBRICK_BLOCK.get()),
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.FIREBRICK_STAIRS.get(), 1)
                .unlockedBy("has_firebrick_block", has(ModBlocks.FIREBRICK_BLOCK.get()))
                .save(writer, ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "firebrick_stairs_from_stonecutting"));

        // === REINFORCED BRICK BLOCKS, SLABS, STAIRS ===
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.REINFORCEDBRICK_BLOCK.get(), 1)
                .pattern("II")
                .pattern("II")
                .define('I', ModItems.REINFORCEDBRICK.get())
                .unlockedBy("has_reinforcedbrick", has(ModItems.REINFORCEDBRICK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.REINFORCEDBRICK_SLAB.get(), 6)
                .pattern("III")
                .define('I', ModBlocks.REINFORCEDBRICK_BLOCK.get())
                .unlockedBy("has_reinforcedbrick_block", has(ModBlocks.REINFORCEDBRICK_BLOCK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.REINFORCEDBRICK_STAIRS.get(), 4)
                .pattern("I  ")
                .pattern("II ")
                .pattern("III")
                .define('I', ModBlocks.REINFORCEDBRICK_BLOCK.get())
                .unlockedBy("has_reinforcedbrick_block", has(ModBlocks.REINFORCEDBRICK_BLOCK.get()))
                .save(writer);

        SingleItemRecipeBuilder.stonecutting(
                        Ingredient.of(ModBlocks.REINFORCEDBRICK_BLOCK.get()),
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.REINFORCEDBRICK_SLAB.get(), 2)
                .unlockedBy("has_reinforcedbrick_block", has(ModBlocks.REINFORCEDBRICK_BLOCK.get()))
                .save(writer, ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "reinforcedbrick_slab_from_stonecutting"));

        SingleItemRecipeBuilder.stonecutting(
                        Ingredient.of(ModBlocks.REINFORCEDBRICK_BLOCK.get()),
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.REINFORCEDBRICK_STAIRS.get(), 1)
                .unlockedBy("has_reinforcedbrick_block", has(ModBlocks.REINFORCEDBRICK_BLOCK.get()))
                .save(writer, ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "reinforcedbrick_stairs_from_stonecutting"));
    }
}
