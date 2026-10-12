package com.trd.climate.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Клиентские визуальные эффекты: пар изо рта на морозе.
 * Частицы начинаются прозрачными, плавно исчезают и замедляются.
 */
@EventBusSubscriber(modid = "trd", value = Dist.CLIENT)
public class TemperatureParticleEffects {

    private static int breathTimer = 0;

    @SubscribeEvent
    public static void onClientPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        
        if (!player.level().isClientSide() || player != Minecraft.getInstance().player) {
            return;
        }

        float temp = ClientTemperatureHandler.getCurrentTemp();
        
        // Пар изо рта при температуре < 5°C
        if (temp < 5.0f) {
            spawnBreathParticles(player, temp);
        }
    }

    /**
     * Спавнит частицы пара изо рта персонажа.
     * Частицы более прозрачные и реалистичные.
     */
    private static void spawnBreathParticles(Player player, float temp) {
        breathTimer++;
        
        // Интервал спавна: чем холоднее, тем чаще
        int interval = Math.max(15, 25 + (int)(temp * 2));
        
        if (breathTimer < interval) {
            return;
        }
        
        breathTimer = 0;
        
        // Позиция рта (голова игрока + смещение вперед)
        Vec3 lookVec = player.getLookAngle();
        double x = player.getX() + lookVec.x * 0.35;
        double y = player.getEyeY() - 0.15;
        double z = player.getZ() + lookVec.z * 0.35;
        
        // Направление взгляда для начальной скорости
        double vx = lookVec.x * 0.03;
        double vy = 0.02;
        double vz = lookVec.z * 0.03;
        
        // Спавним прозрачные частицы пара
        for (int i = 0; i < 2; i++) {
            double offsetX = (player.getRandom().nextDouble() - 0.5) * 0.08;
            double offsetY = (player.getRandom().nextDouble() - 0.5) * 0.08;
            double offsetZ = (player.getRandom().nextDouble() - 0.5) * 0.08;
            
            // Используем белые частицы с низкой скоростью для эффекта пара
            player.level().addParticle(
                ParticleTypes.WHITE_ASH, // Более прозрачные частицы
                x + offsetX,
                y + offsetY,
                z + offsetZ,
                vx, vy, vz
            );
        }
        
        // Добавляем несколько облачков для объема (редко)
        if (player.getRandom().nextFloat() < 0.3f) {
            player.level().addParticle(
                ParticleTypes.CLOUD,
                x, y, z,
                vx * 0.5, vy * 0.5, vz * 0.5 // Медленнее для естественности
            );
        }
    }
}
