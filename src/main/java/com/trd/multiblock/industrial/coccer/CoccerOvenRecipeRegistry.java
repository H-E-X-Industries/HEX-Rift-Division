package com.trd.multiblock.industrial.coccer;

import com.trd.api.fluids.ModFluids;
import com.trd.item.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CoccerOvenRecipeRegistry {
    private static final Map<Item, CoccerOvenRecipe> RECIPES = new HashMap<>();

    public static void init() {
        RECIPES.clear();

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
                new FluidStack(Fluids.WATER, 50), 660, 30);
    }

    private static void register(Item input, ItemStack outItem,
                                 FluidStack outFluid, int reqTemp, int ticks) {
        RECIPES.put(input, new CoccerOvenRecipe(input, outItem, outFluid, reqTemp, ticks));
    }

    @Nullable
    public static CoccerOvenRecipe findRecipe(Item input) {
        return RECIPES.get(input);
    }

    public static Collection<CoccerOvenRecipe> getAllRecipes() {
        return Collections.unmodifiableCollection(RECIPES.values());
    }
}
