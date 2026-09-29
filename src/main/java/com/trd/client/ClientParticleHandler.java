package com.trd.client;

import com.trd.fx.particle.DarkSmokeParticle;
import com.trd.fx.particle.ExplosionSparkParticle;
import com.trd.fx.particle.FireSparkParticle;
import com.trd.fx.particle.ModExplosionParticles;
import com.trd.fx.particle.MushroomSmokeParticle;
import com.trd.fx.particle.WaveSmokeParticle;
import com.trd.main.MainRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientParticleHandler {

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                ModExplosionParticles.EXPLOSION_SPARK.get(),
                ExplosionSparkParticle.Provider::new);

        event.registerSpriteSet(
                ModExplosionParticles.FIRE_SPARK.get(),
                FireSparkParticle.Provider::new);

        event.registerSpriteSet(
                ModExplosionParticles.WAVE_SMOKE.get(),
                WaveSmokeParticle.Provider::new);

        event.registerSpriteSet(
                ModExplosionParticles.DARK_SMOKE.get(),
                DarkSmokeParticle.Provider::new);

        event.registerSpriteSet(
                ModExplosionParticles.MUSHROOM_SMOKE.get(),
                MushroomSmokeParticle.Provider::new);
    }
}
