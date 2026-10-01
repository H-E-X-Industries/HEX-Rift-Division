package com.trd.entity.weapons.bullets;

import com.trd.entity.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.UUID;
import java.util.List;

/**
 * Гильза, остающаяся после выстрела.
 * <p>
 * Живёт {@value #LIFETIME_TICKS} тиков (30 секунд), потом исчезает. Больше
 * {@value #MAX_PER_SHOOTER} штук на одного стрелка быть не может: при
 * автоматическом огне иначе за минуту оседает несколько сотен сущностей и
 * клиент начинает захлёбываться. При переполнении вытесняется самая старая.
 * <p>
 * Физика намеренно своя, а не от {@code ItemEntity}: гильза — не предмет, и
 * наследование тащило за собой стак в NBT, подбор и собственный рендер.
 * Здесь только гравитация, отскок от блоков и остановка на земле.
 */
public class GilseEntity extends Entity {

    /** Время жизни: 30 секунд. */
    public static final int LIFETIME_TICKS = 600;

    /** Максимум гильз на одного стрелка. */
    public static final int MAX_PER_SHOOTER = 5;

    /** Гравитация вдвое меньше земной: лёгкая гильза должна явно падать. */
    private static final double GRAVITY = 0.12D;
    private static final double DRAG = 0.98D;

    /**
     * Трение о землю. Сильнее воздушного, поэтому улегшаяся гильза быстро
     * останавливается, а не скользит бесконечно.
     */
    private static final double GROUND_DRAG = 0.7D;

    private static final double RESTING_SPEED = 0.02D;
    private static final double BOUNCE = 0.3D;

    private static final EntityDataAccessor<Integer> SHOT_TICK =
            SynchedEntityData.defineId(GilseEntity.class, EntityDataSerializers.INT);

    private int shotTick;
    private int age;

    /**
     * UUID стрелка. У {@link Entity} нет встроенного владельца — это поле есть
     * только у {@code Projectile}, а гильза им не является. Храним сами и
     * сравниваем по нему: это и есть признак «своя» гильза для лимита.
     */
    private UUID shooterUUID = new UUID(0L, 0L);

        public GilseEntity(EntityType<? extends GilseEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        this.setNoGravity(false);
    }

    public GilseEntity(Level level, LivingEntity shooter, Vec3 pos, Vec3 velocity) {
        this(ModEntities.GILSE.get(), level);
        this.setPos(pos.x, pos.y, pos.z);
        this.setDeltaMovement(velocity);
        this.setXRot(shooter.getXRot());
        this.setYRot(shooter.getYRot());
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SHOT_TICK, 0);
    }

    @Override
    public void tick() {
        if (++this.age >= LIFETIME_TICKS) {
            this.discard();
            return;
        }

        super.tick();

        Vec3 motion = this.getDeltaMovement();

        if (this.onGround()) {
            // Улеглась: ползём по земле и постепенно тормозим.
            motion = motion.scale(GROUND_DRAG);
            if (motion.horizontalDistanceSqr() < RESTING_SPEED * RESTING_SPEED) {
                motion = Vec3.ZERO;
            }
            // Вертикальную составляющую гасим полностью: прилипла к земле.
            motion = new Vec3(motion.x, 0.0D, motion.z);
        } else {
            motion = motion.scale(DRAG).add(0.0D, -GRAVITY, 0.0D);
        }

        this.setDeltaMovement(motion);

        // Именно move() двигает сущность и разруливает столкновения. Раньше его
        // здесь не было вовсе, поэтому гильза меняла только скорость и поворот,
        // а координаты оставались в точке спавна — визуально она зависала в
        // воздухе возле оружия. onGround() тоже проставляется внутри move(),
        // поэтому без него условие «лежит на земле» никогда не выполнялось.
        this.move(MoverType.SELF, motion);

        // Отскок от блока: гасим вертикальную скорость и подбрасываем вверх.
        if (this.onGround() && motion.y < 0.0D) {
            Vec3 after = this.getDeltaMovement();
            this.setDeltaMovement(after.x * BOUNCE, -after.y * BOUNCE, after.z * BOUNCE);
            this.hasImpulse = true;
        }

        // Кувыркаемся вслед за движением.
        Vec3 now = this.getDeltaMovement();
        if (now.lengthSqr() > 1.0E-6D) {
            double horizontal = Math.sqrt(now.x * now.x + now.z * now.z);
            this.setYRot((float) (Math.atan2(now.x, now.z) * (180.0D / Math.PI)));
            this.setXRot((float) (Math.atan2(now.y, horizontal) * (180.0D / Math.PI)));
        }

        // Просевшая гильза не должна раз в тик будить сеть пакетом позиции.
        if (motion.lengthSqr() > 1.0E-5D) {
            this.hasImpulse = true;
        }
    }

    /**
     * Ставит метку появления и убирает лишние гильзы этого же стрелка.
     * <p>
     * Только сервер: проверка лимита опирается на список сущностей уровня,
     * клиенту тут доверять нельзя.
     */
    public void enforceLimit(Level level, Entity shooter) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        this.shotTick = (int) serverLevel.getGameTime();
        this.entityData.set(SHOT_TICK, this.shotTick);
        this.shooterUUID = shooter.getUUID();

        List<GilseEntity> mine = new ArrayList<>();
        serverLevel.getEntities(
                EntityTypeTest.forClass(GilseEntity.class),
                (other) -> other != this && this.shooterUUID.equals(other.shooterUUID),
                mine);

        // mine.size() гильз этого стрелка уже в мире, плюс наша — новая.
        while (mine.size() + 1 > MAX_PER_SHOOTER) {
            GilseEntity oldest = null;
            for (GilseEntity candidate : mine) {
                if (oldest == null || candidate.shotTick < oldest.shotTick) {
                    oldest = candidate;
                }
            }
            if (oldest == null) {
                break;
            }
            mine.remove(oldest);
            oldest.discard();
        }
    }

    public int getShotTick() {
        return this.entityData.get(SHOT_TICK);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("GilseAge", this.age);
        tag.putInt("GilseShotTick", this.shotTick);
        tag.putUUID("GilseShooter", this.shooterUUID);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt("GilseAge");
        this.shotTick = tag.getInt("GilseShotTick");
        if (tag.hasUUID("GilseShooter")) {
            this.shooterUUID = tag.getUUID("GilseShooter");
        }
    }
}