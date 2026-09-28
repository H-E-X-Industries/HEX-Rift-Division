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
 * Колючая проволока: замедляет, царапает и накапливает «заряд» при движении.
 * <ul>
 *   <li>Цепляет сущность и замедляет её (0.25 / 0.05 / 0.25) через {@link Entity#makeStuckInBlock}.</li>
 *   <li>Чем быстрее движется сущность, тем быстрее копится заряд. 1 заряд = 1 тик урона.</li>
 *   <li>Урон ослабевает от брони: 30 очков брони = полный иммунитет.</li>
 * </ul>
 */
public class BarbedWireBlock extends SteelPropsBlock {

    // Множитель замедления (x/y/z)
    private static final Vec3 WEB_SLOW = new Vec3(0.25D, 0.05D, 0.25D);

    // Скорость (блоков/тик), при которой урон идёт максимально быстро
    private static final double MAX_DAMAGE_SPEED = 1.0D;
    // Порог, ниже которого персонаж считается стоящим на месте
    private static final double MIN_DAMAGE_SPEED = 0.03D;
    // Сколько тиков урона набирается за секунду непрерывного движения
    private static final double MAX_HITS_PER_TICK = 0.5D;

    private static final float BASE_DAMAGE = 2.0F;
    private static final int ARMOR_POINTS_FOR_IMMUNITY = 30;

    public static final ResourceKey<DamageType> BARBED_WIRE_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("trd", "barbed_wire"));

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
        // Проволока всегда строго горизонтальна (ось Y не используется, 4 направления)
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        entity.makeStuckInBlock(state, WEB_SLOW);

        if (level.isClientSide) return;
        if (!(entity instanceof LivingEntity living)) return;
        if (living instanceof Player player && (player.getAbilities().instabuild || player.isSpectator())) return;

        long tick = level.getGameTime();
        Vec3 now = entity.position();

        TrackState track = TRACKING.computeIfAbsent(living, k -> new TrackState());

        if (track.lastTick < 0) {
            track.lastTick = tick;
            track.lastPos = now;
            return;
        }

        long elapsed = tick - track.lastTick;
        if (elapsed <= 0) return;

        double moved = track.lastPos.distanceTo(now);
        track.lastPos = now;
        track.lastTick = tick;

        // Слишком давно не обновлялись — считаем, что персонаж только зашёл на проволоку
        if (elapsed > 2) {
            track.charge = 0;
            return;
        }

        double speed = moved / (double) elapsed * 20.0D;
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
