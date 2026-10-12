package com.trd.climate.effect;

import com.trd.climate.data.PlayerTemperatureData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Применяет игровые эффекты в зависимости от температуры игрока.
 * 
 * Диапазоны температур:
 * < 0°C      - Обледенение (урон + Slowness II)
 * 0-10°C     - Сильное переохлаждение (Slowness I)
 * 10-15°C    - Легкий холод (медленная регенерация голода)
 * 15-25°C    - Зона комфорта (без эффектов)
 * 25-32°C    - Жара (ускоренный расход голода)
 * 32-40°C    - Тепловой удар (Weakness + размытие)
 * > 40°C     - Перегрев (урон + возгорание)
 */
public class TemperatureEffectApplier {

    private static final int EFFECT_DURATION = 25; // Длительность в тиках (чуть больше 1 секунды)

    /**
     * Применяет эффекты температуры к игроку.
     * Вызывается раз в секунду.
     */
    public static void applyTemperatureEffects(Player player, PlayerTemperatureData data) {
        float temp = data.getCurrentTemp();
        PlayerTemperatureData.TemperatureState state = data.getState();

        switch (state) {
            case FREEZING:
                applyFreezingEffects(player, temp);
                break;
            case SEVERE_COLD:
                applySevereColdEffects(player);
                break;
            case COLD:
                applyColdEffects(player);
                break;
            case COMFORTABLE:
                // Нет негативных эффектов
                break;
            case HOT:
                applyHotEffects(player);
                break;
            case HEAT_STROKE:
                applyHeatStrokeEffects(player);
                break;
            case OVERHEATING:
                applyOverheatingEffects(player, temp);
                break;
        }
    }

    /**
     * < 0°C - Обледенение
     * - Урон здоровью (как от powder snow)
     * - Замедление II
     */
    private static void applyFreezingEffects(Player player, float temp) {
        // Замедление II
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_DURATION, 1, false, false));
        
        // Урон каждую секунду (0.5 сердца при -10°C, 1 сердце при -20°C)
        float damage = Math.abs(temp) * 0.05f;
        damage = Math.min(damage, 2.0f); // Максимум 1 сердце за тик
        
        if (damage > 0) {
            player.hurt(player.damageSources().freeze(), damage);
        }
        
        // Увеличиваем счетчик заморозки быстрее для визуального эффекта
        int currentFreeze = player.getTicksFrozen();
        if (currentFreeze < 140) { // 140 = максимальная заморозка (100%)
            player.setTicksFrozen(Math.min(140, currentFreeze + 5)); // Увеличено с 3 до 5
        }
    }

    /**
     * 0-10°C - Сильное переохлаждение
     * - Замедление I
     * - Снижение эффективности регенерации
     */
    private static void applySevereColdEffects(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_DURATION, 0, false, false));
        
        // Уменьшаем счетчик заморозки постепенно (медленнее)
        int currentFreeze = player.getTicksFrozen();
        if (currentFreeze > 0) {
            player.setTicksFrozen(Math.max(0, currentFreeze - 1)); // Уменьшено с 1 до 1 (оставляем для плавности)
        }
    }

    /**
     * 10-15°C - Легкий холод
     * - Ускоренный расход насыщения (медленнее восстанавливается голод)
     */
    private static void applyColdEffects(Player player) {
        // Легкий штраф к насыщению
        player.getFoodData().addExhaustion(0.02f);
        
        // Убираем визуальный эффект заморозки
        if (player.getTicksFrozen() > 0) {
            player.setTicksFrozen(Math.max(0, player.getTicksFrozen() - 2));
        }
    }

    /**
     * 25-32°C - Жара
     * - Ускоренный расход голода/жажды
     */
    private static void applyHotEffects(Player player) {
        // Ускоренное истощение насыщения
        player.getFoodData().addExhaustion(0.05f);
    }

    /**
     * 32-40°C - Тепловой удар
     * - Слабость I
     * - Сильное истощение
     */
    private static void applyHeatStrokeEffects(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_DURATION, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, EFFECT_DURATION, 0, false, false));
        
        // Сильное истощение
        player.getFoodData().addExhaustion(0.1f);
    }

    /**
     * > 40°C - Перегрев/Ожог
     * - Урон здоровью от гипертермии
     * - Шанс загореться
     */
    private static void applyOverheatingEffects(Player player, float temp) {
        // Слабость II
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_DURATION, 1, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, EFFECT_DURATION, 1, false, false));
        
        // Урон от перегрева (0.5 сердца при 45°C, 1 сердце при 50°C+)
        float damage = (temp - 40.0f) * 0.1f;
        damage = Math.min(damage, 2.0f);
        
        if (damage > 0) {
            player.hurt(player.damageSources().onFire(), damage);
        }
        
        // Шанс загореться при экстремальной жаре
        if (temp > 50.0f && player.getRandom().nextFloat() < 0.1f) {
            player.setRemainingFireTicks(60); // 3 секунды горения
        }
        
        // Критическое истощение
        player.getFoodData().addExhaustion(0.15f);
    }
}
