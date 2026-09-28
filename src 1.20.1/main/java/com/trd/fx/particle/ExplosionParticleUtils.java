package com.trd.fx.particle;

import com.trd.network.packet.explosion.SpawnExplosionParticlesPacket;
import net.minecraft.server.level.ServerLevel;

public class ExplosionParticleUtils {

    public static final int EFFECT_FULL = 0;
    public static final int EFFECT_SPARKS = 1;
    public static final int EFFECT_FIRE_SPARKS = 2;
    public static final int EFFECT_SHOCKWAVE = 3;
    public static final int EFFECT_MUSHROOM_CLOUD = 4;

    public static void spawnAirBombExplosion(ServerLevel level, double x, double y, double z) {
        spawnAirBombSparks(level, x, y, z);
        level.getServer().tell(new net.minecraft.server.TickTask(3,
                () -> spawnAirBombShockwave(level, x, y, z)));
        level.getServer().tell(new net.minecraft.server.TickTask(8,
                () -> spawnAirBombMushroomCloud(level, x, y, z)));
    }

    public static void spawnAirBombSparks(ServerLevel level, double x, double y, double z) {
        SpawnExplosionParticlesPacket.send(level, EFFECT_SPARKS, x, y, z);
    }

    public static void spawnAirBombFireSparks(ServerLevel level, double x, double y, double z) {
        SpawnExplosionParticlesPacket.send(level, EFFECT_FIRE_SPARKS, x, y, z);
    }

    public static void spawnAirBombShockwave(ServerLevel level, double x, double y, double z) {
        SpawnExplosionParticlesPacket.send(level, EFFECT_SHOCKWAVE, x, y, z);
    }

    public static void spawnAirBombMushroomCloud(ServerLevel level, double x, double y, double z) {
        SpawnExplosionParticlesPacket.send(level, EFFECT_MUSHROOM_CLOUD, x, y, z);
    }
}
