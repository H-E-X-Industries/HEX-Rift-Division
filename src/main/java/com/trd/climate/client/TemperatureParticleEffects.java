package com.trd.climate.client;

import com.trd.fx.particle.ModExplosionParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Клиентские визуальные эффекты температуры:
 * Реалистичный пар изо рта на холоде.
 * Пар выходит строго по направлению взгляда головы/камеры,
 * летит дальше (+30%), имеет увеличенный разлет (+30%) и объемный размер,
 * плавно замедляясь и растворяясь в воздухе.
 */
@EventBusSubscriber(modid = "trd", value = Dist.CLIENT)
public class TemperatureParticleEffects {

    // Таймер дыхательного цикла (пауза между выдохами)
    private static int breathCycleTimer = 0;
    // Счетчик тиков активного выдоха струйки пара
    private static int exhalingTicks = 0;

    @SubscribeEvent
    public static void onClientPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Minecraft mc = Minecraft.getInstance();

        // Проверяем клиентскую сторону и локального игрока
        if (!player.level().isClientSide() || player != mc.player) {
            return;
        }

        // Под водой или в режиме наблюдателя пар не спавним
        if (player.isUnderWater() || player.isSpectator()) {
            breathCycleTimer = 0;
            exhalingTicks = 0;
            return;
        }

        float temp = ClientTemperatureHandler.getCurrentTemp();

        // Пар изо рта появляется при температуре ниже 5°C
        if (temp < 5.0f) {
            tickBreathCycle(player, temp);
        } else {
            breathCycleTimer = 0;
            exhalingTicks = 0;
        }
    }

    /**
     * Симуляция естественного дыхательного цикла человека:
     * пауза (вдох) -> серия мягких частиц (выдох струйки пара).
     */
    private static void tickBreathCycle(Player player, float temp) {
        // Если сейчас идет фаза выдоха
        if (exhalingTicks > 0) {
            exhalingTicks--;
            spawnMouthBreathParticle(player);
            return;
        }

        breathCycleTimer++;

        // Интервал между выдохами (при сильном морозе дыхание чаще)
        int breathInterval = Math.max(30, 55 + (int) (temp * 1.5f));

        if (breathCycleTimer >= breathInterval) {
            breathCycleTimer = 0;
            // Выдох длится 5-7 тиков, создавая выразительную струю пара
            exhalingTicks = 5 + player.getRandom().nextInt(3);
            spawnMouthBreathParticle(player);
        }
    }

    /**
     * Создает отдельную частицу пара прямо из точки рта игрока
     * строго по направлению камеры/головы с увеличенной дальностью и разлетом.
     */
    private static void spawnMouthBreathParticle(Player player) {
        // Вектор взгляда камеры с учетом поворота головы
        Vec3 look = player.getViewVector(1.0F);
        Vec3 movement = player.getDeltaMovement();

        // Точка рта персонажа:
        // Уровень глаз минус ~0.13 блока вниз, и вынос вперед строго по взгляду камеры look
        double forwardOffset = 0.25;
        double mouthX = player.getX() + look.x * forwardOffset;
        double mouthY = player.getEyeY() - 0.13 + look.y * 0.15;
        double mouthZ = player.getZ() + look.z * forwardOffset;

        // Разлет струи пара у рта увеличен (+30%)
        double posSpread = 0.0075;
        double offsetX = (player.getRandom().nextDouble() - 0.5) * posSpread;
        double offsetY = (player.getRandom().nextDouble() - 0.5) * posSpread;
        double offsetZ = (player.getRandom().nextDouble() - 0.5) * posSpread;

        // Скорость полета пара увеличена на 30% (летит дальше)
        double speed = 0.052 + player.getRandom().nextDouble() * 0.018;

        // Разлет вектора скорости увеличен (+30% сильнее конус рассеивания)
        double velSpread = 0.008;
        double vx = look.x * speed + movement.x * 0.35 + (player.getRandom().nextDouble() - 0.5) * velSpread;
        double vy = look.y * speed + movement.y * 0.35 + (player.getRandom().nextDouble() - 0.5) * velSpread;
        double vz = look.z * speed + movement.z * 0.35 + (player.getRandom().nextDouble() - 0.5) * velSpread;

        // Спавним реалистичную объемную частицу пара
        player.level().addParticle(
            ModExplosionParticles.BREATH.get(),
            mouthX + offsetX,
            mouthY + offsetY,
            mouthZ + offsetZ,
            vx, vy, vz
        );
    }
}
