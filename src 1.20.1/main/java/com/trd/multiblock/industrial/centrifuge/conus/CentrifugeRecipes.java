package com.trd.multiblock.industrial.centrifuge.conus;

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


        register(new CentrifugeRecipe(
                new ResourceLocation("trd", "bone_block_centrifuging"),
                new ItemStack(Items.BONE_BLOCK, 1),
                List.of(
                        new ItemStack(Items.BONE_MEAL, 9)
                ),
                160));
    }
}
