package com.trd.api.chemistry;

import com.trd.api.fluids.ModFluids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class ChemicalPlantRecipeRegistry {
    private static final List<ChemicalPlantRecipe> RECIPES = new ArrayList<>();

    public static void init() {
        register(new ChemicalPlantRecipe(
                new ResourceLocation("trd", "hydrogen_peroxide"),
                List.of(new FluidStack(Fluids.WATER, 300)),
                List.of(new FluidStack(ModFluids.HYDROGEN_PEROXIDE_SOURCE.get(), 150)),
                List.of(),
                List.of(),
                20 // 1 second
        ));
        // TODO: add more recipes here
    }

    public static void register(ChemicalPlantRecipe recipe) {
        RECIPES.add(recipe);
    }

    public static List<ChemicalPlantRecipe> getAllRecipes() {
        return new ArrayList<>(RECIPES);
    }

    public static ChemicalPlantRecipe findMatching(List<FluidStack> fluids, List<ItemStack> items) {
        for (ChemicalPlantRecipe recipe : RECIPES) {
            if (recipe.matches(fluids, items)) return recipe;
        }
        return null;
    }

    public static ChemicalPlantRecipe getById(ResourceLocation id) {
        for (ChemicalPlantRecipe recipe : RECIPES) {
            if (recipe.getId().equals(id)) return recipe;
        }
        return null;
    }
}