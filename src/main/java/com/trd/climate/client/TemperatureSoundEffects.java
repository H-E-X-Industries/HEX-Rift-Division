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
 * - Хруст снега и морозный треск при критическом замерзании
 * - Тяжелое прерывистое дыхание при перегреве
 */
@EventBusSubscriber(modid = "trd", value = Dist.CLIENT)
public class TemperatureSoundEffects {

    private static int soundTimer = 0;
    private static final int SOUND_INTERVAL = 45; // Примерно каждые 2.25 секунды

    @SubscribeEvent
    public static void onClientPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        
        if (!player.level().isClientSide() || player != mc.player || mc.level == null) {
            return;
        }

        // В креативе или наблюдателе звуки климатического дискомфорта отключены
        if (player.isCreative() || player.isSpectator()) {
            soundTimer = 0;
            return;
        }

        float temp = ClientTemperatureHandler.getCurrentTemp();
        soundTimer++;
        
        if (soundTimer >= SOUND_INTERVAL) {
            soundTimer = 0;
            playTemperatureSounds(mc, player, temp);
        }
    }

    /**
     * Воспроизводит естественные звуки окружающей среды в зависимости от температуры.
     */
    private static void playTemperatureSounds(Minecraft mc, Player player, float temp) {
        if (mc.level == null) return;

        if (temp < 0.0f) {
            if (temp < -12.0f) {
                // Глухой морозный треск ломающегося льда
                mc.level.playLocalSound(
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GLASS_BREAK,
                    SoundSource.PLAYERS,
                    0.25f,
                    0.45f,
                    false
                );
            } else {
                // Морозный стук / хруст снега от озноба
                mc.level.playLocalSound(
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SNOW_HIT,
                    SoundSource.PLAYERS,
                    0.3f,
                    1.6f,
                    false
                );
            }
        } else if (temp > 35.0f) {
            // Тяжелое горячее дыхание при жаре
            mc.level.playLocalSound(
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_BREATH,
                SoundSource.PLAYERS,
                0.25f,
                0.9f,
                false
            );
        }
    }
}
