package com.trd.client.sound;

import com.trd.block.entity.industrial.rotation.MotorElectroBlockEntity;
import com.trd.sound.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

public class MotorElectroSoundInstance extends AbstractTickableSoundInstance {
    private final MotorElectroBlockEntity motor;

    public MotorElectroSoundInstance(MotorElectroBlockEntity motor) {
        super(ModSounds.MOTOR_ELECTRO_LOOP.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.motor = motor;
        this.looping = true;
        this.delay = 0;
        this.x = motor.getBlockPos().getX() + 0.5;
        this.y = motor.getBlockPos().getY() + 0.5;
        this.z = motor.getBlockPos().getZ() + 0.5;
        this.attenuation = Attenuation.LINEAR;
        this.volume = 1.0f;
        this.pitch = 1.0f;
    }

    @Override
    public void tick() {
        if (this.motor.isRemoved() || this.motor.getLevel() == null) {
            this.stop();
            return;
        }

        if (!this.motor.isRunning()) {
            this.stop();
            return;
        }
    }

    public void stopSound() {
        this.stop();
    }
}
