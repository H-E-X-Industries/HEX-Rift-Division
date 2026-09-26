package com.trd.api.recipe;

import com.trd.main.MainRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, MainRegistry.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MainRegistry.MOD_ID);

    public static final Supplier<RecipeType<MillstoneRecipe>> MILLSTONE_TYPE =
            RECIPE_TYPES.register("grinding", () -> new RecipeType<MillstoneRecipe>() {
                @Override
                public String toString() {
                    return "grinding";
                }
            });

    public static final Supplier<RecipeSerializer<MillstoneRecipe>> MILLSTONE_SERIALIZER =
            RECIPE_SERIALIZERS.register("grinding", MillstoneRecipe.Serializer::new);

    public static final Supplier<RecipeType<com.trd.api.metallurgy.system.recipe.SmeltRecipe>> SMELTER_MELTING_TYPE =
            RECIPE_TYPES.register("smelter_melting", () -> new RecipeType<com.trd.api.metallurgy.system.recipe.SmeltRecipe>() {
                @Override
                public String toString() {
                    return "smelter_melting";
                }
            });

    public static final Supplier<RecipeSerializer<com.trd.api.metallurgy.system.recipe.SmeltRecipe>> SMELTER_MELTING_SERIALIZER =
            RECIPE_SERIALIZERS.register("smelter_melting", com.trd.api.metallurgy.system.recipe.SmeltRecipe.Serializer::new);

    public static final Supplier<RecipeSerializer<com.trd.api.metallurgy.system.recipe.SmeltRecipe>> MELTING_SERIALIZER =
            RECIPE_SERIALIZERS.register("melting", com.trd.api.metallurgy.system.recipe.SmeltRecipe.Serializer::new);

    public static void register(IEventBus eventBus) {
        RECIPE_TYPES.register(eventBus);
        RECIPE_SERIALIZERS.register(eventBus);
    }
}
