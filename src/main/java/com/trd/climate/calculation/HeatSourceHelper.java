package com.trd.climate.calculation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Система push-нагрева от источников тепла (костры, печки, лава).
 * Источник тепла активно излучает тепло в определенном радиусе.
 */
public class HeatSourceHelper {

    // Радиус излучения тепла для различных источников
    private static final double CAMPFIRE_RADIUS = 5.0;
    private static final double FURNACE_RADIUS = 3.0;
    private static final double LAVA_RADIUS = 4.0;
    private static final double FIRE_RADIUS = 2.5;

    // Максимальная температура от источников
    private static final float CAMPFIRE_MAX_HEAT = 15.0f; // +15°C в упор к костру
    private static final float FURNACE_MAX_HEAT = 12.0f;  // +12°C у работающей печи
    private static final float LAVA_MAX_HEAT = 20.0f;     // +20°C у лавы
    private static final float FIRE_MAX_HEAT = 10.0f;     // +10°C у огня

    /**
     * Сканирует окружающие блоки на наличие источников тепла.
     * Возвращает список найденных источников в радиусе действия.
     */
    public static List<HeatSource> findNearbyHeatSources(ServerLevel level, BlockPos playerPos) {
        List<HeatSource> sources = new ArrayList<>();
        
        // Максимальный радиус сканирования (берем наибольший из всех источников)
        int maxRadius = (int) Math.ceil(Math.max(CAMPFIRE_RADIUS, 
                        Math.max(FURNACE_RADIUS, Math.max(LAVA_RADIUS, FIRE_RADIUS))));
        
        BlockPos.MutableBlockPos checkPos = new BlockPos.MutableBlockPos();
        
        // Сканируем куб вокруг игрока
        for (int x = -maxRadius; x <= maxRadius; x++) {
            for (int y = -maxRadius; y <= maxRadius; y++) {
                for (int z = -maxRadius; z <= maxRadius; z++) {
                    checkPos.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                    
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    BlockState state = level.getBlockState(checkPos);
                    
                    // Проверяем костры
                    if (state.is(Blocks.CAMPFIRE) && distance <= CAMPFIRE_RADIUS) {
                        // Проверяем, горит ли костер
                        if (state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
                            sources.add(new HeatSource(checkPos.immutable(), distance, 
                                       CAMPFIRE_MAX_HEAT, CAMPFIRE_RADIUS, HeatSourceType.CAMPFIRE));
                        }
                    }
                    // Проверяем костры-душ (soul campfire)
                    else if (state.is(Blocks.SOUL_CAMPFIRE) && distance <= CAMPFIRE_RADIUS) {
                        if (state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
                            sources.add(new HeatSource(checkPos.immutable(), distance, 
                                       CAMPFIRE_MAX_HEAT * 0.7f, CAMPFIRE_RADIUS, HeatSourceType.SOUL_CAMPFIRE));
                        }
                    }
                    // Проверяем печки и коптильни
                    else if ((state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || 
                              state.is(Blocks.SMOKER)) && distance <= FURNACE_RADIUS) {
                        // Проверяем, горит ли печь
                        if (state.getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT)) {
                            sources.add(new HeatSource(checkPos.immutable(), distance, 
                                       FURNACE_MAX_HEAT, FURNACE_RADIUS, HeatSourceType.FURNACE));
                        }
                    }
                    // Проверяем лаву
                    else if (state.is(Blocks.LAVA) && distance <= LAVA_RADIUS) {
                        sources.add(new HeatSource(checkPos.immutable(), distance, 
                                   LAVA_MAX_HEAT, LAVA_RADIUS, HeatSourceType.LAVA));
                    }
                    // Проверяем огонь
                    else if (state.is(Blocks.FIRE) && distance <= FIRE_RADIUS) {
                        sources.add(new HeatSource(checkPos.immutable(), distance, 
                                   FIRE_MAX_HEAT, FIRE_RADIUS, HeatSourceType.FIRE));
                    }
                }
            }
        }
        
        return sources;
    }

    /**
     * Рассчитывает суммарное тепло от всех источников с учетом расстояния,
     * преград и уровня укрытия.
     */
    public static float calculateTotalHeat(ServerLevel level, BlockPos playerPos, float shelterLevel) {
        List<HeatSource> sources = findNearbyHeatSources(level, playerPos);
        float totalHeat = 0.0f;
        
        Vec3 playerEyePos = new Vec3(playerPos.getX() + 0.5, playerPos.getY() + 1.5, playerPos.getZ() + 0.5);
        
        for (HeatSource source : sources) {
            // Проверяем прямую видимость (рейкаст)
            Vec3 sourcePos = new Vec3(source.pos.getX() + 0.5, source.pos.getY() + 0.5, source.pos.getZ() + 0.5);
            
            // Используем конструктор с CollisionContext
            ClipContext context = new ClipContext(
                playerEyePos,
                sourcePos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()
            );
            
            BlockHitResult hitResult = level.clip(context);
            
            // Если есть преграда между игроком и источником - пропускаем
            if (hitResult.getType() != HitResult.Type.MISS) {
                BlockPos hitPos = hitResult.getBlockPos();
                if (!hitPos.equals(source.pos)) {
                    continue; // Стена блокирует тепло
                }
            }
            
            // Рассчитываем тепло от источника с учетом расстояния (обратный квадрат)
            float distanceFactor = (float) (1.0 - (source.distance / source.radius));
            distanceFactor = Math.max(0.0f, distanceFactor); // Клампим к [0, 1]
            distanceFactor = distanceFactor * distanceFactor; // Квадратичный спад
            
            float heat = source.maxHeat * distanceFactor;
            
            // Применяем модификатор укрытия
            // На открытом воздухе (shelterLevel = 0) тепло сдувается ветром (30% эффективности)
            // В укрытии (shelterLevel = 1) тепло работает на 100%
            float shelterBonus = 0.3f + 0.7f * shelterLevel;
            heat *= shelterBonus;
            
            totalHeat += heat;
        }
        
        return totalHeat;
    }

    /**
     * Класс для хранения информации об источнике тепла.
     */
    public static class HeatSource {
        public final BlockPos pos;
        public final double distance;
        public final float maxHeat;
        public final double radius;
        public final HeatSourceType type;

        public HeatSource(BlockPos pos, double distance, float maxHeat, double radius, HeatSourceType type) {
            this.pos = pos;
            this.distance = distance;
            this.maxHeat = maxHeat;
            this.radius = radius;
            this.type = type;
        }
    }

    /**
     * Типы источников тепла.
     */
    public enum HeatSourceType {
        CAMPFIRE,
        SOUL_CAMPFIRE,
        FURNACE,
        LAVA,
        FIRE
    }
}
