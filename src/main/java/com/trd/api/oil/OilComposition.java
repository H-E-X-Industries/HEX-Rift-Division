package com.trd.api.oil;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class OilComposition {

    private final Map<OilFraction, Integer> fractions;
    private final float gasRatio;
    private final float sulfur;
    private final float pressure;
    private final ReservoirType reservoirType;
    private final VeinShape shape;
    @Nullable
    private final ResourceLocation biomeKey;
    private final float temperature;

    public OilComposition(Map<OilFraction, Integer> fractions,
                          float gasRatio,
                          float sulfur,
                          float pressure,
                          ReservoirType reservoirType,
                          VeinShape shape,
                          @Nullable ResourceLocation biomeKey,
                          float temperature) {
        this.fractions = Collections.unmodifiableMap(new EnumMap<>(fractions));
        this.gasRatio = gasRatio;
        this.sulfur = sulfur;
        this.pressure = pressure;
        this.reservoirType = reservoirType;
        this.shape = shape;
        this.biomeKey = biomeKey;
        this.temperature = temperature;
    }

    public Map<OilFraction, Integer> getFractions() {
        return fractions;
    }

    public float getGasRatio() {
        return gasRatio;
    }

    public float getSulfur() {
        return sulfur;
    }

    public boolean isSour() {
        return sulfur > 0.0f;
    }

    /**
     * Коэффициент коррозийности сырой нефти [0.0 ... 1.0].
     * Чем выше содержание серы (H2S), тем выше коррозия труб и оборудования при выкачке.
     */
    public float getCorrosiveness() {
        return Math.min(1.0f, sulfur * 20.0f);
    }

    public float getCorrosivenessPercent() {
        return getCorrosiveness() * 100.0f;
    }

    public float getPressure() {
        return pressure;
    }

    public ReservoirType getReservoirType() {
        return reservoirType;
    }

    public VeinShape getShape() {
        return shape;
    }

    @Nullable
    public ResourceLocation getBiomeKey() {
        return biomeKey;
    }

    public float getTemperature() {
        return temperature;
    }

    public static OilComposition generate(int yLevel, long veinSeed,
                                          Holder<Biome> biomeHolder,
                                          VeinShape shape,
                                          ReservoirType reservoirType) {
        RandomSource random = RandomSource.create(veinSeed ^ 0x011C0DEC0FFEE1L);
        float temperature = biomeHolder.value().getBaseTemperature();
        ResourceLocation biomeKey = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);

        Map<OilFraction, Integer> frac = new EnumMap<>(OilFraction.class);

        // Климатический спектр фракций по температуре
        if (temperature <= 0.35f) {
            // Холодный климат (тундра, тайга, снежные пики): тяжелые фракции
            frac.put(OilFraction.BITUMEN, 38 + random.nextInt(6)); // 38-43% Гудроновая
            frac.put(OilFraction.FUEL_OIL, 26 + random.nextInt(5)); // 26-30% Мазутная
            frac.put(OilFraction.DIESEL, 16 + random.nextInt(4));   // 16-19% Дизельная
            frac.put(OilFraction.KEROSENE, 7 + random.nextInt(3));  // 7-9% Керосиновая
            frac.put(OilFraction.NAPHTHA, 3 + random.nextInt(2));   // 3-4% Лигроиновая
            frac.put(OilFraction.GASOLINE, 1 + random.nextInt(2));  // 1-2% Бензиновая
        } else if (temperature >= 1.0f) {
            // Жаркий климат (пустыни, саванны, меза): легкие фракции
            frac.put(OilFraction.GASOLINE, 22 + random.nextInt(6)); // 22-27% Бензиновая
            frac.put(OilFraction.NAPHTHA, 24 + random.nextInt(5));  // 24-28% Лигроиновая
            frac.put(OilFraction.KEROSENE, 18 + random.nextInt(4)); // 18-21% Керосиновая
            frac.put(OilFraction.DIESEL, 14 + random.nextInt(4));   // 14-17% Дизельная
            frac.put(OilFraction.FUEL_OIL, 8 + random.nextInt(4));  // 8-11% Мазутная
            frac.put(OilFraction.BITUMEN, 2 + random.nextInt(3));   // 2-4% Гудроновая
        } else {
            // Умеренный климат (равнины, леса)
            frac.put(OilFraction.DIESEL, 23 + random.nextInt(5));   // 23-27% Дизельная
            frac.put(OilFraction.FUEL_OIL, 18 + random.nextInt(5)); // 18-22% Мазутная
            frac.put(OilFraction.KEROSENE, 14 + random.nextInt(4)); // 14-17% Керосиновая
            frac.put(OilFraction.BITUMEN, 14 + random.nextInt(4));  // 14-17% Гудроновая
            frac.put(OilFraction.NAPHTHA, 12 + random.nextInt(3));  // 12-14% Лигроиновая
            frac.put(OilFraction.GASOLINE, 11 + random.nextInt(3)); // 11-13% Бензиновая
        }

        // Нормализация суммы фракций ровно к 100%
        int sum = 0;
        for (int v : frac.values()) sum += v;
        if (sum != 100) {
            int diff = 100 - sum;
            OilFraction adjustFraction = temperature <= 0.35f ? OilFraction.BITUMEN
                    : (temperature >= 1.0f ? OilFraction.GASOLINE : OilFraction.DIESEL);
            frac.put(adjustFraction, frac.get(adjustFraction) + diff);
        }

        // Газовый фактор (5% - 80%)
        float gasRatio = 0.05f + random.nextFloat() * 0.75f;

        // Сернистость: 50% сладкая (0%), 50% кислая (1.5% - 4.5%)
        float sulfur = 0.0f;
        if (random.nextFloat() < 0.50f) {
            sulfur = 0.015f + random.nextFloat() * 0.030f;
        }

        // Базовое гидростатическое/литостатическое давление по горизонту Y
        float basePressure;
        if (yLevel >= 40) {
            basePressure = 12.0f + random.nextFloat() * 18.0f; // 12-30 bar (поверхность)
        } else if (yLevel >= 0) {
            basePressure = 60.0f + random.nextFloat() * 45.0f; // 60-105 bar (средний)
        } else {
            basePressure = 140.0f + random.nextFloat() * 70.0f; // 140-210 bar (глубокий сланец)
        }
        float pressure = Math.round(basePressure * (1.0f + 2.5f * gasRatio) * 10.0f) / 10.0f;

        return new OilComposition(frac, gasRatio, sulfur, pressure, reservoirType, shape, biomeKey, temperature);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag fracTag = new CompoundTag();
        fractions.forEach((f, v) -> fracTag.putInt(f.name(), v));
        tag.put("Fractions", fracTag);
        tag.putFloat("GasRatio", gasRatio);
        tag.putFloat("Sulfur", sulfur);
        tag.putFloat("Pressure", pressure);
        tag.putString("ReservoirType", reservoirType.name());
        tag.putString("Shape", shape.name());
        if (biomeKey != null) tag.putString("Biome", biomeKey.toString());
        tag.putFloat("Temp", temperature);
        return tag;
    }

    public static OilComposition load(CompoundTag tag) {
        Map<OilFraction, Integer> fractions = new EnumMap<>(OilFraction.class);
        if (tag.contains("Fractions", CompoundTag.TAG_COMPOUND)) {
            CompoundTag fracTag = tag.getCompound("Fractions");
            for (String k : fracTag.getAllKeys()) {
                try {
                    fractions.put(OilFraction.valueOf(k), fracTag.getInt(k));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        float gasRatio = tag.getFloat("GasRatio");
        float sulfur = tag.getFloat("Sulfur");
        float pressure = tag.getFloat("Pressure");
        ReservoirType resType = ReservoirType.POROUS_STONE;
        if (tag.contains("ReservoirType")) {
            try {
                resType = ReservoirType.valueOf(tag.getString("ReservoirType"));
            } catch (IllegalArgumentException ignored) {}
        }
        VeinShape shape = VeinShape.DOME;
        if (tag.contains("Shape")) {
            try {
                shape = VeinShape.valueOf(tag.getString("Shape"));
            } catch (IllegalArgumentException ignored) {}
        }
        ResourceLocation biomeKey = tag.contains("Biome") ? ResourceLocation.tryParse(tag.getString("Biome")) : null;
        float temp = tag.getFloat("Temp");

        return new OilComposition(fractions, gasRatio, sulfur, pressure, resType, shape, biomeKey, temp);
    }
}
