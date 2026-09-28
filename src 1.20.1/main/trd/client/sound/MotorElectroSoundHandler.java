package com.trd.client.sound;

import com.trd.block.entity.industrial.rotation.MotorElectroBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MotorElectroSoundHandler {
    private static final Map<BlockPos, MotorElectroSoundInstance> MOTOR_SOUNDS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Integer> STOP_DELAYS = new ConcurrentHashMap<>();

    public static void tick(MotorElectroBlockEntity motor) {
        if (motor == null || motor.getLevel() == null || !motor.getLevel().isClientSide) return;

        BlockPos pos = motor.getBlockPos();
        boolean shouldPlay = motor.isRunning();

        MotorElectroSoundInstance current = MOTOR_SOUNDS.get(pos);
        if (current != null && current.isStopped()) {
            MOTOR_SOUNDS.remove(pos);
            STOP_DELAYS.remove(pos);
            current = null;
        }

        if (shouldPlay) {
            STOP_DELAYS.remove(pos);
            if (current == null) {
                MotorElectroSoundInstance sound = new MotorElectroSoundInstance(motor);
                MOTOR_SOUNDS.put(pos, sound);
                Minecraft.getInstance().getSoundManager().play(sound);
            }
        } else {
            if (current != null) {
                int delay = STOP_DELAYS.getOrDefault(pos, 0) + 1;
                if (delay > 10) { // 0.5 сек защиты от кратковременных просадок пакетов
                    current.stopSound();
                    MOTOR_SOUNDS.remove(pos);
                    STOP_DELAYS.remove(pos);
                } else {
                    STOP_DELAYS.put(pos, delay);
                }
            }
        }
    }

    public static void stop(BlockPos pos) {
        STOP_DELAYS.remove(pos);
        MotorElectroSoundInstance sound = MOTOR_SOUNDS.remove(pos);
        if (sound != null) {
            sound.stopSound();
        }
    }
}
