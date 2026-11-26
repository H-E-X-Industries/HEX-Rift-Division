package com.trd.api.capabilities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.trd.api.nature.ore.oil.OilComposition;
import com.trd.api.nature.ore.oil.OilFraction;
import com.trd.api.nature.ore.oil.ReservoirType;
import com.trd.main.MainRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
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
    // ПАРАМЕТРЫ СКВАЖИННОГО ФЛЮИДА (DataComponentType на FluidStack)
    // =========================================================

    public record WellheadFluidData(
            float pressure,
            String reservoirType,
            float gasRatio,
            float sulfur,
            int gasoline,
            int naphtha,
            int kerosene,
            int diesel,
            int fuelOil,
            int bitumen
    ) {
        public static final WellheadFluidData DEFAULT = new WellheadFluidData(
                15.0f, ReservoirType.POROUS_STONE.getId(), 0.15f, 0.0f,
                12, 15, 18, 25, 20, 10
        );

        public static WellheadFluidData fromComposition(@Nullable OilComposition comp) {
            if (comp == null) return DEFAULT;
            Map<OilFraction, Integer> f = comp.getFractions();
            return new WellheadFluidData(
                    comp.getPressure(),
                    comp.getReservoirType().getId(),
                    comp.getGasRatio(),
                    comp.getSulfur(),
                    f.getOrDefault(OilFraction.GASOLINE, 0),
                    f.getOrDefault(OilFraction.NAPHTHA, 0),
                    f.getOrDefault(OilFraction.KEROSENE, 0),
                    f.getOrDefault(OilFraction.DIESEL, 0),
                    f.getOrDefault(OilFraction.FUEL_OIL, 0),
                    f.getOrDefault(OilFraction.BITUMEN, 0)
            );
        }

        public ReservoirType getReservoirTypeEnum() {
            for (ReservoirType t : ReservoirType.values()) {
                if (t.getId().equalsIgnoreCase(reservoirType) || t.name().equalsIgnoreCase(reservoirType)) {
                    return t;
                }
            }
            return ReservoirType.POROUS_STONE;
        }

        public float corrosivenessPercent() {
            return Math.min(100.0f, Math.max(0.0f, sulfur * 20.0f * 100.0f));
        }
    }

    public static final Codec<WellheadFluidData> WELLHEAD_FLUID_DATA_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("pressure").forGetter(WellheadFluidData::pressure),
                    Codec.STRING.fieldOf("reservoir_type").forGetter(WellheadFluidData::reservoirType),
                    Codec.FLOAT.fieldOf("gas_ratio").forGetter(WellheadFluidData::gasRatio),
                    Codec.FLOAT.fieldOf("sulfur").forGetter(WellheadFluidData::sulfur),
                    Codec.INT.fieldOf("gasoline").forGetter(WellheadFluidData::gasoline),
                    Codec.INT.fieldOf("naphtha").forGetter(WellheadFluidData::naphtha),
                    Codec.INT.fieldOf("kerosene").forGetter(WellheadFluidData::kerosene),
                    Codec.INT.fieldOf("diesel").forGetter(WellheadFluidData::diesel),
                    Codec.INT.fieldOf("fuel_oil").forGetter(WellheadFluidData::fuelOil),
                    Codec.INT.fieldOf("bitumen").forGetter(WellheadFluidData::bitumen)
            ).apply(instance, WellheadFluidData::new)
    );

    public static final StreamCodec<ByteBuf, WellheadFluidData> WELLHEAD_FLUID_DATA_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WellheadFluidData decode(ByteBuf buf) {
            return new WellheadFluidData(
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf)
            );
        }

        @Override
        public void encode(ByteBuf buf, WellheadFluidData val) {
            ByteBufCodecs.FLOAT.encode(buf, val.pressure());
            ByteBufCodecs.STRING_UTF8.encode(buf, val.reservoirType());
            ByteBufCodecs.FLOAT.encode(buf, val.gasRatio());
            ByteBufCodecs.FLOAT.encode(buf, val.sulfur());
            ByteBufCodecs.VAR_INT.encode(buf, val.gasoline());
            ByteBufCodecs.VAR_INT.encode(buf, val.naphtha());
            ByteBufCodecs.VAR_INT.encode(buf, val.kerosene());
            ByteBufCodecs.VAR_INT.encode(buf, val.diesel());
            ByteBufCodecs.VAR_INT.encode(buf, val.fuelOil());
            ByteBufCodecs.VAR_INT.encode(buf, val.bitumen());
        }
    };

    public static final Supplier<DataComponentType<WellheadFluidData>> WELLHEAD_FLUID_DATA = DATA_COMPONENTS.register("wellhead_fluid_data",
            () -> DataComponentType.<WellheadFluidData>builder()
                    .persistent(WELLHEAD_FLUID_DATA_CODEC)
                    .networkSynchronized(WELLHEAD_FLUID_DATA_STREAM_CODEC)
                    .build());

    // =========================================================

    public static void register(IEventBus eventBus) {
        DATA_COMPONENTS.register(eventBus);
    }
}
