package com.trd.fx.particle;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.trd.main.MainRegistry;

public class ModExplosionParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MainRegistry.MOD_ID);

    public static final RegistryObject<ParticleType<?>> EXPLOSION_SPARK =
            PARTICLE_TYPES.register("explosion_spark", () -> new SimpleParticleType(true));

    public static final RegistryObject<ParticleType<?>> FIRE_SPARK =
            PARTICLE_TYPES.register("fire_spark", () -> new SimpleParticleType(true));

    public static final RegistryObject<ParticleType<?>> WAVE_SMOKE =
            PARTICLE_TYPES.register("wave_smoke", () -> new SimpleParticleType(true));

    public static final RegistryObject<ParticleType<?>> DARK_SMOKE =
            PARTICLE_TYPES.register("dark_smoke", () -> new SimpleParticleType(true));

    public static final RegistryObject<ParticleType<?>> MUSHROOM_SMOKE =
            PARTICLE_TYPES.register("mushroom_smoke", () -> new SimpleParticleType(true));
}
