package com.trd.climate.effect;

import com.trd.climate.data.PlayerTemperatureData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Применяет игровые эффекты в зависимости от температуры игрока.
 * 
 * Диапазоны температур:
 * < 0°C      - Обледенение (урон холодом + заморозка powder snow + снежинки + Slowness II)
 * 0-10°C     - Сильное переохлаждение (Slowness I + легкая изморозь)
 * 10-15°C    - Легкий холод (штраф к насыщению)
 * 15-25°C    - Зона комфорта (без эффектов)
 * 25-32°C    - Жара (ускоренный расход голода)
 * 32-40°C    - Тепловой удар (Weakness + истощение)
 * > 40°C     - Перегрев (урон от огня + возгорание)
 */
public class TemperatureEffectApplier {

    private static final int EFFECT_DURATION = 30; // Длительность в тиках (1.5 секунды с запасом)

    /**
     * Применяет периодические эффекты температуры к игроку (вызывается раз в секунду).
     */
    public static void applyTemperatureEffects(Player player, PlayerTemperatureData data) {
        // Игроки в креативе, спектаторе или с иммунитетом не получают дебаффов
        if (player.isCreative() || player.isSpectator() || player.isInvulnerable()) {
            return;
        }

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
     * - Полная заморозка как в рыхлом снеге (голубые сердечки, ледяная виньетка, дрожание)
     * - Урон холодом (freeze damage)
     * - Снежинки замерзания (SNOWFLAKE particles)
     * - Замедление II
     */
    private static void applyFreezingEffects(Player player, float temp) {
        // Накладываем замедление II
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_DURATION, 1, false, false));
        
        // Гарантируем максимальный уровень заморозки для эффектов рыхлого снега
        player.setTicksFrozen(player.getTicksRequiredToFreeze());

        // Расчет урона от холода: 0.5 сердца при -10°C, до 1 сердца при -20°C
        float damage = Math.abs(temp) * 0.05f;
        damage = Math.min(damage, 2.0f);
        
        if (damage > 0) {
            // Наносим ванильный урон от замерзания
            player.hurt(player.damageSources().freeze(), damage);
        }
    }

    /**
     * 0-10°C - Сильное переохлаждение
     * - Замедление I
     */
    private static void applySevereColdEffects(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_DURATION, 0, false, false));
    }

    /**
     * 10-15°C - Легкий холод
     * - Легкий штраф к насыщению
     */
    private static void applyColdEffects(Player player) {
        player.getFoodData().addExhaustion(0.02f);
    }

    /**
     * 25-32°C - Жара
     * - Ускоренный расход сытости
     */
    private static void applyHotEffects(Player player) {
        player.getFoodData().addExhaustion(0.05f);
    }

    /**
     * 32-40°C - Тепловой удар
     * - Слабость I
     * - Сильное истощение
     */
    private static void applyHeatStrokeEffects(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_DURATION, 0, false, false));
        player.getFoodData().addExhaustion(0.1f);
    }

    /**
     * > 40°C - Перегрев/Ожог
     * - Слабость II
     * - Урон здоровью от перегрева (onFire)
     * - Шанс возгорания при экстремальной жаре (> 50°C)
     * - Критическое истощение
     */
    private static void applyOverheatingEffects(Player player, float temp) {
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_DURATION, 1, false, false));
        
        float damage = (temp - 40.0f) * 0.1f;
        damage = Math.min(damage, 2.0f);
        
        if (damage > 0) {
            player.hurt(player.damageSources().onFire(), damage);
        }
        
        // Шанс загореться при экстремальной жаре
        if (temp > 50.0f && player.getRandom().nextFloat() < 0.1f) {
            player.setRemainingFireTicks(60); // 3 секунды горения
        }
        
        player.getFoodData().addExhaustion(0.15f);
    }
}
