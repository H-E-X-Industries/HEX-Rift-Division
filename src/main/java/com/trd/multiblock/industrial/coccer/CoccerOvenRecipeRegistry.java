package com.trd.multiblock.industrial.coccer;

import com.trd.api.fluids.ModFluids;
import com.trd.item.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class CoccerOvenRecipeRegistry {
    private static final Map<net.minecraft.world.item.Item, CoccerOvenRecipe> RECIPES = new HashMap<>();

    public static void init() {
        register(Items.WET_SPONGE, new ItemStack(Items.SPONGE),
                new FluidStack(Fluids.WATER, 1000), 100, 60);

        register(Items.MAGMA_BLOCK, new ItemStack(Items.BASALT),
                new FluidStack(Fluids.LAVA, 100), 800, 100);

        register(Items.GLOWSTONE, new ItemStack(ModItems.SULFUR.get()),
                new FluidStack(ModFluids.SULFURIC_ACID_SOURCE.get(), 25), 440, 30);

        register(ModItems.CINNABAR.get(), new ItemStack(Items.AIR),
                new FluidStack(ModFluids.MERCURY_SOURCE.get(), 100), 1200, 60);

        register(ModItems.SODA_CRYSTAL.get(), new ItemStack(ModItems.SODA.get()),
                new FluidStack(Fluids.WATER, 250), 100, 60);

        // Прокалка гидроксида алюминия -> глинозём + пар воды
        register(ModItems.ALUMINUM_HYDROXIDE.get(), new ItemStack(ModItems.ALUMINA.get()),
                new FluidStack(Fluids.WATER, 50), 1100, 80);
    }

    private static void register(net.minecraft.world.item.Item input, net.minecraft.world.item.ItemStack outItem,
                                 FluidStack outFluid, int reqTemp, int ticks) {
        RECIPES.put(input, new CoccerOvenRecipe(input, outItem, outFluid, reqTemp, ticks));
    }

    @Nullable
    public static CoccerOvenRecipe findRecipe(net.minecraft.world.item.Item input) {
        return RECIPES.get(input);
    }

    public static java.util.Collection<CoccerOvenRecipe> getAllRecipes() {
        return java.util.Collections.unmodifiableCollection(RECIPES.values());
    }
}