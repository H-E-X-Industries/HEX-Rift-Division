package com.trd.api.vein;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Слоёная матрица фракции («пирог»). Каждый металл живёт ровно в одном слое.
 * Слой = глубина извлечения = сложность реагента:
 * <pre>
 *   0 — пероксид водорода   (очень легко)  — базовые металлы
 *   1 — серная кислота      (средне)       — средние
 *   2 — щёлочь              (сложно)       — упрямые
 *   3 — соляная кислота     (крайне тяжело)— максимум/топ
 * </pre>
 *
 * Веса металлов берутся из {@link FractionType#getMetalWeights()} (единый источник
 * «обильности»). Матрица задаёт только принадлежность металла слою. Слой может
 * оставаться пустым (в нём нет металлов) — такие слои пропускаются при извлечении.
 *
 * Класс можно грузить без Minecraft: реагенты хранятся строковыми ResourceLocation
 * и резолвятся в {@link Fluid} лениво ({@link #getLayerFluid(int)}).
 *
 * Разбиение OU (целые гранулы, без дробных долей) выполняет {@link DistributionMath}.
 */
public class FractionLayerMatrix {

    public static final int MAX_LAYERS = 4;

    /** Стандартные реагенты по глубине слоя (индекс = глубина). */
    public static final String[] STANDARD_FLUIDS = {
            "trd:hydrogen_peroxide",
            "trd:sulfuric_acid",
            "trd:sodium_hydroxide",
            "trd:hydrogen_chlorine"
    };

    /** Запись «металл + вес обильности» внутри слоя. */
    public record MetalEntry(String metal, int weight) {}

    /** Один слой пирога. */
    public record Layer(int index, String fluidId, List<MetalEntry> metals) {
        public boolean isEmpty() {
            return metals.isEmpty();
        }

        public int getTotalWeight() {
            int sum = 0;
            for (MetalEntry entry : metals) sum += entry.weight();
            return sum;
        }
    }

    // ══════════════════ МАТРИЦЫ ФРАКЦИЙ ══════════════════

    /**
     * Лёгкие металлы — от дешёвых (слой 0, пероксид) к самым ценным (слой 3, соляная).
     * Порядок по решению пользователя: цена растёт медь → алюминий → бериллий → титан.
     */
    public static final FractionLayerMatrix LIGHT = builder(FractionType.LIGHT_METAL)
            .layer(0, "copper")
            .layer(1, "aluminum")
            .layer(2, "beryllium", "titanium")
            .build();

    /** Тяжёлые металлы (эталонный пример из плана). Слой 3 пустой — топ-слой пока не заполнен. */
    public static final FractionLayerMatrix HEAVY = builder(FractionType.HEAVY_METAL)
            .layer(0, "iron", "tin")
            .layer(1, "zinc", "lead")
            .layer(2, "tungsten")
            .build();

    /** Благородные: золото — только в самом глубоком слое (соляная кислота). */
    public static final FractionLayerMatrix NOBLE = builder(FractionType.NOBLE_METAL)
            .layer(1, "gold")
            .build();

    /** Редкоземельные: неодим — только в самом глубоком слое (соляная кислота). */
    public static final FractionLayerMatrix RARE_EARTH = builder(FractionType.RARE_EARTH)
            .layer(1, "neodymium")
            .build();

    /** Матрица по типу фракции. */
    public static FractionLayerMatrix forFraction(FractionType fraction) {
        return switch (fraction) {
            case LIGHT_METAL -> LIGHT;
            case HEAVY_METAL -> HEAVY;
            case NOBLE_METAL -> NOBLE;
            case RARE_EARTH -> RARE_EARTH;
        };
    }

    public static Builder builder(FractionType fraction) {
        return new Builder(fraction);
    }

    // ══════════════════ INSTANCE ══════════════════

    private final FractionType fraction;
    private final List<Layer> layers;

    private FractionLayerMatrix(FractionType fraction, List<Layer> layers) {
        this.fraction = fraction;
        this.layers = layers;
    }

    public FractionType getFraction() {
        return fraction;
    }

    /** Слои 0..3 (всегда полный список, пустые слои тоже присутствуют). */
    public List<Layer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    /** Все металлы фракции с их весами (объединение по слоям). */
    public Map<String, Integer> getAllMetalWeights() {
        Map<String, Integer> all = new LinkedHashMap<>();
        for (Layer layer : layers) {
            for (MetalEntry entry : layer.metals()) {
                all.put(entry.metal(), entry.weight());
            }
        }
        return all;
    }

    /**
     * Запекает веса металлов фракции с учётом биом-бустов. Веса без буста остаются
     * эталонными (из {@link FractionType}), бустированные — округляются, минимум 1.
     * Чистый метод: работает с {@link VeinModifier} без Minecraft.
     */
    public Map<String, Integer> bakeWeights(VeinModifier modifier) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : getAllMetalWeights().entrySet()) {
            float boost = modifier.metalMultiplier(entry.getKey());
            if (boost <= 1.0f) {
                result.put(entry.getKey(), entry.getValue());
            } else {
                result.put(entry.getKey(), Math.max(1, Math.round(entry.getValue() * boost)));
            }
        }
        return result;
    }

    @Nullable
    public Layer getLayer(int index) {
        if (index < 0 || index >= layers.size()) return null;
        return layers.get(index);
    }

    /**
     * Реагент конкретного слоя. Только в runtime Minecraft: строка резолвится в
     * {@link Fluid} через Forge-регистри. Вернёт {@code null}, если жидкость не зарегистрирована.
     */
    @Nullable
    public Fluid getLayerFluid(int index) {
        Layer layer = getLayer(index);
        if (layer == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(layer.fluidId());
        if (id == null) return null;
        return ForgeRegistries.FLUIDS.getValue(id);
    }

    public boolean isLayerEmpty(int index) {
        Layer layer = getLayer(index);
        return layer == null || layer.isEmpty();
    }

    /**
     * Первый непустой слой начиная с {@code fromIndex} (включительно).
     * {@code -1}, если непустых слоёв нет.
     */
    public int nextNonEmptyLayer(int fromIndex) {
        for (int i = Math.max(0, fromIndex); i < layers.size(); i++) {
            if (!isLayerEmpty(i)) return i;
        }
        return -1;
    }

    /**
     * Делит OU пирога между слоями: ключ = индекс слоя, значение = OU слоя.
     * Сумма значений == {@code totalOus}; пустые слои получают 0.
     */
    public Map<Integer, Integer> distributeLayerOus(int totalOus) {
        Map<Integer, Integer> weights = new LinkedHashMap<>();
        for (Layer layer : layers) {
            weights.put(layer.index(), layer.getTotalWeight());
        }
        return DistributionMath.distribute(totalOus, weights);
    }

    /**
     * Делит OU слоя между металлами: ключ = id металла, значение = гранулы.
     * Сумма значений == {@code layerOus}; металлы с нулевым результатом присутствуют как 0.
     */
    public Map<String, Integer> distributeMetals(int layerOus, int layerIndex) {
        Layer layer = getLayer(layerIndex);
        Map<String, Integer> weights = new LinkedHashMap<>();
        if (layer != null) {
            for (MetalEntry entry : layer.metals()) {
                weights.put(entry.metal(), entry.weight());
            }
        }
        return DistributionMath.distribute(layerOus, weights);
    }

    // ══════════════════ BUILDER ══════════════════

    public static final class Builder {
        private final FractionType fraction;
        private final Layer[] layers = new Layer[MAX_LAYERS];

        private Builder(FractionType fraction) {
            this.fraction = fraction;
        }

        /** Задаёт слой со стандартным реагентом для этой глубины ({@link #STANDARD_FLUIDS}). */
        public Builder layer(int index, String... metals) {
            return layerAdvanced(index, STANDARD_FLUIDS[index], metals);
        }

        /** Задаёт слой с произвольным реагентом (строковый ResourceLocation, напр. "trd:hydrogen_peroxide"). */
        public Builder layerAdvanced(int index, String fluidId, String... metals) {
            if (index < 0 || index >= MAX_LAYERS) {
                throw new IllegalArgumentException("Слой вне диапазона 0.." + (MAX_LAYERS - 1) + ": " + index);
            }
            Map<String, Integer> weights = fraction.getMetalWeights();
            List<MetalEntry> entries = new ArrayList<>(metals.length);
            for (String metal : metals) {
                int weight = weights.getOrDefault(metal, 0);
                if (weight <= 0) {
                    throw new IllegalArgumentException(
                            "Металл '" + metal + "' отсутствует в весах фракции " + fraction.getName());
                }
                entries.add(new MetalEntry(metal, weight));
            }
            layers[index] = new Layer(index, fluidId, List.copyOf(entries));
            return this;
        }

        /** Проверяет, что каждый металл фракции входит ровно в один слой. */
        public Builder validateComplete(Set<String> metals) {
            Map<String, Integer> placed = new LinkedHashMap<>();
            for (Layer layer : layers) {
                if (layer == null) continue;
                for (MetalEntry entry : layer.metals()) {
                    if (placed.containsKey(entry.metal())) {
                        throw new IllegalStateException(
                                "Металл '" + entry.metal() + "' задан в нескольких слоях фракции " + fraction.getName());
                    }
                    placed.put(entry.metal(), layer.index());
                }
            }
            for (String metal : metals) {
                if (!placed.containsKey(metal)) {
                    throw new IllegalStateException(
                            "Металл '" + metal + "' фракции " + fraction.getName() + " не попал ни в один слой");
                }
            }
            return this;
        }

        public FractionLayerMatrix build() {
            for (int i = 0; i < MAX_LAYERS; i++) {
                if (layers[i] == null) {
                    layers[i] = new Layer(i, STANDARD_FLUIDS[i], List.of());
                }
            }
            return new FractionLayerMatrix(fraction, List.of(layers));
        }
    }
}