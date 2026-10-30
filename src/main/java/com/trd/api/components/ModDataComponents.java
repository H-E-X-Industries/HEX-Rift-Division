package com.trd.api.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Універсальна система Data Components для предметів (заміна ItemStack NBT у 1.21.1)
 */
public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MainRegistry.MOD_ID);

    // =========================================================
    // БАЗОВІ ТИПИ ДАНИХ (Універсальні заміни)
    // =========================================================

    // Physical durability and wear tracking component for electric lamps
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LampComponent>> LAMP_DATA =
            DATA_COMPONENTS.register("lamp_data",
                    () -> DataComponentType.<LampComponent>builder()
                            .persistent(LampComponent.CODEC)
                            .networkSynchronized(LampComponent.STREAM_CODEC)
                            .build());

    // Цілочисельне значення (Для температури, режимів роботи, тирів)
    public static final Supplier<DataComponentType<Integer>> INT_VALUE = DATA_COMPONENTS.register("int_value",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .build());

    // Довге ціле (Для зберігання енергії чи об'ємів рідини в предметах)
    public static final Supplier<DataComponentType<Long>> LONG_VALUE = DATA_COMPONENTS.register("long_value",
            () -> DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .build());

    // Логічне значення (Для перемикачів on/off)
    public static final Supplier<DataComponentType<Boolean>> BOOLEAN_VALUE = DATA_COMPONENTS.register("boolean_value",
            () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .build());

    // Рядкове значення (Для ID, назв, посилань)
    public static final Supplier<DataComponentType<String>> STRING_VALUE = DATA_COMPONENTS.register("string_value",
            () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .build());

    // Позиція блоку (Для пультів, ключів прив'язки та конекторів)
    public static final Supplier<DataComponentType<BlockPos>> BLOCK_POS_VALUE = DATA_COMPONENTS.register("block_pos_value",
            () -> DataComponentType.<BlockPos>builder()
                    .persistent(BlockPos.CODEC)
                    .build());

    // =========================================================
    // ПРИКЛАД СКЛАДНОГО КОМПОНЕНТА (Custom Record)
    // =========================================================
    
    public record FluidData(String fluidName, long amount) {}

    public static final Codec<FluidData> FLUID_DATA_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("fluid_name").forGetter(FluidData::fluidName),
                    Codec.LONG.fieldOf("amount").forGetter(FluidData::amount)
            ).apply(instance, FluidData::new)
    );

    public static final Supplier<DataComponentType<FluidData>> FLUID_DATA = DATA_COMPONENTS.register("fluid_data",
            () -> DataComponentType.<FluidData>builder()
                    .persistent(FLUID_DATA_CODEC)
                    .build());

    // =========================================================

    public static void register(IEventBus eventBus) {
        DATA_COMPONENTS.register(eventBus);
    }
}
