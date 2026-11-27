package com.trd.api.nature.ore.oil;

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
            // БИТУМИНОЗНЫЕ ПЕСКИ / ЗАМАЗУЧЕННЫЙ ГРУНТ: тяжелые фракции (мазутная + дизельная) ВСЕГДА доминируют
            if (temperature <= 0.25f) {
                // Холодный биом (тундра, снег): тяжелые фракции доминируют
                frac.put(OilFraction.FUEL_OIL, 52 + random.nextInt(6)); // 52-57%
                frac.put(OilFraction.DIESEL, 23 + random.nextInt(4));   // 23-26%
                frac.put(OilFraction.KEROSENE, 10 + random.nextInt(2)); // 10-11%
                frac.put(OilFraction.NAPHTHA, 5 + random.nextInt(2));   // 5-6%
                frac.put(OilFraction.GASOLINE, 3 + random.nextInt(2));  // 3-4%
                frac.put(OilFraction.GAS, 1 + random.nextInt(2));       // 1-2%
            } else if (temperature <= 0.50f) {
                // Умеренно-холодный биом (тайга)
                frac.put(OilFraction.FUEL_OIL, 46 + random.nextInt(5)); // 46-50%
                frac.put(OilFraction.DIESEL, 24 + random.nextInt(3));   // 24-26%
                frac.put(OilFraction.KEROSENE, 12 + random.nextInt(2)); // 12-13%
                frac.put(OilFraction.NAPHTHA, 7 + random.nextInt(2));   // 7-8%
                frac.put(OilFraction.GASOLINE, 4 + random.nextInt(2));  // 4-5%
                frac.put(OilFraction.GAS, 2 + random.nextInt(2));       // 2-3%
            } else if (temperature < 0.90f) {
                // Умеренный биом
                frac.put(OilFraction.FUEL_OIL, 40 + random.nextInt(4)); // 40-43%
                frac.put(OilFraction.DIESEL, 24 + random.nextInt(3));   // 24-26%
                frac.put(OilFraction.KEROSENE, 15 + random.nextInt(3)); // 15-17%
                frac.put(OilFraction.NAPHTHA, 9 + random.nextInt(2));   // 9-10%
                frac.put(OilFraction.GASOLINE, 6 + random.nextInt(2));  // 6-7%
                frac.put(OilFraction.GAS, 2 + random.nextInt(2));       // 2-3%
            } else {
                // Пустыня: климатическая компенсация при лидерстве тяжелых фракций
                frac.put(OilFraction.FUEL_OIL, 34 + random.nextInt(4)); // 34-37%
                frac.put(OilFraction.DIESEL, 25 + random.nextInt(3));   // 25-27%
                frac.put(OilFraction.KEROSENE, 16 + random.nextInt(3)); // 16-18%
                frac.put(OilFraction.NAPHTHA, 11 + random.nextInt(2));  // 11-12%
                frac.put(OilFraction.GASOLINE, 7 + random.nextInt(3));  // 7-9%
                frac.put(OilFraction.GAS, 3 + random.nextInt(2));       // 3-4%
            }
        } else if (reservoirType == ReservoirType.TIGHT_SHALE) {
            // НЕФТЯНОЙ СЛАНЕЦ: сдвиг в сторону средних и легких фракций
            if (temperature <= 0.35f) {
                frac.put(OilFraction.DIESEL, 28 + random.nextInt(4));
                frac.put(OilFraction.FUEL_OIL, 26 + random.nextInt(3));
                frac.put(OilFraction.KEROSENE, 17 + random.nextInt(3));
                frac.put(OilFraction.NAPHTHA, 11 + random.nextInt(2));
                frac.put(OilFraction.GASOLINE, 7 + random.nextInt(2));
                frac.put(OilFraction.GAS, 5 + random.nextInt(2));
            } else if (temperature >= 0.90f) {
                frac.put(OilFraction.NAPHTHA, 24 + random.nextInt(4));
                frac.put(OilFraction.GASOLINE, 23 + random.nextInt(4));
                frac.put(OilFraction.KEROSENE, 20 + random.nextInt(3));
                frac.put(OilFraction.GAS, 12 + random.nextInt(4));
                frac.put(OilFraction.DIESEL, 12 + random.nextInt(3));
                frac.put(OilFraction.FUEL_OIL, 4 + random.nextInt(2));
            } else {
                frac.put(OilFraction.DIESEL, 25 + random.nextInt(4));
                frac.put(OilFraction.KEROSENE, 20 + random.nextInt(3));
                frac.put(OilFraction.NAPHTHA, 18 + random.nextInt(3));
                frac.put(OilFraction.GASOLINE, 15 + random.nextInt(3));
                frac.put(OilFraction.FUEL_OIL, 10 + random.nextInt(3));
                frac.put(OilFraction.GAS, 7 + random.nextInt(3));
            }
        } else {
            // ПОРИСТЫЙ ПЕСЧАНИК: сбалансированная сырая нефть
            if (temperature <= 0.35f) {
                frac.put(OilFraction.FUEL_OIL, 34 + random.nextInt(4));
                frac.put(OilFraction.DIESEL, 26 + random.nextInt(3));
                frac.put(OilFraction.KEROSENE, 16 + random.nextInt(3));
                frac.put(OilFraction.NAPHTHA, 10 + random.nextInt(2));
                frac.put(OilFraction.GASOLINE, 6 + random.nextInt(2));
                frac.put(OilFraction.GAS, 3 + random.nextInt(2));
            } else if (temperature >= 0.90f) {
                frac.put(OilFraction.NAPHTHA, 24 + random.nextInt(4));
                frac.put(OilFraction.GASOLINE, 22 + random.nextInt(4));
                frac.put(OilFraction.KEROSENE, 20 + random.nextInt(3));
                frac.put(OilFraction.DIESEL, 17 + random.nextInt(3));
                frac.put(OilFraction.GAS, 8 + random.nextInt(3));
                frac.put(OilFraction.FUEL_OIL, 4 + random.nextInt(2));
            } else {
                frac.put(OilFraction.DIESEL, 26 + random.nextInt(4));
                frac.put(OilFraction.FUEL_OIL, 20 + random.nextInt(3));
                frac.put(OilFraction.KEROSENE, 18 + random.nextInt(3));
                frac.put(OilFraction.NAPHTHA, 15 + random.nextInt(3));
                frac.put(OilFraction.GASOLINE, 11 + random.nextInt(3));
                frac.put(OilFraction.GAS, 5 + random.nextInt(3));
            }
        }

        // Нормализация суммы фракций ровно к 100%
        int sum = 0;
        for (int v : frac.values()) sum += v;
        if (sum != 100) {
            int diff = 100 - sum;
            OilFraction adjustFraction = reservoirType == ReservoirType.TAR_SANDS
                    ? OilFraction.FUEL_OIL
                    : (temperature <= 0.35f ? OilFraction.FUEL_OIL : (temperature >= 0.90f ? OilFraction.GASOLINE : OilFraction.DIESEL));
            frac.put(adjustFraction, frac.get(adjustFraction) + diff);
        }

        // Газовый фактор и пластовое давление
        float gasRatio;
        float pressure;

        if (reservoirType == ReservoirType.TAR_SANDS || shape == VeinShape.PUDDLE) {
            // Поверхностная залежь (битуминозные пески / лужа): открыта атмосфере, полностью дегазирована, давление атмосферное
            gasRatio = 0.01f + random.nextFloat() * 0.03f; // 1% - 4% остаточного растворенного газа
            pressure = 1.0f + Math.round(random.nextFloat() * 1.5f * 10.0f) / 10.0f; // 1.0 - 2.5 bar
        } else {
            // Подземные залежи (купола, пласты) под непроницаемой покрышкой
            gasRatio = 0.10f + random.nextFloat() * 0.65f;
            float basePressure;
            if (yLevel >= 40) {
                basePressure = 15.0f + random.nextFloat() * 20.0f;
            } else if (yLevel >= 0) {
                basePressure = 50.0f + random.nextFloat() * 40.0f;
            } else {
                basePressure = 130.0f + random.nextFloat() * 60.0f;
            }
            pressure = Math.round(basePressure * (1.0f + 0.8f * gasRatio) * 10.0f) / 10.0f;
        }

        // Сернистость: 50% сладкая (0%), 50% кислая (1.5% - 4.5%)
        float sulfur = 0.0f;
        if (random.nextFloat() < 0.50f) {
            sulfur = 0.015f + random.nextFloat() * 0.030f;
        }

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
                if ("BITUMEN".equals(k)) {
                    fractions.merge(OilFraction.FUEL_OIL, fracTag.getInt(k), Integer::sum);
                    continue;
                }
                try {
                    fractions.put(OilFraction.valueOf(k), fracTag.getInt(k));
                } catch (IllegalArgumentException ignored) {}
            }
            if (!fractions.isEmpty()) {
                for (OilFraction f : OilFraction.values()) {
                    fractions.putIfAbsent(f, 0);
                }
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
