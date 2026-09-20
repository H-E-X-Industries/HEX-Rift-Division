package com.trd.client.sound;

import com.trd.multiblock.industrial.boiler.BoilerBlockEntity;
import com.trd.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

public class BoilerSoundInstance extends AbstractTickableSoundInstance {
    private final BoilerBlockEntity boiler;
    private final float basePitch;

    public BoilerSoundInstance(BoilerBlockEntity boiler, float pitch) {
        super(ModSounds.BOILER.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.boiler = boiler;
        this.basePitch = pitch;
        this.looping = true;
        this.delay = 0;
        this.x = boiler.getBlockPos().getX() + 0.5;
        this.y = boiler.getBlockPos().getY() + 1.5;
        this.z = boiler.getBlockPos().getZ() + 0.5;
        this.attenuation = Attenuation.LINEAR;
        this.pitch = pitch;
        updateVolume();
    }

    @Override
    public void tick() {
        if (this.boiler.isRemoved() || this.boiler.getLevel() == null) {
            this.stop();
            return;
        }

        float temp = this.boiler.getTemperature();
        if (temp < 48.0f) {
            this.stop();
            return;
        }

        updateVolume();
    }

    private void updateVolume() {
        float temp = this.boiler.getTemperature();
        Minecraft mc = Minecraft.getInstance();
        double dist = 0.0;
        if (mc.player != null) {
            dist = Math.sqrt(mc.player.distanceToSqr(this.x, this.y, this.z));
        }

        // Увеличенный радиус: до 5 блоков полная громкость, плавное затухание до 22 блоков
        float maxRadius = 22.0f;
        float innerRadius = 5.0f;
        float distFactor = 1.0f - (float) ((dist - innerRadius) / (maxRadius - innerRadius));
        distFactor = Mth.clamp(distFactor, 0.0f, 1.0f);

        float tempProgress = temp >= 100.0f ? 1.0f : ((temp - 50.0f) / 50.0f);
        tempProgress = Mth.clamp(tempProgress, 0.0f, 1.0f);

        this.volume = tempProgress * distFactor;
        this.pitch = this.basePitch;
    }

    public void stopSound() {
        this.stop();
    }
}
