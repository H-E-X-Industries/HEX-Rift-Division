package com.trd.api.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data Component для ламп (NeoForge 1.21.1).
 * Зберігає залишковий фізичний ресурс лампи (0.0f - 1.0f) та факт поломки/перегорання нитки розжарення.
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

    public LampComponent withDurability(float newDurability) {
        float clamped = Math.max(0.0f, Math.min(1.0f, newDurability));
        return new LampComponent(clamped, this.broken || clamped <= 0.0f);
    }

    public LampComponent asBroken() {
        return new LampComponent(0.0f, true);
    }

    public boolean isBroken() {
        return this.broken || this.durability <= 0.0f;
    }

    public int getDurabilityPercent() {
        return Math.round(Math.max(0.0f, Math.min(1.0f, this.durability)) * 100.0f);
    }
}
