package com.trd.client.sound;

import com.trd.block.entity.industrial.rotation.MotorElectroBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MotorElectroSoundHandler {
    private static final Map<BlockPos, MotorElectroSoundInstance> MOTOR_SOUNDS = new ConcurrentHashMap<>();

    public static void tick(MotorElectroBlockEntity motor) {
        if (motor == null || motor.getLevel() == null || !motor.getLevel().isClientSide) return;

        BlockPos pos = motor.getBlockPos();
        boolean shouldPlay = motor.isRunning();

        MotorElectroSoundInstance current = MOTOR_SOUNDS.get(pos);
        if (current != null && current.isStopped()) {
            MOTOR_SOUNDS.remove(pos);
            current = null;
        }

        if (shouldPlay) {
            if (current == null) {
                MotorElectroSoundInstance sound = new MotorElectroSoundInstance(motor);
                MOTOR_SOUNDS.put(pos, sound);
                Minecraft.getInstance().getSoundManager().play(sound);
            }
        } else {
            if (current != null) {
                current.stopSound();
                MOTOR_SOUNDS.remove(pos);
            }
        }
    }

    public static void stop(BlockPos pos) {
        MotorElectroSoundInstance sound = MOTOR_SOUNDS.remove(pos);
        if (sound != null) {
            sound.stopSound();
        }
    }
}
