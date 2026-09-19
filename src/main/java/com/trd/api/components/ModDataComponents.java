package com.trd.api.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Универсальная система Data Components для предметов (замена ItemStack NBT из 1.20.1)
 */
public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MainRegistry.MOD_ID);

    // =========================================================
    // БАЗОВЫЕ ТИПЫ ДАННЫХ (Универсальные замены)
    // =========================================================

    // Целочисленное значение (Для температуры, режимов работы, тиров)
    public static final Supplier<DataComponentType<Integer>> INT_VALUE = DATA_COMPONENTS.register("int_value",
            () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .build());

    // Длинное целое (Для хранения энергии или объемов жидкости в предметах)
    public static final Supplier<DataComponentType<Long>> LONG_VALUE = DATA_COMPONENTS.register("long_value",
            () -> DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .build());

    // Логическое значение (Для переключателей on/off, как в детонаторах)
    public static final Supplier<DataComponentType<Boolean>> BOOLEAN_VALUE = DATA_COMPONENTS.register("boolean_value",
            () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .build());

    // Строковое значение (Для ID, названий, ссылок)
    public static final Supplier<DataComponentType<String>> STRING_VALUE = DATA_COMPONENTS.register("string_value",
            () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .build());

    // Позиция блока (Для пультов, ключей привязки и коннекторов)
    public static final Supplier<DataComponentType<BlockPos>> BLOCK_POS_VALUE = DATA_COMPONENTS.register("block_pos_value",
            () -> DataComponentType.<BlockPos>builder()
                    .persistent(BlockPos.CODEC)
                    .build());

    // =========================================================
    // ПРИМЕР СЛОЖНОГО КОМПОНЕНТА (Custom Record)
    // =========================================================
    
    /**
     * Пример записи для хранения данных о жидкости.
     * Record - идеальный формат для NeoForge 1.21.1 Data Components.
     */
    public record FluidData(String fluidName, long amount) {}

    // Кодек для сериализации/десериализации FluidData (замена CompoundTag)
    public static final Codec<FluidData> FLUID_DATA_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("fluid_name").forGetter(FluidData::fluidName),
                    Codec.LONG.fieldOf("amount").forGetter(FluidData::amount)
            ).apply(instance, FluidData::new)
    );

    // Сам компонент для жидкости
    public static final Supplier<DataComponentType<FluidData>> FLUID_DATA = DATA_COMPONENTS.register("fluid_data",
            () -> DataComponentType.<FluidData>builder()
                    .persistent(FLUID_DATA_CODEC)
                    .build());

    // =========================================================

    public static void register(IEventBus eventBus) {
        DATA_COMPONENTS.register(eventBus);
    }
}
