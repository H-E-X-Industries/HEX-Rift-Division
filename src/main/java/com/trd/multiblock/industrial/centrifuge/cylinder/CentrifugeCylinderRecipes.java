package com.trd.multiblock.industrial.centrifuge.cylinder;

import com.trd.api.fluids.ModFluids;
import com.trd.item.ModItems;
import com.trd.main.ResourceRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Статический реестр рецептов жидкостной насадки центрифуги
 * (по образцу реестра насадки-конуса).
 */
public class CentrifugeCylinderRecipes {

    private static final List<CentrifugeCylinderRecipe> RECIPES = new ArrayList<>();

    public static void register(CentrifugeCylinderRecipe recipe) {
        RECIPES.add(recipe);
    }

    public static List<CentrifugeCylinderRecipe> getAllRecipes() {
        return List.copyOf(RECIPES);
    }

    /** Ищет рецепт по типу и количеству жидкости во входном буфере. */
    public static CentrifugeCylinderRecipe findMatching(FluidStack tankFluid) {
        if (tankFluid.isEmpty()) return null;
        for (CentrifugeCylinderRecipe recipe : RECIPES) {
            if (recipe.matches(tankFluid)) return recipe;
        }
        return null;
    }

    /**
     * Разрешён ли тип жидкости к заливке: только те, что используются
     * хотя бы в одном рецепте.
     */
    public static boolean isFluidUsed(Fluid fluid) {
        if (fluid == null) return false;
        for (CentrifugeCylinderRecipe recipe : RECIPES) {
            if (recipe.getInputFluid().getFluid() == fluid) return true;
        }
        return false;
    }

    public static void init() {

        // Красный шлам: сепарация на воду, оксидное железо, песок и титансодержащую фракцию
        register(new CentrifugeCylinderRecipe(
                new ResourceLocation("trd", "red_sludge_centrifuging"),
                new FluidStack(ModFluids.RED_SLUDGE_SOURCE.get(), 100),
                List.of(new FluidStack(Fluids.WATER, 50)),
                List.of(
                        new ItemStack(Items.IRON_NUGGET, 4),
                        new ItemStack(ModItems.SULFUR.get(), 1),
                        new ItemStack(ResourceRegistry.getSmallUnit("titanium"), 1)
                ),
                140));

        register(new CentrifugeCylinderRecipe(
                new ResourceLocation("trd", "sulfuric_acid_centrifuging"),
                new FluidStack(ModFluids.SULFURIC_ACID_SOURCE.get(), 1000),
                List.of(new FluidStack(Fluids.WATER, 900)),
                List.of(
                        new ItemStack(ModItems.SULFUR.get(), 2)
                ),
                60));
    }
}
