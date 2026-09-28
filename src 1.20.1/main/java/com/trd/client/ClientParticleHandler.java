package com.trd.client;

import com.trd.fx.particle.DarkSmokeParticle;
import com.trd.fx.particle.ExplosionSparkParticle;
import com.trd.fx.particle.FireSparkParticle;
import com.trd.fx.particle.ModExplosionParticles;
import com.trd.fx.particle.MushroomSmokeParticle;
import com.trd.fx.particle.WaveSmokeParticle;
import com.trd.main.MainRegistry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientParticleHandler {

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                (SimpleParticleType) ModExplosionParticles.EXPLOSION_SPARK.get(),
                ExplosionSparkParticle.Provider::new);

        event.registerSpriteSet(
                (SimpleParticleType) ModExplosionParticles.FIRE_SPARK.get(),
                FireSparkParticle.Provider::new);

        event.registerSpriteSet(
                (SimpleParticleType) ModExplosionParticles.WAVE_SMOKE.get(),
                WaveSmokeParticle.Provider::new);

        event.registerSpriteSet(
                (SimpleParticleType) ModExplosionParticles.DARK_SMOKE.get(),
                DarkSmokeParticle.Provider::new);

        event.registerSpriteSet(
                (SimpleParticleType) ModExplosionParticles.MUSHROOM_SMOKE.get(),
                MushroomSmokeParticle.Provider::new);
    }
}
