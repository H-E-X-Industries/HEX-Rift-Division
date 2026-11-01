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

    // Месторождение (Cluster of pockets)
    private final boolean isField;
    private final String fieldName;
    private final int totalPockets;
    private final int pocketIndex;

    public OilComposition(Map<OilFraction, Integer> fractions,
                          float gasRatio,
                          float sulfur,
                          float pressure,
                          ReservoirType reservoirType,
                          VeinShape shape,
                          @Nullable ResourceLocation biomeKey,
                          float temperature,
                          boolean isField,
                          String fieldName,
                          int totalPockets,
                          int pocketIndex) {
        this.fractions = Collections.unmodifiableMap(new EnumMap<>(fractions));
        this.gasRatio = gasRatio;
        this.sulfur = sulfur;
        this.pressure = pressure;
        this.reservoirType = reservoirType;
        this.shape = shape;
        this.biomeKey = biomeKey;
        this.temperature = temperature;
        this.isField = isField;
        this.fieldName = fieldName != null ? fieldName : "";
        this.totalPockets = totalPockets;
        this.pocketIndex = pocketIndex;
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

    public OilComposition withReservoirType(ReservoirType newType) {
        if (this.reservoirType == newType) return this;
        return new OilComposition(this.fractions, this.gasRatio, this.sulfur, this.pressure,
                newType, this.shape, this.biomeKey, this.temperature,
                this.isField, this.fieldName, this.totalPockets, this.pocketIndex);
    }

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

    public boolean isField() {
        return isField;
    }

    public String getFieldName() {
        return fieldName;
    }

    public int getTotalPockets() {
        return totalPockets;
    }

    public int getPocketIndex() {
        return pocketIndex;
    }

    public static OilComposition generate(int yLevel, long veinSeed,
                                          Holder<Biome> biomeHolder,
                                          VeinShape shape,
                                          ReservoirType reservoirType,
                                          boolean isField,
                                          String fieldName,
                                          int totalPockets,
                                          int pocketIndex) {
        RandomSource random = RandomSource.create(veinSeed ^ 0x011C0DEC0FFEE1L);
        float temperature = biomeHolder.value().getBaseTemperature();
        ResourceLocation biomeKey = biomeHolder.unwrapKey().map(k -> k.location()).orElse(null);

        Map<OilFraction, Integer> frac = new EnumMap<>(OilFraction.class);

        // === ПРИОРИТЕТ 1: ТИП ЗАЛЕЖИ (ReservoirType) ===
        // === ПРИОРИТЕТ 2: ВЛИЯНИЕ ТЕМПЕРАТУРЫ БИОМА ===
        if (reservoirType == ReservoirType.TAR_SANDS) {
            // БИТУМИНОЗНЫЕ ПЕСКИ / ЗАМАЗУЧЕННЫЙ ГРУНТ: тяжелые фракции ВСЕГДА доминируют!
            if (temperature <= 0.25f) {
                // Холодный биом (тундра, снег): 65-72% тяжелых фракций!
                frac.put(OilFraction.BITUMEN, 42 + random.nextInt(6)); // 42-47%
                frac.put(OilFraction.FUEL_OIL, 23 + random.nextInt(4)); // 23-26%
                frac.put(OilFraction.DIESEL, 14 + random.nextInt(3));   // 14-16%
                frac.put(OilFraction.KEROSENE, 7 + random.nextInt(2));  // 7-8%
                frac.put(OilFraction.NAPHTHA, 4 + random.nextInt(2));   // 4-5%
                frac.put(OilFraction.GASOLINE, 2 + random.nextInt(2));  // 2-3%
            } else if (temperature <= 0.50f) {
                // Умеренно-холодный биом (тайга): 58-64% тяжелых фракций
                frac.put(OilFraction.BITUMEN, 36 + random.nextInt(5)); // 36-40%
                frac.put(OilFraction.FUEL_OIL, 22 + random.nextInt(4)); // 22-25%
                frac.put(OilFraction.DIESEL, 17 + random.nextInt(3));   // 17-19%
                frac.put(OilFraction.KEROSENE, 11 + random.nextInt(2)); // 11-12%
                frac.put(OilFraction.NAPHTHA, 7 + random.nextInt(2));   // 7-8%
                frac.put(OilFraction.GASOLINE, 4 + random.nextInt(2));  // 4-5%
            } else if (temperature < 0.90f) {
                // Умеренный биом: 50-56% тяжелых фракций
                frac.put(OilFraction.BITUMEN, 31 + random.nextInt(4)); // 31-34%
                frac.put(OilFraction.FUEL_OIL, 20 + random.nextInt(3)); // 20-22%
                frac.put(OilFraction.DIESEL, 19 + random.nextInt(3));   // 19-21%
                frac.put(OilFraction.KEROSENE, 14 + random.nextInt(3)); // 14-16%
                frac.put(OilFraction.NAPHTHA, 9 + random.nextInt(2));   // 9-10%
                frac.put(OilFraction.GASOLINE, 5 + random.nextInt(2));  // 5-6%
            } else {
                // Пустыня: за счет климатической компенсации 42-49% тяжелых фракций (тяжелые все еще во главе!)
                frac.put(OilFraction.BITUMEN, 26 + random.nextInt(4)); // 26-29%
                frac.put(OilFraction.FUEL_OIL, 18 + random.nextInt(3)); // 18-20%
                frac.put(OilFraction.DIESEL, 20 + random.nextInt(3));   // 20-22%
                frac.put(OilFraction.KEROSENE, 16 + random.nextInt(3)); // 16-18%
                frac.put(OilFraction.NAPHTHA, 11 + random.nextInt(2));  // 11-12%
                frac.put(OilFraction.GASOLINE, 7 + random.nextInt(3));  // 7-9%
            }
        } else if (reservoirType == ReservoirType.TIGHT_SHALE) {
            // НЕФТЯНОЙ СЛАНЕЦ: сдвиг в сторону средних и легких фракций
            if (temperature <= 0.35f) {
                frac.put(OilFraction.DIESEL, 26 + random.nextInt(4));
                frac.put(OilFraction.FUEL_OIL, 23 + random.nextInt(3));
                frac.put(OilFraction.BITUMEN, 18 + random.nextInt(3));
                frac.put(OilFraction.KEROSENE, 16 + random.nextInt(3));
                frac.put(OilFraction.NAPHTHA, 10 + random.nextInt(2));
                frac.put(OilFraction.GASOLINE, 5 + random.nextInt(2));
            } else if (temperature >= 0.90f) {
                frac.put(OilFraction.NAPHTHA, 25 + random.nextInt(4));
                frac.put(OilFraction.GASOLINE, 24 + random.nextInt(4));
                frac.put(OilFraction.KEROSENE, 21 + random.nextInt(3));
                frac.put(OilFraction.DIESEL, 17 + random.nextInt(3));
                frac.put(OilFraction.FUEL_OIL, 8 + random.nextInt(2));
                frac.put(OilFraction.BITUMEN, 4 + random.nextInt(2));
            } else {
                frac.put(OilFraction.DIESEL, 24 + random.nextInt(4));
                frac.put(OilFraction.KEROSENE, 20 + random.nextInt(3));
                frac.put(OilFraction.NAPHTHA, 18 + random.nextInt(3));
                frac.put(OilFraction.GASOLINE, 15 + random.nextInt(3));
                frac.put(OilFraction.FUEL_OIL, 14 + random.nextInt(2));
                frac.put(OilFraction.BITUMEN, 8 + random.nextInt(2));
            }
        } else {
            // ПОРИСТЫЙ ПЕСЧАНИК: сбалансированная сырая нефть
            if (temperature <= 0.35f) {
                frac.put(OilFraction.FUEL_OIL, 27 + random.nextInt(4));
                frac.put(OilFraction.BITUMEN, 23 + random.nextInt(4));
                frac.put(OilFraction.DIESEL, 22 + random.nextInt(3));
                frac.put(OilFraction.KEROSENE, 14 + random.nextInt(2));
                frac.put(OilFraction.NAPHTHA, 8 + random.nextInt(2));
                frac.put(OilFraction.GASOLINE, 5 + random.nextInt(2));
            } else if (temperature >= 0.90f) {
                frac.put(OilFraction.NAPHTHA, 24 + random.nextInt(4));
                frac.put(OilFraction.GASOLINE, 22 + random.nextInt(4));
                frac.put(OilFraction.KEROSENE, 20 + random.nextInt(3));
                frac.put(OilFraction.DIESEL, 18 + random.nextInt(3));
                frac.put(OilFraction.FUEL_OIL, 11 + random.nextInt(2));
                frac.put(OilFraction.BITUMEN, 4 + random.nextInt(2));
            } else {
                frac.put(OilFraction.DIESEL, 25 + random.nextInt(4));
                frac.put(OilFraction.FUEL_OIL, 20 + random.nextInt(3));
                frac.put(OilFraction.KEROSENE, 18 + random.nextInt(3));
                frac.put(OilFraction.NAPHTHA, 15 + random.nextInt(3));
                frac.put(OilFraction.GASOLINE, 12 + random.nextInt(2));
                frac.put(OilFraction.BITUMEN, 9 + random.nextInt(2));
            }
        }

        // Нормализация суммы фракций ровно к 100%
        int sum = 0;
        for (int v : frac.values()) sum += v;
        if (sum != 100) {
            int diff = 100 - sum;
            OilFraction adjustFraction = reservoirType == ReservoirType.TAR_SANDS
                    ? OilFraction.BITUMEN
                    : (temperature <= 0.35f ? OilFraction.FUEL_OIL : (temperature >= 0.90f ? OilFraction.GASOLINE : OilFraction.DIESEL));
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
            basePressure = 12.0f + random.nextFloat() * 18.0f;
        } else if (yLevel >= 0) {
            basePressure = 60.0f + random.nextFloat() * 45.0f;
        } else {
            basePressure = 140.0f + random.nextFloat() * 70.0f;
        }
        float pressure = Math.round(basePressure * (1.0f + 2.5f * gasRatio) * 10.0f) / 10.0f;

        return new OilComposition(frac, gasRatio, sulfur, pressure, reservoirType, shape, biomeKey, temperature,
                isField, fieldName, totalPockets, pocketIndex);
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

        tag.putBoolean("IsField", isField);
        tag.putString("FieldName", fieldName);
        tag.putInt("TotalPockets", totalPockets);
        tag.putInt("PocketIndex", pocketIndex);
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

        boolean isField = tag.getBoolean("IsField");
        String fieldName = tag.getString("FieldName");
        int totalPockets = tag.getInt("TotalPockets");
        int pocketIndex = tag.getInt("PocketIndex");

        return new OilComposition(fractions, gasRatio, sulfur, pressure, resType, shape, biomeKey, temp,
                isField, fieldName, totalPockets, pocketIndex);
    }
}
