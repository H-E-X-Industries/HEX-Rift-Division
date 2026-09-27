package com.trd.api.chemistry;

import com.trd.api.fluids.ModFluids;
import com.trd.block.basic.ModBlocks;
import com.trd.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class ChemicalPlantRecipeRegistry {
    private static final List<ChemicalPlantRecipe> RECIPES = new ArrayList<>();

    public static void init() {
        RECIPES.clear();

        register(new ChemicalPlantRecipe(
                ResourceLocation.fromNamespaceAndPath("trd", "hydrogen_peroxide"),
                List.of(new FluidStack(Fluids.WATER, 300)),
                List.of(new FluidStack(ModFluids.HYDROGEN_PEROXIDE_SOURCE.get(), 150)),
                List.of(),
                List.of(),
                20, // 1 second
                30  // 30 degrees min temperature
        ));

        // Sulfuric Acid: 3 Sulfur + 1000mB Water = 1000mB Sulfuric Acid. 3s (60 ticks), 125°C
        register(new ChemicalPlantRecipe(
                ResourceLocation.fromNamespaceAndPath("trd", "sulfuric_acid"),
                List.of(new FluidStack(ModFluids.HYDROGEN_PEROXIDE_SOURCE.get(), 1000)),
                List.of(new FluidStack(ModFluids.SULFURIC_ACID_SOURCE.get(), 1000)),
                List.of(new ItemStack(ModItems.SULFUR.get(), 3)),
                List.of(),
                60,
                125
        ));

        // Obsidian: 1000mB Water + 1000mB Lava = 1 Obsidian. 5s (100 ticks), 200°C
        register(new ChemicalPlantRecipe(
                ResourceLocation.fromNamespaceAndPath("trd", "obsidian"),
                List.of(new FluidStack(Fluids.WATER, 1000),
                        new FluidStack(Fluids.LAVA, 1000)),
                List.of(),
                List.of(),
                List.of(new ItemStack(Items.OBSIDIAN, 1)),
                100,
                200
        ));

        // Hydrogen Chlorine: 1 Salt + 1000mB Sulfuric Acid = 700mB Sodium Sulfate + 300mB Hydrogen Chloride
        register(new ChemicalPlantRecipe(
                ResourceLocation.fromNamespaceAndPath("trd", "hydrogen_chlorine"),
                List.of(new FluidStack(ModFluids.SULFURIC_ACID_SOURCE.get(), 1000)),
                List.of(new FluidStack(ModFluids.SODIUM_SULFATE_SOURCE.get(), 700),
                        new FluidStack(ModFluids.HYDROGEN_CHLORINE_SOURCE.get(), 300)),
                List.of(new ItemStack(ModItems.SALT.get(), 1)),
                List.of(),
                90,
                100
        ));

        // Black Ash
        register(new ChemicalPlantRecipe(
                ResourceLocation.fromNamespaceAndPath("trd", "black_ash"),
                List.of(new FluidStack(ModFluids.SODIUM_SULFATE_SOURCE.get(), 1000)),
                List.of(new FluidStack(ModFluids.CARBON_DIOXIDE_SOURCE.get(), 100)),
                List.of(new ItemStack(ModItems.LIMESTONE_POWDER.get(), 1), new ItemStack(Items.COAL, 1)),
                List.of(new ItemStack(ModItems.BLACK_ASH.get(), 1)),
                90,
                200
        ));

        // Sodium Hydroxide
        register(new ChemicalPlantRecipe(
                ResourceLocation.fromNamespaceAndPath("trd", "sodium_hydroxide"),
                List.of(new FluidStack(Fluids.WATER, 1000)),
                List.of(new FluidStack(ModFluids.SODIUM_HYDROXIDE_SOURCE.get(), 250)),
                List.of(new ItemStack(ModItems.SODA.get(), 1), new ItemStack(ModItems.QUICKLIME.get(), 1)),
                List.of(),
                90,
                50
        ));

        // Concrete
        register(new ChemicalPlantRecipe(
                ResourceLocation.fromNamespaceAndPath("trd", "concrete"),
                List.of(new FluidStack(Fluids.WATER, 1000)),
                List.of(),
                List.of(new ItemStack(Items.GRAVEL, 1), new ItemStack(Items.SAND, 1), new ItemStack(ModItems.QUICKLIME.get(), 1)),
                List.of(new ItemStack(ModBlocks.CONCRETE.get(), 1)),
                90,
                30
        ));

        // Aluminate Solution
        register(new ChemicalPlantRecipe(
                ResourceLocation.fromNamespaceAndPath("trd", "aluminate_solution"),
                List.of(new FluidStack(ModFluids.SODIUM_HYDROXIDE_SOURCE.get(), 500)),
                List.of(new FluidStack(ModFluids.ALUMINATE_SOLUTION_SOURCE.get(), 250),
                        new FluidStack(ModFluids.RED_SLUDGE_SOURCE.get(), 50)),
                List.of(new ItemStack(ModItems.BAUXITE_POWDER.get(), 4)),
                List.of(),
                90,
                150
        ));

        // Aluminate Decomposition
        register(new ChemicalPlantRecipe(
                ResourceLocation.fromNamespaceAndPath("trd", "aluminate_decomposition"),
                List.of(new FluidStack(ModFluids.ALUMINATE_SOLUTION_SOURCE.get(), 125),
                        new FluidStack(Fluids.WATER, 375)),
                List.of(new FluidStack(ModFluids.SODIUM_HYDROXIDE_SOURCE.get(), 450)),
                List.of(),
                List.of(new ItemStack(ModItems.ALUMINUM_HYDROXIDE.get(), 2)),
                90,
                60
        ));
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
        if (id == null) return null;
        for (ChemicalPlantRecipe recipe : RECIPES) {
            if (recipe.getId().equals(id)) return recipe;
        }
        return null;
    }
}
