package com.trd.multiblock.industrial.stanok;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Статический реестр рецептов станка.
 * Рецепты регистрируются в {@link StanokRecipes#register()} при запуске мода.
 */
public class StanokRecipeRegistry {

    private static final Map<ResourceLocation, StanokRecipe> RECIPES = new LinkedHashMap<>();

    public static void register(StanokRecipe recipe) {
        RECIPES.put(recipe.getId(), recipe);
    }

    public static List<StanokRecipe> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(RECIPES.values()));
    }

    public static StanokRecipe getById(ResourceLocation id) {
        return RECIPES.get(id);
    }

    public static List<StanokRecipe> getForCarriage(CarriageType type) {
        List<StanokRecipe> result = new ArrayList<>();
        for (StanokRecipe recipe : RECIPES.values()) {
            if (recipe.getCarriageType() == type) result.add(recipe);
        }
        return result;
    }
}
