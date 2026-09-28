package com.trd.block.basic.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Колючая проволока.
 * <p>
 * Механика:
 * <ul>
 *   <li>Неколлизионна как паутина: сущность проваливается внутрь проволоки и вязнет в ней.</li>
 *   <li>Замедляет сущность точно как паутина (0.25 / 0.05 / 0.25) через {@link Entity#makeStuckInBlock}.</li>
 *   <li>Если сущность движется внутри проволоки, она получает урон. Чем выше горизонтальная скорость —
 *       тем чаще срабатывания урона. При скорости 1 блок/сек урон идёт "тик через тик".</li>
 *   <li>Урон зависит от очков защиты: 100% при 0 очков, 0% при 20 очках (полный сет незерита).</li>
 * </ul>
 */
public class BarbedWireBlock extends SteelPropsBlock {

    // Множитель замедления как у паутины (горизонталь/вертикаль)
    private static final Vec3 WEB_SLOW = new Vec3(0.25D, 0.05D, 0.25D);

    // Скорость (блоков/сек), при которой достигается максимальная частота урона
    private static final double MAX_DAMAGE_SPEED = 1.0D;
    // Ниже этой скорости (блоков/сек) урон не наносится: сущность стоит на месте, а не выбирается
    private static final double MIN_DAMAGE_SPEED = 0.03D;
    // Максимум срабатываний в тик: 0.5 => "урон тик через тик"
    private static final double MAX_HITS_PER_TICK = 0.5D;

    // Базовый урон за одно срабатывание при 0 очков защиты
    private static final float BASE_DAMAGE = 2.0F;
    // Очки защиты, при которых урон полностью исчезает
    private static final int ARMOR_POINTS_FOR_IMMUNITY = 30;

    public static final ResourceKey<DamageType> BARBED_WIRE_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("trd", "barbed_wire"));

    // Состояние замеров движения для каждой сущности (WeakHashMap — не держим ссылки на умерших)
    private static final Map<Entity, TrackState> TRACKING = new WeakHashMap<>();

    private static final class TrackState {
        long lastTick = -1;
        Vec3 lastPos;
        double charge;
    }

    public BarbedWireBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Проволока — горизонтальная лента (модель поворачивается по Y), поэтому только 4 стороны
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Пустая коллизия как у паутины: иначе сущность вставала бы на проволоку, а не проваливалась внутрь
        return Shapes.empty();
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        // Прозрачна для света и соседних блоков (как паутина)
        return Shapes.empty();
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // Замедление как у паутины
        entity.makeStuckInBlock(state, WEB_SLOW);

        if (level.isClientSide) return;
        if (!(entity instanceof LivingEntity living)) return;
        if (living instanceof Player player && (player.getAbilities().instabuild || player.isSpectator())) return;

        long tick = level.getGameTime();
        Vec3 now = entity.position();

        TrackState track = TRACKING.computeIfAbsent(living, k -> new TrackState());

        // Первый контакт — только фиксируем позицию
        if (track.lastTick < 0) {
            track.lastTick = tick;
            track.lastPos = now;
            return;
        }

        long elapsed = tick - track.lastTick;
        if (elapsed <= 0) return; // защита от повторного вызова в одном тике

        double moved = track.lastPos.distanceTo(now);
        track.lastPos = now;
        track.lastTick = tick;

        // Сущность вышла из проволоки надолго — сбрасываем накопленный заряд
        if (elapsed > 2) {
            track.charge = 0;
            return;
        }

        double speed = moved / (double) elapsed * 20.0D; // блоков/сек
        if (speed < MIN_DAMAGE_SPEED) {
            track.charge = 0;
            return;
        }

        double fraction = Math.min(1.0D, speed / MAX_DAMAGE_SPEED) * MAX_HITS_PER_TICK;
        track.charge += fraction;
        if (track.charge >= 1.0D) {
            track.charge -= 1.0D;

            int armor = Math.min(ARMOR_POINTS_FOR_IMMUNITY, living.getArmorValue());
            float damage = BASE_DAMAGE * (1.0F - armor / (float) ARMOR_POINTS_FOR_IMMUNITY);
            if (damage > 0.0F) {
                living.hurt(barbedWireSource(level), damage);
            }
        }
    }

    private static DamageSource barbedWireSource(Level level) {
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(BARBED_WIRE_DAMAGE);
        return new DamageSource(holder);
    }
}