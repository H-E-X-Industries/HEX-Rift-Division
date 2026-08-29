package com.trd.api.vein;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Целочисленное распределение «целых частей» (метод наибольших остатков).
 *
 * <p>Гарантии:
 * <ul>
 *   <li>все значения — целые, неотрицательные;</li>
 *   <li>сумма результатов == {@code total}, если {@code total > 0} и есть хотя бы
 *       один положительный вес;</li>
 *   <li>нулевые веса всегда получают 0;</li>
 *   <li>детерминизм: при равных дробных остатках приоритет у ключа, идущего раньше по
 *       входному порядку.</li>
 * </ul>
 */
public final class DistributionMath {

    private DistributionMath() {
    }

    public static <K> Map<K, Integer> distribute(int total, Map<K, Integer> weights) {
        Map<K, Integer> result = new LinkedHashMap<>();
        if (weights == null || weights.isEmpty()) return result;

        long sumWeights = 0;
        for (Integer w : weights.values()) sumWeights += Math.max(0, w);

        if (total <= 0 || sumWeights == 0) {
            for (K key : weights.keySet()) result.put(key, 0);
            return result;
        }

        List<K> keys = new ArrayList<>(weights.keySet());
        Map<K, Integer> floorShares = new LinkedHashMap<>();
        Map<K, Double> remainders = new LinkedHashMap<>();
        long allocated = 0;

        for (K key : keys) {
            int weight = Math.max(0, weights.getOrDefault(key, 0));
            double exact = (double) total * weight / sumWeights;
            int floor = (int) Math.floor(exact);
            floorShares.put(key, floor);
            remainders.put(key, exact - floor);
            allocated += floor;
        }

        // Остаток (всегда 0..keys.size()-1) раздаём по наибольшим дробным частям.
        long rest = total - allocated;
        List<K> order = new ArrayList<>(keys);
        order.sort(Comparator.<K>comparingDouble(remainders::get).reversed()
                .thenComparingInt(keys::indexOf));

        long handed = 0;
        for (K key : order) {
            if (handed >= rest) break;
            result.put(key, floorShares.get(key) + 1);
            handed++;
        }

        for (K key : keys) {
            result.putIfAbsent(key, floorShares.get(key));
        }
        return result;
    }
}