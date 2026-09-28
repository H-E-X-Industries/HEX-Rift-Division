package com.trd.network.packet.explosion;

import com.trd.fx.particle.ExplosionParticleUtils;
import com.trd.network.ModPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;


import java.util.function.Supplier;

public class SpawnExplosionParticlesPacket {

    private final int effectType;
    private final double x;
    private final double y;
    private final double z;

    public SpawnExplosionParticlesPacket(int effectType, double x, double y, double z) {
        this.effectType = effectType;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static void send(ServerLevel level, int effectType, double x, double y, double z) {
        ModPacketHandler.INSTANCE.send(
                PacketDistributor.DIMENSION.with(() -> level.dimension()),
                new SpawnExplosionParticlesPacket(effectType, x, y, z));
    }

    public static void encode(SpawnExplosionParticlesPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.effectType);
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.y);
        buf.writeDouble(packet.z);
    }

    public static SpawnExplosionParticlesPacket decode(FriendlyByteBuf buf) {
        return new SpawnExplosionParticlesPacket(
                buf.readInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble());
    }

    public static void handle(SpawnExplosionParticlesPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var level = ModPacketHandler.getClientLevel();
            if (!(level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel)) return;

            switch (packet.effectType) {
                case ExplosionParticleUtils.EFFECT_SPARKS -> spawnSparks(clientLevel, packet.x, packet.y, packet.z);
                case ExplosionParticleUtils.EFFECT_FIRE_SPARKS -> spawnFireSparks(clientLevel, packet.x, packet.y, packet.z);
                case ExplosionParticleUtils.EFFECT_SHOCKWAVE -> spawnShockwave(clientLevel, packet.x, packet.y, packet.z);
                case ExplosionParticleUtils.EFFECT_MUSHROOM_CLOUD -> spawnMushroomCloud(clientLevel, packet.x, packet.y, packet.z);
            }
        });
        context.setPacketHandled(true);
    }

    private static void spawnSparks(net.minecraft.client.multiplayer.ClientLevel clientLevel, double x, double y, double z) {
        var random = net.minecraft.util.RandomSource.create();

        for (int i = 0; i < 400; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;
            double phi = random.nextDouble() * Math.PI;

            double dirX = Math.sin(phi) * Math.cos(theta);
            double dirY = Math.cos(phi);
            double dirZ = Math.sin(phi) * Math.sin(theta);

            double speed = 0.8 + random.nextDouble() * 0.5;

            clientLevel.addAlwaysVisibleParticle(
                    (net.minecraft.core.particles.SimpleParticleType) com.trd.fx.particle.ModExplosionParticles.EXPLOSION_SPARK.get(),
                    true,
                    x, y, z,
                    dirX * speed, dirY * speed, dirZ * speed
            );
        }
    }

    private static void spawnFireSparks(net.minecraft.client.multiplayer.ClientLevel clientLevel, double x, double y, double z) {
        var random = net.minecraft.util.RandomSource.create();

        for (int i = 0; i < 400; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;
            double phi = random.nextDouble() * Math.PI;

            double dirX = Math.sin(phi) * Math.cos(theta);
            double dirY = Math.cos(phi);
            double dirZ = Math.sin(phi) * Math.sin(theta);

            double speed = 0.8 + random.nextDouble() * 0.5;

            clientLevel.addAlwaysVisibleParticle(
                    (net.minecraft.core.particles.SimpleParticleType) com.trd.fx.particle.ModExplosionParticles.FIRE_SPARK.get(),
                    true,
                    x, y, z,
                    dirX * speed, dirY * speed, dirZ * speed
            );
        }
    }

    private static void spawnShockwave(net.minecraft.client.multiplayer.ClientLevel clientLevel, double x, double y, double z) {
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
                    (net.minecraft.core.particles.SimpleParticleType) com.trd.fx.particle.ModExplosionParticles.WAVE_SMOKE.get(),
                    true,
                    x + offsetX, y + offsetY, z + offsetZ,
                    xSpeed, ySpeed, zSpeed
            );
        }
    }

    private static void spawnMushroomCloud(net.minecraft.client.multiplayer.ClientLevel clientLevel, double x, double y, double z) {
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
                    (net.minecraft.core.particles.SimpleParticleType) com.trd.fx.particle.ModExplosionParticles.DARK_SMOKE.get(),
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
                    (net.minecraft.core.particles.SimpleParticleType) com.trd.fx.particle.ModExplosionParticles.DARK_SMOKE.get(),
                    true,
                    x + offsetX, capY, z + offsetZ,
                    xSpeed, ySpeed, zSpeed
            );
        }
    }
}
