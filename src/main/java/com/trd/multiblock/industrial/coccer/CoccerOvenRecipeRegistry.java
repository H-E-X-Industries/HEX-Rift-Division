package com.trd.multiblock.industrial.coccer;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class CoccerOvenRecipeRegistry {
    private static final Map<net.minecraft.world.item.Item, CoccerOvenRecipe> RECIPES = new HashMap<>();

    public static void init() {
        register(Items.WET_SPONGE, new net.minecraft.world.item.ItemStack(Items.SPONGE),
                new FluidStack(Fluids.WATER, 1000), 100, 60);

        register(Items.MAGMA_BLOCK, new net.minecraft.world.item.ItemStack(Items.BASALT),
                new FluidStack(Fluids.LAVA, 100), 800, 100);
    }

    private static void register(net.minecraft.world.item.Item input, net.minecraft.world.item.ItemStack outItem,
                                 FluidStack outFluid, int reqTemp, int ticks) {
        RECIPES.put(input, new CoccerOvenRecipe(input, outItem, outFluid, reqTemp, ticks));
    }

    @Nullable
    public static CoccerOvenRecipe findRecipe(net.minecraft.world.item.Item input) {
        return RECIPES.get(input);
    }
}