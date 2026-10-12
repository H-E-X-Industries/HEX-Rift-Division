package com.trd.climate.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Звуковые эффекты температуры:
 * - Дрожащие зубы при холоде
 * - Тяжелое дыхание при жаре
 * - Треск льда при критическом замерзании
 */
@EventBusSubscriber(modid = "trd", value = Dist.CLIENT)
public class TemperatureSoundEffects {

    private static int soundTimer = 0;
    private static final int SOUND_INTERVAL = 40; // Каждые 2 секунды

    @SubscribeEvent
    public static void onClientPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        
        if (!player.level().isClientSide() || player != Minecraft.getInstance().player) {
            return;
        }

        float temp = ClientTemperatureHandler.getCurrentTemp();
        
        // Увеличиваем таймер
        soundTimer++;
        
        // Воспроизводим звуки в зависимости от температуры
        if (soundTimer >= SOUND_INTERVAL) {
            playTemperatureSounds(player, temp);
            soundTimer = 0;
        }
    }

    /**
     * Воспроизводит звуки в зависимости от температуры.
     */
    private static void playTemperatureSounds(Player player, float temp) {
        Minecraft mc = Minecraft.getInstance();
        
        if (temp < 0.0f) {
            // Критическое замерзание: треск льда
            if (temp < -10.0f) {
                mc.level.playLocalSound(
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GLASS_BREAK,
                    SoundSource.PLAYERS,
                    0.3f,
                    0.5f,
                    false
                );
            }
            // Обычное замерзание: дрожащие зубы (звук камина)
            else {
                mc.level.playLocalSound(
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRE_AMBIENT,
                    SoundSource.PLAYERS,
                    0.2f,
                    1.5f,
                    false
                );
            }
        } else if (temp > 35.0f) {
            // Тяжелое дыхание при жаре (звук игрока под водой)
            mc.level.playLocalSound(
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_BREATH,
                SoundSource.PLAYERS,
                0.3f,
                1.0f,
                false
            );
        }
    }
}
