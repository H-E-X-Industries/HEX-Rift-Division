package com.trd.client.sound;

import com.trd.multiblock.industrial.stanok.CarriageType;
import com.trd.multiblock.industrial.stanok.StanokBlockEntity;
import com.trd.sound.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

public class StanokWireSoundInstance extends AbstractTickableSoundInstance {
    private final StanokBlockEntity stanok;

    public StanokWireSoundInstance(StanokBlockEntity stanok) {
        super(ModSounds.STANOK_WIRE.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.stanok = stanok;
        this.looping = true;
        this.delay = 0;
        this.x = stanok.getBlockPos().getX() + 0.5;
        this.y = stanok.getBlockPos().getY() + 1.0;
        this.z = stanok.getBlockPos().getZ() + 0.5;
        this.attenuation = Attenuation.LINEAR;
        this.volume = 1.0f;
        this.pitch = 1.0f;
    }

    @Override
    public void tick() {
        if (this.stanok.isRemoved() || this.stanok.getLevel() == null) {
            this.stop();
            return;
        }

        if (this.stanok.getCurrentCarriageType() != CarriageType.WIRE || !this.stanok.isCrafting()) {
            this.stop();
            return;
        }
    }

    public void stopSound() {
        this.stop();
    }
}
