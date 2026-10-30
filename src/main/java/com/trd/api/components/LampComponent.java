package com.trd.api.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data component holding runtime physical state and remaining lifespan for electric lamp items.
 *
 * @param durability Normalized remaining lifespan of the lamp filament/tube (0.0f to 1.0f).
 * @param broken     True if the filament or discharge tube has burned out or broken.
 */
public record LampComponent(float durability, boolean broken) {

    public static final Codec<LampComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("durability").forGetter(LampComponent::durability),
                    Codec.BOOL.fieldOf("broken").forGetter(LampComponent::broken)
            ).apply(instance, LampComponent::new)
    );

    public static final StreamCodec<ByteBuf, LampComponent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, LampComponent::durability,
            ByteBufCodecs.BOOL, LampComponent::broken,
            LampComponent::new
    );

    public static final LampComponent DEFAULT = new LampComponent(1.0f, false);
    public static final LampComponent BROKEN = new LampComponent(0.0f, true);

    /**
     * Creates a new component instance with updated durability, automatically marking as broken if depleted.
     */
    public LampComponent withDurability(float newDurability) {
        float clamped = Math.max(0.0f, Math.min(1.0f, newDurability));
        return new LampComponent(clamped, this.broken || clamped <= 0.0f);
    }

    /**
     * Returns a broken instance of this lamp component.
     */
    public LampComponent asBroken() {
        return new LampComponent(0.0f, true);
    }

    /**
     * Checks whether this lamp is broken or has exhausted its service life.
     */
    public boolean isBroken() {
        return this.broken || this.durability <= 0.0f;
    }

    /**
     * Calculates the remaining durability as an integer percentage from 0 to 100.
     */
    public int getDurabilityPercent() {
        return Math.round(Math.max(0.0f, Math.min(1.0f, this.durability)) * 100.0f);
    }
}
