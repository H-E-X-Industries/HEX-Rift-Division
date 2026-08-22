package com.trd.multiblock.industrial.vishelashivatel;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Статический реестр рецептов выщелащивателя (по образцу коксовой печи).
 */
public class VishelashivatelRecipes {

    private static final List<VishelashivatelRecipe> RECIPES = new ArrayList<>();

    public static void register(VishelashivatelRecipe recipe) {
        RECIPES.add(recipe);
    }

    public static List<VishelashivatelRecipe> getAllRecipes() {
        return List.copyOf(RECIPES);
    }

    /** Ищет рецепт по входному предмету и типу жидкости в баке. */
    public static VishelashivatelRecipe findMatching(ItemStack input, FluidStack tankFluid) {
        for (VishelashivatelRecipe recipe : RECIPES) {
            if (recipe.matches(input, tankFluid)) return recipe;
        }
        return null;
    }

    /**
     * Разрешён ли тип жидкости к заливке: только те, что используются
     * хотя бы в одном рецепте.
     */
    public static boolean isFluidUsed(Fluid fluid) {
        if (fluid == null) return false;
        for (VishelashivatelRecipe recipe : RECIPES) {
            if (recipe.getRequiredFluid().getFluid() == fluid) return true;
        }
        return false;
    }

    /** Рецепты, в которых используется данный тип жидкости. */
    public static List<VishelashivatelRecipe> getRecipesForFluid(Fluid fluid) {
        List<VishelashivatelRecipe> result = new ArrayList<>();
        for (VishelashivatelRecipe recipe : RECIPES) {
            if (recipe.getRequiredFluid().getFluid() == fluid) result.add(recipe);
        }
        return result;
    }

    public static void init() {
        // === Рецепт-заглушка: кожа из гнилой плоти ===
        // 3 секунды, от 100 об/мин, 250 мБ пероксида водорода за 1 кожу
        register(new VishelashivatelRecipe(
                new ResourceLocation("trd", "leather_from_rotten_flesh"),
                new FluidStack(com.trd.api.fluids.ModFluids.HYDROGEN_PEROXIDE_SOURCE.get(), 250),
                new ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH, 1),
                List.of(new ItemStack(net.minecraft.world.item.Items.LEATHER, 1)),
                60, 100, 20));
    }
}
