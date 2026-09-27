package com.trd.datagen.recipes;

import com.trd.main.ResourceRegistry;
import com.trd.main.ResourceRegistry.ResourceEntry;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

import java.util.function.Function;

/**
 * Автоматическая генерация рецептов для ресурсов из ResourceRegistry (1.21.1 NeoForge).
 * Создает рецепты:
 * - 9 мелких единиц -> 1 основная
 * - 1 основная -> 9 мелких
 * - 9 основных -> 1 блок
 * - 1 блок -> 9 основных
 */
public class ResourceRecipeHelper {

    /**
     * Генерирует все рецепты для всех зарегистрированных ресурсов
     */
    public static void generateRecipes(RecipeOutput writer, Function<ItemLike, Criterion<InventoryChangeTrigger.TriggerInstance>> hasFunc) {
        for (ResourceEntry resource : ResourceRegistry.getResources()) {
            generateRecipesForResource(writer, resource, hasFunc);
        }
    }

    /**
     * Генерирует рецепты для конкретного ресурса
     */
    private static void generateRecipesForResource(RecipeOutput writer, ResourceEntry resource, Function<ItemLike, Criterion<InventoryChangeTrigger.TriggerInstance>> hasFunc) {
        String name = resource.name;

        // 1. Мелкая единица -> Основная (9:1) - если есть мелкая единица
        if (resource.hasSmallUnit()) {
            // 9 самородков/кусочков/осколков = 1 слиток/гранула/кристалл
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, resource.mainUnit.get())
                    .requires(resource.smallUnit.get(), 9)
                    .unlockedBy("has_" + resource.getSmallUnitId(), hasFunc.apply(resource.smallUnit.get()))
                    .save(writer, ResourceLocation.parse(getRecipeId(name, "small_to_main")));

            // Обратно: 1 слиток = 9 самородков
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, resource.smallUnit.get(), 9)
                    .requires(resource.mainUnit.get())
                    .unlockedBy("has_" + resource.getMainUnitId(), hasFunc.apply(resource.mainUnit.get()))
                    .save(writer, ResourceLocation.parse(getRecipeId(name, "main_to_small")));
        }

        // 2. Основная единица -> Блок (9:1) - если есть блок
        if (resource.hasBlock()) {
            // 9 слитков/гранул = 1 блок
            ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, resource.block.get())
                    .pattern("###")
                    .pattern("###")
                    .pattern("###")
                    .define('#', resource.mainUnit.get())
                    .unlockedBy("has_" + resource.getMainUnitId(), hasFunc.apply(resource.mainUnit.get()))
                    .save(writer, ResourceLocation.parse(getRecipeId(name, "main_to_block")));

            // Обратно: блок -> 9 слитков
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, resource.mainUnit.get(), 9)
                    .requires(resource.block.get())
                    .unlockedBy("has_" + resource.getBlockId(), hasFunc.apply(resource.block.get()))
                    .save(writer, ResourceLocation.parse(getRecipeId(name, "block_to_main")));
        }
    }

    private static String getRecipeId(String resourceName, String recipeType) {
        return "trd:" + resourceName + "_" + recipeType;
    }
}
