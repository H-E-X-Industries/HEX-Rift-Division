package com.trd.fx.particle;

import com.trd.main.MainRegistry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModExplosionParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, MainRegistry.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EXPLOSION_SPARK =
            PARTICLE_TYPES.register("explosion_spark", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIRE_SPARK =
            PARTICLE_TYPES.register("fire_spark", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WAVE_SMOKE =
            PARTICLE_TYPES.register("wave_smoke", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DARK_SMOKE =
            PARTICLE_TYPES.register("dark_smoke", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUSHROOM_SMOKE =
            PARTICLE_TYPES.register("mushroom_smoke", () -> new SimpleParticleType(true));

    /**
     * Вспышка выстрела: короткая, всегда светящаяся, без физики.
     * <p>
     * Имя типа обязано совпадать с файлом {@code particles/shot.json}, иначе
     * движ частиц не найдёт список спрайтов и в логе будет «missing particle
     * sprites».
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHOT =
            PARTICLE_TYPES.register("shot", () -> new SimpleParticleType(true));
}
