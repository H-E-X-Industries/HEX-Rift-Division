package com.trd.client.sound;

import com.trd.multiblock.industrial.stanok.CarriageType;
import com.trd.multiblock.industrial.stanok.StanokBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StanokSoundHandler {
    private static final Map<BlockPos, StanokWireSoundInstance> WIRE_SOUNDS = new ConcurrentHashMap<>();

    public static void tick(StanokBlockEntity stanok) {
        if (stanok == null || stanok.getLevel() == null || !stanok.getLevel().isClientSide) return;

        BlockPos pos = stanok.getBlockPos();
        CarriageType carriage = stanok.getCurrentCarriageType();
        boolean shouldPlayWire = carriage == CarriageType.WIRE && stanok.isCrafting();

        StanokWireSoundInstance current = WIRE_SOUNDS.get(pos);
        if (current != null && current.isStopped()) {
            WIRE_SOUNDS.remove(pos);
            current = null;
        }

        if (shouldPlayWire) {
            if (current == null) {
                StanokWireSoundInstance sound = new StanokWireSoundInstance(stanok);
                WIRE_SOUNDS.put(pos, sound);
                Minecraft.getInstance().getSoundManager().play(sound);
            }
        } else {
            if (current != null) {
                current.stopSound();
                WIRE_SOUNDS.remove(pos);
            }
        }
    }

    public static void stop(BlockPos pos) {
        StanokWireSoundInstance sound = WIRE_SOUNDS.remove(pos);
        if (sound != null) {
            sound.stopSound();
        }
    }
}
