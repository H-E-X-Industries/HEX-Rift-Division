package com.trd.multiblock.industrial.centrifuge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class CentrifugeRecipes {

    private static final List<CentrifugeRecipe> RECIPES = new ArrayList<>();

    public static void register(CentrifugeRecipe recipe) {
        RECIPES.add(recipe);
    }

    public static List<CentrifugeRecipe> getAllRecipes() {
        return List.copyOf(RECIPES);
    }

    public static CentrifugeRecipe findMatching(ItemStack input) {
        if (input.isEmpty()) return null;
        for (CentrifugeRecipe recipe : RECIPES) {
            if (recipe.matches(input)) return recipe;
        }
        return null;
    }

    public static boolean hasRecipe(ItemStack input) {
        return findMatching(input) != null;
    }

    public static void init() {
        // Три уникальных выхода — проверка распределения по разным слотам
        register(new CentrifugeRecipe(
                new ResourceLocation("trd", "dirt_centrifuging"),
                new ItemStack(Items.DIRT, 1),
                List.of(
                        new ItemStack(Items.CLAY_BALL, 1),
                        new ItemStack(Items.SAND, 2),
                        new ItemStack(Items.FLINT, 1)
                ),
                100));

        // Несколько одинаковых предметов в выходе — уходят в один слот, пока не забьётся
        register(new CentrifugeRecipe(
                new ResourceLocation("trd", "gravel_centrifuging"),
                new ItemStack(Items.GRAVEL, 1),
                List.of(
                        new ItemStack(Items.IRON_NUGGET, 2),
                        new ItemStack(Items.FLINT, 4)
                ),
                80));

        register(new CentrifugeRecipe(
                new ResourceLocation("trd", "bone_block_centrifuging"),
                new ItemStack(Items.BONE_BLOCK, 1),
                List.of(
                        new ItemStack(Items.BONE_MEAL, 9)
                ),
                160));
    }
}
