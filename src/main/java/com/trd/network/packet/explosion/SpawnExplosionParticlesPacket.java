package com.trd.network.packet.explosion;

import com.trd.fx.particle.ExplosionParticleUtils;
import com.trd.fx.particle.ModExplosionParticles;
import com.trd.main.MainRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C: запуск одного из эффектов взрыва на клиенте (искры / огненные искры / ударная волна / грибовидное облако). */
public record SpawnExplosionParticlesPacket(int effectType, double x, double y, double z)
        implements CustomPacketPayload {

    public static final Type<SpawnExplosionParticlesPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "spawn_explosion_particles"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpawnExplosionParticlesPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SpawnExplosionParticlesPacket::effectType,
                    ByteBufCodecs.DOUBLE, SpawnExplosionParticlesPacket::x,
                    ByteBufCodecs.DOUBLE, SpawnExplosionParticlesPacket::y,
                    ByteBufCodecs.DOUBLE, SpawnExplosionParticlesPacket::z,
                    SpawnExplosionParticlesPacket::new);

    public static void send(net.minecraft.server.level.ServerLevel level, int effectType, double x, double y, double z) {
        PacketDistributor.sendToPlayersInDimension(level, new SpawnExplosionParticlesPacket(effectType, x, y, z));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (!(level instanceof ClientLevel clientLevel)) return;

            switch (effectType) {
                case ExplosionParticleUtils.EFFECT_SPARKS -> spawnSparks(clientLevel, x, y, z);
                case ExplosionParticleUtils.EFFECT_FIRE_SPARKS -> spawnFireSparks(clientLevel, x, y, z);
                case ExplosionParticleUtils.EFFECT_SHOCKWAVE -> spawnShockwave(clientLevel, x, y, z);
                case ExplosionParticleUtils.EFFECT_MUSHROOM_CLOUD -> spawnMushroomCloud(clientLevel, x, y, z);
            }
        });
    }

    private static void spawnSparks(ClientLevel clientLevel, double x, double y, double z) {
        var random = net.minecraft.util.RandomSource.create();

        for (int i = 0; i < 400; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;
            double phi = random.nextDouble() * Math.PI;

            double dirX = Math.sin(phi) * Math.cos(theta);
            double dirY = Math.cos(phi);
            double dirZ = Math.sin(phi) * Math.sin(theta);

            double speed = 0.8 + random.nextDouble() * 0.5;

            clientLevel.addAlwaysVisibleParticle(
                    (SimpleParticleType) ModExplosionParticles.EXPLOSION_SPARK.get(),
                    true,
                    x, y, z,
                    dirX * speed, dirY * speed, dirZ * speed
            );
        }
    }

    private static void spawnFireSparks(ClientLevel clientLevel, double x, double y, double z) {
        var random = net.minecraft.util.RandomSource.create();

        for (int i = 0; i < 400; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;
            double phi = random.nextDouble() * Math.PI;

            double dirX = Math.sin(phi) * Math.cos(theta);
            double dirY = Math.cos(phi);
            double dirZ = Math.sin(phi) * Math.sin(theta);

            double speed = 0.8 + random.nextDouble() * 0.5;

            clientLevel.addAlwaysVisibleParticle(
                    (SimpleParticleType) ModExplosionParticles.FIRE_SPARK.get(),
                    true,
                    x, y, z,
                    dirX * speed, dirY * speed, dirZ * speed
            );
        }
    }

    private static void spawnShockwave(ClientLevel clientLevel, double x, double y, double z) {
        var random = net.minecraft.util.RandomSource.create();

        int particleCount = 500;

        for (int i = 0; i < particleCount; i++) {
            double angle = (i / (double) particleCount) * 2 * Math.PI;

            double startRadius = 9.0 + random.nextDouble() * 2.0;

            double offsetX = Math.cos(angle) * startRadius;
            double offsetZ = Math.sin(angle) * startRadius;
            double offsetY = (random.nextDouble() - 0.5) * 2.0;

            double expansionSpeed = 0.6 + random.nextDouble() * 0.2;

            double xSpeed = Math.cos(angle) * expansionSpeed;
            double zSpeed = Math.sin(angle) * expansionSpeed;
            double ySpeed = -0.05 + random.nextDouble() * 0.1;

            clientLevel.addAlwaysVisibleParticle(
                    (SimpleParticleType) ModExplosionParticles.WAVE_SMOKE.get(),
                    true,
                    x + offsetX, y + offsetY, z + offsetZ,
                    xSpeed, ySpeed, zSpeed
            );
        }
    }

    private static void spawnMushroomCloud(ClientLevel clientLevel, double x, double y, double z) {
        var random = net.minecraft.util.RandomSource.create();

        for (int i = 0; i < 750; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;
            double phi = random.nextDouble() * Math.PI;
            double radius = random.nextDouble() * 4.0;

            double offsetX = radius * Math.sin(phi) * Math.cos(theta);
            double offsetY = radius * Math.sin(phi) * Math.sin(theta);
            double offsetZ = radius * Math.cos(phi);

            double expansionSpeed = 0.5 + random.nextDouble() * 0.1;
            double xSpeed = (offsetX / Math.max(radius, 0.1)) * expansionSpeed;
            double ySpeed = (offsetY / Math.max(radius, 0.1)) * expansionSpeed;
            double zSpeed = (offsetZ / Math.max(radius, 0.1)) * expansionSpeed;

            clientLevel.addAlwaysVisibleParticle(
                    (SimpleParticleType) ModExplosionParticles.DARK_SMOKE.get(),
                    true,
                    x + offsetX, y + offsetY, z + offsetZ,
                    xSpeed, ySpeed, zSpeed
            );
        }

        for (int i = 0; i < 350; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = random.nextDouble() * 4.0;
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            double capY = y + 1 + random.nextDouble() * 2;

            double xSpeed = Math.cos(angle) * 0.5;
            double ySpeed = -0.01 + random.nextDouble() * 0.02;
            double zSpeed = Math.sin(angle) * 0.5;

            clientLevel.addAlwaysVisibleParticle(
                    (SimpleParticleType) ModExplosionParticles.DARK_SMOKE.get(),
                    true,
                    x + offsetX, capY, z + offsetZ,
                    xSpeed, ySpeed, zSpeed
            );
        }
    }
}
