package com.trd.multiblock.industrial.vishelashivatel;

import com.trd.api.fluids.ModFluids;
import com.trd.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
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

    /** Первый рецепт для данного входного предмета (без учёта жидкости). */
    public static VishelashivatelRecipe findForInput(ItemStack input) {
        if (input.isEmpty()) return null;
        for (VishelashivatelRecipe recipe : RECIPES) {
            if (ItemStack.isSameItemSameTags(input, recipe.getItemInput())) return recipe;
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
                new FluidStack(ModFluids.HYDROGEN_PEROXIDE_SOURCE.get(), 250),
                new ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH, 1),
                List.of(new ItemStack(net.minecraft.world.item.Items.LEATHER, 1)),
                60, 100, 20));

        register(new VishelashivatelRecipe(
                new ResourceLocation("trd", "soda_crystal"),
                new FluidStack(Fluids.WATER, 250),
                new ItemStack(ModItems.BLACK_ASH.get(), 1),
                List.of(new ItemStack(ModItems.SODA_CRYSTAL.get(), 1),new ItemStack(ModItems.SALT.get(), 1)),
                60, 100, 20));
    }
}
