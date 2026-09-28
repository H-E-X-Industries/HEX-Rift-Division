package com.trd.api.energy;

import com.trd.item.industrial.energy.WireCoilWindingRecipe;
import com.trd.main.MainRegistry;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Регистры рецептов (кастомные сериализаторы).
 */
public class ModRecipes {
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MainRegistry.MOD_ID);

    /** Намотка катушки: катушка + N проводов -> катушка с +N проводами */
    public static final RegistryObject<net.minecraft.world.item.crafting.RecipeSerializer<?>> WIRE_COIL_WINDING =
            SERIALIZERS.register("wire_coil_winding",
                    WireCoilWindingRecipe.Serializer::new);

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
