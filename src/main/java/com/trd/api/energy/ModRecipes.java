package com.trd.api.energy;

import com.trd.item.industrial.energy.WireCoilWindingRecipe;
import com.trd.main.MainRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Регистры рецептов (кастомные сериализаторы).
 */
public class ModRecipes {
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MainRegistry.MOD_ID);

    /** Намотка катушки: катушка + N проводов -> катушка с +N проводами */
    public static final java.util.function.Supplier<net.minecraft.world.item.crafting.RecipeSerializer<?>> WIRE_COIL_WINDING =
            SERIALIZERS.register("wire_coil_winding",
                    WireCoilWindingRecipe.Serializer::new);

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
