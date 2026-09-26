package com.trd.multiblock.industrial.centrifuge.cylinder;

import com.trd.api.fluids.ModFluids;
import com.trd.item.ModItems;
import com.trd.main.ResourceRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class CentrifugeCylinderRecipes {

    private static final List<CentrifugeCylinderRecipe> RECIPES = new ArrayList<>();

    public static void register(CentrifugeCylinderRecipe recipe) {
        RECIPES.add(recipe);
    }

    public static List<CentrifugeCylinderRecipe> getAllRecipes() {
        return List.copyOf(RECIPES);
    }

    public static CentrifugeCylinderRecipe findMatching(FluidStack tankFluid) {
        if (tankFluid.isEmpty()) return null;
        for (CentrifugeCylinderRecipe recipe : RECIPES) {
            if (recipe.matches(tankFluid)) return recipe;
        }
        return null;
    }

    public static boolean isFluidUsed(Fluid fluid) {
        if (fluid == null) return false;
        for (CentrifugeCylinderRecipe recipe : RECIPES) {
            if (recipe.getInputFluid().getFluid() == fluid) return true;
        }
        return false;
    }

    public static void init() {
        // Красный шлам: сепарация на воду, железо, серу и титан
        register(new CentrifugeCylinderRecipe(
                ResourceLocation.fromNamespaceAndPath("trd", "red_sludge_centrifuging"),
                new FluidStack(ModFluids.RED_SLUDGE_SOURCE.get(), 100),
                List.of(new FluidStack(Fluids.WATER, 50)),
                List.of(
                        new ItemStack(Items.IRON_NUGGET, 4),
                        new ItemStack(ModItems.SULFUR.get(), 1),
                        new ItemStack(ResourceRegistry.getSmallUnit("titanium"), 1)
                ),
                140));

        // Раствор соды: разделение на гидроксид натрия (NaOH) и воду
        register(new CentrifugeCylinderRecipe(
                ResourceLocation.fromNamespaceAndPath("trd", "soda_centrifuging"),
                new FluidStack(ModFluids.SODA_SOURCE.get(), 1000),
                List.of(
                        new FluidStack(ModFluids.SODIUM_HYDROXIDE_SOURCE.get(), 500),
                        new FluidStack(Fluids.WATER, 500)
                ),
                List.of(),
                120));

        // Серная кислота: разделение на воду и серу
        register(new CentrifugeCylinderRecipe(
                ResourceLocation.fromNamespaceAndPath("trd", "sulfuric_acid_centrifuging"),
                new FluidStack(ModFluids.SULFURIC_ACID_SOURCE.get(), 1000),
                List.of(new FluidStack(Fluids.WATER, 600)),
                List.of(
                        new ItemStack(ModItems.SULFUR.get(), 2)
                ),
                140));

        // Вода: разделение / выпаривание на соль
        register(new CentrifugeCylinderRecipe(
                ResourceLocation.fromNamespaceAndPath("trd", "water_centrifuging"),
                new FluidStack(Fluids.WATER, 1000),
                List.of(),
                List.of(
                        new ItemStack(ModItems.SALT.get(), 2)
                ),
                100));

        // Пероксид водорода: разложение на воду и осадок железа
        register(new CentrifugeCylinderRecipe(
                ResourceLocation.fromNamespaceAndPath("trd", "peroxide_centrifuging"),
                new FluidStack(ModFluids.HYDROGEN_PEROXIDE_SOURCE.get(), 1000),
                List.of(new FluidStack(Fluids.WATER, 800)),
                List.of(
                        new ItemStack(Items.IRON_NUGGET, 1)
                ),
                80));
    }
}
