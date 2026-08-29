package com.trd.api.vein;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributionMathTest {

    private static Map<String, Integer> weights(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], Integer.parseInt(pairs[i + 1].toString()));
        }
        return map;
    }

    private static int sum(Map<?, Integer> values) {
        return values.values().stream().mapToInt(Integer::intValue).sum();
    }

    @Test
    void distributesWholeNumbers() {
        assertEquals(Map.of("a", 50, "b", 30, "c", 20),
                DistributionMath.distribute(100, weights("a", 50, "b", 30, "c", 20)));
    }

    @Test
    void distributesMaxRemainder() {
        Map<String, Integer> result = DistributionMath.distribute(7, weights("a", 3, "b", 3, "c", 4));
        assertEquals(Map.of("a", 2, "b", 2, "c", 3), result);
        assertEquals(7, sum(result));
    }

    @Test
    void sumAlwaysEqualsTotal() {
        for (int total = 0; total <= 40; total++) {
            for (String[] caseWeights : new String[][]{
                    {"a", "3", "b", "3", "c", "4"},
                    {"a", "50", "b", "30", "c", "20", "d", "7", "e", "1"},
                    {"a", "90", "b", "10"},
                    {"a", "100"},
            }) {
                Map<String, Integer> result = DistributionMath.distribute(total, weights((Object[]) caseWeights));
                assertEquals(total, sum(result), "total=" + total);
                assertTrue(result.values().stream().allMatch(v -> v >= 0), "no negatives, total=" + total);
            }
        }
    }

    @Test
    void zeroTotalYieldsAllZeros() {
        Map<String, Integer> result = DistributionMath.distribute(0, weights("a", 50, "b", 30));
        assertEquals(Map.of("a", 0, "b", 0), result);
    }

    @Test
    void negativeTotalYieldsAllZeros() {
        Map<String, Integer> result = DistributionMath.distribute(-5, weights("a", 50));
        assertEquals(Map.of("a", 0), result);
    }

    @Test
    void zeroWeightsYieldAllZerosEvenWithTotal() {
        Map<String, Integer> result = DistributionMath.distribute(10, weights("a", 0, "b", 0));
        assertEquals(Map.of("a", 0, "b", 0), result);
    }

    @Test
    void emptyWeightsYieldEmptyResult() {
        assertTrue(DistributionMath.distribute(10, Map.of()).isEmpty());
        assertTrue(DistributionMath.distribute(10, null).isEmpty());
    }

    @Test
    void zeroWeightKeyAlwaysZero() {
        Map<String, Integer> result = DistributionMath.distribute(10, weights("a", 90, "b", 0, "c", 10));
        assertEquals(0, result.get("b"));
        assertEquals(10, sum(result));
    }

    @Test
    void tieBreaksByInputOrder() {
        Map<String, Integer> result = DistributionMath.distribute(1, weights("first", 1, "second", 1));
        assertEquals(Map.of("first", 1, "second", 0), result);
    }

    // ═══════════ Матрицы фракций (чисты от Minecraft до ленивого резолва жидкости) ═══════════

    @Test
    void heavyMatrixOuDistribution() {
        Map<Integer, Integer> result = FractionLayerMatrix.HEAVY.distributeLayerOus(70);
        Map<Integer, Integer> expected = Map.of(0, 39, 1, 29, 2, 2, 3, 0);
        assertEquals(expected, result);
        assertEquals(70, sum(result));
    }

    @Test
    void heavyMatrixMetalDistribution() {
        Map<String, Integer> result = FractionLayerMatrix.HEAVY.distributeMetals(39, 0);
        assertEquals(Map.of("iron", 28, "tin", 11), result);
        assertEquals(39, sum(result));
    }

    @Test
    void lightMatrixOuDistribution() {
        Map<Integer, Integer> result = FractionLayerMatrix.LIGHT.distributeLayerOus(50);
        assertEquals(Map.of(0, 25, 1, 13, 2, 12, 3, 0), result);
        assertEquals(50, sum(result));
    }

    @Test
    void lightMetalLayersMatchUserMatrix() {
        // Слои (по правкам пользователя): L0 медь, L1 алюминий, L2 бериллий+титан, L3 пусто
        FractionLayerMatrix matrix = FractionLayerMatrix.LIGHT;
        assertEquals("copper", matrix.getLayer(0).metals().get(0).metal());
        assertEquals("aluminum", matrix.getLayer(1).metals().get(0).metal());
        assertEquals(2, matrix.getLayer(2).metals().size());
        assertEquals("beryllium", matrix.getLayer(2).metals().get(0).metal());
        assertEquals("titanium", matrix.getLayer(2).metals().get(1).metal());
        assertTrue(matrix.isLayerEmpty(3));
    }

    @Test
    void singleMetalFractionsSitInLayerOne() {
        FractionLayerMatrix noble = FractionLayerMatrix.NOBLE;
        FractionLayerMatrix rare = FractionLayerMatrix.RARE_EARTH;
        assertEquals("gold", noble.getLayer(1).metals().get(0).metal());
        assertEquals("neodymium", rare.getLayer(1).metals().get(0).metal());
        assertTrue(noble.isLayerEmpty(0));
        assertTrue(noble.isLayerEmpty(2));
        assertTrue(noble.isLayerEmpty(3));
        assertTrue(rare.isLayerEmpty(0));
        assertTrue(rare.isLayerEmpty(2));
        assertTrue(rare.isLayerEmpty(3));
    }

    @Test
    void everyFractionMetalLandInExactlyOneLayer() {
        for (FractionType fraction : FractionType.values()) {
            FractionLayerMatrix matrix = FractionLayerMatrix.forFraction(fraction);
            // Каждый металл фракции присутствует ровно в одном непустом слое
            Map<String, Integer> layersPerMetal = new LinkedHashMap<>();
            for (FractionLayerMatrix.Layer layer : matrix.getLayers()) {
                for (FractionLayerMatrix.MetalEntry entry : layer.metals()) {
                    layersPerMetal.merge(entry.metal(), 1, Integer::sum);
                }
            }
            assertEquals(fraction.getMetalWeights().keySet(), layersPerMetal.keySet());
            assertTrue(layersPerMetal.values().stream().allMatch(count -> count == 1));
        }
    }

    // ═══════════ Модификаторы жилы (Этап 1: температура + биом-теги) ═══════════

    @Test
    void modifierMultipliersApply() {
        VeinModifier modifier = VeinModifier.of(
                Map.of(FractionType.HEAVY_METAL, 1.6f, FractionType.LIGHT_METAL, 0.7f),
                Map.of("tungsten", 2.0f, "lead", 1.5f));

        assertEquals(1.6f, modifier.fractionMultiplier(FractionType.HEAVY_METAL));
        assertEquals(0.7f, modifier.fractionMultiplier(FractionType.LIGHT_METAL));
        assertEquals(1.0f, modifier.fractionMultiplier(FractionType.NOBLE_METAL));
        assertEquals(2.0f, modifier.metalMultiplier("tungsten"));
        assertEquals(1.0f, modifier.metalMultiplier("iron"));
        assertFalse(modifier.isEmpty());

        VeinModifier none = VeinModifier.NONE;
        assertEquals(1.0f, none.fractionMultiplier(FractionType.HEAVY_METAL));
        assertEquals(1.0f, none.metalMultiplier("gold"));
        assertTrue(none.isEmpty());
    }

    @Test
    void modifierCombineTakesMaxBoost() {
        VeinModifier a = VeinModifier.of(Map.of(FractionType.HEAVY_METAL, 1.6f), Map.of("tungsten", 2.0f));
        VeinModifier b = VeinModifier.of(Map.of(FractionType.HEAVY_METAL, 1.2f), Map.of("aluminum", 2.0f));

        VeinModifier combined = a.combine(b);
        assertEquals(1.6f, combined.fractionMultiplier(FractionType.HEAVY_METAL));
        assertEquals(2.0f, combined.metalMultiplier("tungsten"));
        assertEquals(2.0f, combined.metalMultiplier("aluminum"));
    }

    @Test
    void bakeWeightsAppliesMetalBoostsOnly() {
        // Эталон HEAVY: tungsten=3, lead=17, iron=40, tin=15, zinc=25
        VeinModifier mountains = VeinModifier.of(Map.of(), Map.of("tungsten", 2.0f, "lead", 1.5f));
        Map<String, Integer> baked = FractionLayerMatrix.HEAVY.bakeWeights(mountains);

        assertEquals(6, baked.get("tungsten"));   // 3 * 2.0
        assertEquals(26, baked.get("lead"));      // 17 * 1.5 = 25.5 → round → 26
        assertEquals(40, baked.get("iron"));      // без буста — эталон без изменений
        assertEquals(15, baked.get("tin"));
        assertEquals(25, baked.get("zinc"));
    }

    @Test
    void bakeWeightsWithoutBoostsEqualsBaseline() {
        assertEquals(FractionLayerMatrix.HEAVY.getAllMetalWeights(),
                FractionLayerMatrix.HEAVY.bakeWeights(VeinModifier.NONE));
        assertEquals(FractionLayerMatrix.LIGHT.getAllMetalWeights(),
                FractionLayerMatrix.LIGHT.bakeWeights(VeinModifier.NONE));
    }

    @Test
    void generationWithColdModifierSumsToHundred() {
        VeinModifier cold = VeinModifier.of(Map.of(FractionType.HEAVY_METAL, 1.6f), Map.of());
        VeinComposition composition = VeinCompositionGenerator.generate(-40, net.minecraft.util.RandomSource.create(42L), cold);
        Map<FractionType, Integer> fractions = composition.getFractions();
        int total = fractions.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(100, total);
        assertTrue(fractions.values().stream().allMatch(percent -> percent >= 0));
    }
}