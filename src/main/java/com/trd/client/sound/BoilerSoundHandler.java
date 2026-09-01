package com.trd.client.sound;

import com.trd.multiblock.industrial.boiler.BoilerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BoilerSoundHandler {
    private static class SoundPair {
        final BoilerSoundInstance sound1;
        final BoilerSoundInstance sound2;

        SoundPair(BoilerSoundInstance s1, BoilerSoundInstance s2) {
            this.sound1 = s1;
            this.sound2 = s2;
        }

        boolean isStopped() {
            return (sound1.isStopped() || !Minecraft.getInstance().getSoundManager().isActive(sound1)) &&
                   (sound2.isStopped() || !Minecraft.getInstance().getSoundManager().isActive(sound2));
        }

        void stop() {
            sound1.stopSound();
            sound2.stopSound();
        }
    }

    private static final Map<BlockPos, SoundPair> PLAYING_SOUNDS = new ConcurrentHashMap<>();

    public static void tick(BoilerBlockEntity boiler) {
        if (boiler == null || boiler.getLevel() == null || !boiler.getLevel().isClientSide) return;

        BlockPos pos = boiler.getBlockPos();
        float temp = boiler.getTemperature();

        SoundPair current = PLAYING_SOUNDS.get(pos);

        if (current != null && current.isStopped()) {
            PLAYING_SOUNDS.remove(pos);
            current = null;
        }

        if (temp >= 50.0f) {
            if (current == null) {
                // Двойной слой (две звуковые дорожки параллельно) для плотного, громкого звука + пониженный бас (0.70 и 0.65)
                BoilerSoundInstance sound1 = new BoilerSoundInstance(boiler, 0.70f);
                BoilerSoundInstance sound2 = new BoilerSoundInstance(boiler, 0.65f);
                SoundPair pair = new SoundPair(sound1, sound2);
                PLAYING_SOUNDS.put(pos, pair);
                Minecraft.getInstance().getSoundManager().play(sound1);
                Minecraft.getInstance().getSoundManager().play(sound2);
            }
        } else {
            if (current != null) {
                current.stop();
                PLAYING_SOUNDS.remove(pos);
            }
        }
    }

    public static void stop(BlockPos pos) {
        SoundPair sound = PLAYING_SOUNDS.remove(pos);
        if (sound != null) {
            sound.stop();
        }
    }
}
