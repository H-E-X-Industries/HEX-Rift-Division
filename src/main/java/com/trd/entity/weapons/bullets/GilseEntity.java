package com.trd.entity.weapons.bullets;

import com.trd.entity.ModEntities;
import com.trd.sound.ModSounds;
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

    /**
     * Трение о вертикальную скорость в полёте. Лёгкое, чтобы гильза
     * нормально падала, а не висела в воздухе.
     */
    private static final double DRAG = 0.98D;

    /**
     * Трение о горизонтальную скорость в полёте.
     * <p>
     * Сильно отличается от вертикального, и это неслучайно. При DRAG выше
     * суммарный путь по горизонтали равен {@code v / (1 - DRAG)}, то есть при
     * 0.98 это {@code v * 50}: гильза, вылетевшая со скоростью 2 блока/тик,
     * улетала на сотню блоков. Сильное горизонтальное трение удерживает её
     * рядом со стрелком — примерно в пределах полублока, — не мешая падению.
     */
    private static final double HORIZONTAL_AIR_DRAG = 0.6D;

    /**
     * Трение о землю. Сильнее воздушного, поэтому улегшаяся гильза быстро
     * останавливается, а не скользит бесконечно.
     */
    private static final double GROUND_DRAG = 0.5D;

    private static final double RESTING_SPEED = 0.02D;

    /**
     * Коэффициент отскока и порог, ниже которого отскока не будет вовсе.
     * <p>
     * BOUNCE поднят с 0.45 до 0.9: гильза отскакивает почти на половину
     * скорости удара, то есть заметно подпрыгивает, но не улетает — при
     * коэффициенте около единицы отскок превратился бы в бесконечные прыжки.
     * Порог нужен, чтобы гильза, уже почти остановившаяся у стены, не
     * подпрыгивала от её пиксельных неровностей.
     */
    private static final double BOUNCE = 0.9D;
    private static final double MIN_BOUNCE_SPEED = 0.05D;

    private static final EntityDataAccessor<Integer> SHOT_TICK =
            SynchedEntityData.defineId(GilseEntity.class, EntityDataSerializers.INT);

    private int shotTick;
    private int age;

    /**
     * Отскочила ли гильза уже. Отскок разрешён ровно один: иначе на неровной
     * поверхности гильза начинает клацать и подпрыгивать по каждому бугорку.
     */
    private boolean bounced;

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
            // По вертикали лёгкое трение, по горизонтали — сильное (см.
            // HORIZONTAL_AIR_DRAG): так гильза быстро падает, но далеко не
            // улетает вбок.
            motion = new Vec3(
                    motion.x * HORIZONTAL_AIR_DRAG,
                    motion.y * DRAG - GRAVITY,
                    motion.z * HORIZONTAL_AIR_DRAG
            );
        }

        this.setDeltaMovement(motion);

        // Было ли касание блока на этом тике. Отслеживается только для отскока.
        boolean wasOnGround = this.onGround();
        boolean hitGround = false;

        // Именно move() двигает сущность и разруливает столкновения. Раньше его
        // здесь не было вовсе, поэтому гильза меняла только скорость и поворот,
        // а координаты оставались в точке спавна — визуально она зависала в
        // воздухе возле оружия. onGround() тоже проставляется внутри move(),
        // поэтому без него условие «лежит на земле» никогда не выполнялось.
        this.move(MoverType.SELF, motion);

        // Удар о пол или о стену: было в воздухе, оказалось на земле.
        hitGround = !wasOnGround && this.onGround();

        // Боковой удар: move() гасит компоненту движения о препятствие, но
        // onGround() при этом остаётся false.
        if (!hitGround && !wasOnGround) {
            Vec3 after = this.getDeltaMovement();
            if (Math.abs(after.y - motion.y) > 1.0E-4D
                    || Math.abs(after.x - motion.x) > 1.0E-4D
                    || Math.abs(after.z - motion.z) > 1.0E-4D) {
                hitGround = true;
            }
        }

        // Отскок и звук — строго на первом касании, больше ни разу.
        //
        // Раньше звук висел на hitGround, и это давало непрерывное щёлканье:
        // у лежащей гильзы move() каждый тик чуть гасит вертикальную
        // компоненту, вторая проверка это ловит как касание, и звук играл
        // каждые 50 мс все 30 секунд жизни. Теперь оба события заперты на
        // флаге bounced — за жизнь гильзы ровно один щелчок и один отскок.
        if (hitGround && !bounced) {
            bounced = true;
            // Скорость берём до move(): он обнуляет fallDistance внутри себя.
            playImpactSound(motion.y);
            bounce(motion);
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
     * Щелчок гильзы при касании блока.
     * <p>
     * Только на сервере: {@code level().playSound} сам рассылает звук всем
     * nearby-игрокам, а на клиенте тот же вызов проиграл бы его повторно у
     * самого стрелка — получился бы двойной щелчок.
     * <p>
     * Сила удара передаётся снаружи и берётся из скорости <em>до</em> move():
     * внутри {@code move()} вызывается {@code checkFallDamage}, который
     * обнуляет {@code fallDistance}, и к моменту проверки он уже всегда ноль.
     */
    private void playImpactSound(double impactSpeed) {
        if (this.level().isClientSide) return;
        if (!ModSounds.GILSE_LIGHT.isBound()) return;

        float strength = Math.min(1.0F, (float) Math.abs(impactSpeed) * 1.6F);
        if (strength <= 0.0F) {
            strength = 0.3F;
        }

        // Пичч и громкость слегка разбросаны: гильзы летят разные (разный
        // угол выстрела, разная высота), и одинаковый звук на каждое касание
        // звучал бы механически. Громкость зависит от силы удара: чем мягче
        // касание, тем тише.
        float pitch = 0.92F + this.random.nextFloat() * 0.18F;
        float volume = (0.3F + strength * 0.55F) * (0.9F + this.random.nextFloat() * 0.2F);

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.GILSE_LIGHT.get(), net.minecraft.sounds.SoundSource.PLAYERS,
                volume, pitch);
    }

    /**
     * Отскок от поверхности при первом касании.
     * <p>
     * Формула та же, что у гранат ({@code GrenadeIfProjectileEntity}): скорость
     * зеркально отражается относительно нормали поверхности, то есть
     * {@code v - 2 * (v . n) * n}, и гасится на {@link #BOUNCE}. Раньше здесь
     * было простоое переворачивание вертикальной компоненты, из-за чего удар о
     * боковую стену гасил скорость совсем, а удар о пол не давал подброса под
     * углом.
     * <p>
     * Вызывается исключительно на сервере: отскок меняет физику сущности, и
     * клиентское предсказание только мешало бы — сервер всё равно пришлёт
     * authoritative-позицию.
     */
    private void bounce(Vec3 incoming) {
        if (this.level().isClientSide) return;

        // Упав так медленно, что отскок был бы незаметен: просто ложимся.
        if (incoming.length() < MIN_BOUNCE_SPEED) {
            return;
        }

        Vec3 normal = contactNormal(incoming);
        Vec3 reflected = incoming.subtract(normal.scale(2.0D * incoming.dot(normal)));
        this.setDeltaMovement(reflected.scale(BOUNCE));
        this.hasImpulse = true;
    }

    /**
     * Нормаль поверхности, в которую ударилась гильза.
     * <p>
     * У гранат она приходит из {@code BlockHitResult} вместе с лучом. У гильзы
     * своего луча нет — она просто перемещается вызовом {@code move()}, и тот
     * гасит компоненту движения о препятствие, но нормаль не отдаёт. Поэтому
     * направление определяем по тому, какие компоненты скорости изменились:
     * погашенная по вертикали — удар о пол или о потолок, по горизонтали — о
     * стену.
     */
    private Vec3 contactNormal(Vec3 incoming) {
        Vec3 after = this.getDeltaMovement();
        Vec3 delta = after.subtract(incoming);

        boolean hitVertical = Math.abs(delta.y) > 1.0E-4D;
        boolean hitHorizontal =
                Math.abs(delta.x) > 1.0E-4D || Math.abs(delta.z) > 1.0E-4D;

        // Два сразу — угловой удар. Берём нормаль по преобладающей компоненте
        // скорости: она указывает, с какой стороны прилетел удар.
        if (hitVertical && hitHorizontal) {
            double ax = Math.abs(incoming.x);
            double ay = Math.abs(incoming.y);
            double az = Math.abs(incoming.z);
            if (ay >= ax && ay >= az) {
                return new Vec3(0.0D, incoming.y >= 0.0D ? 1.0D : -1.0D, 0.0D);
            }
            if (ax >= az) {
                return new Vec3(incoming.x >= 0.0D ? 1.0D : -1.0D, 0.0D, 0.0D);
            }
            return new Vec3(0.0D, 0.0D, incoming.z >= 0.0D ? 1.0D : -1.0D);
        }

        if (hitVertical) {
            return new Vec3(0.0D, incoming.y >= 0.0D ? 1.0D : -1.0D, 0.0D);
        }

        if (hitHorizontal) {
            // Стена: нормаль лежит в плоскости пола, берём её из той
            // горизонтальной оси, по которой ударились сильнее.
            if (Math.abs(after.x - incoming.x) > Math.abs(after.z - incoming.z)) {
                return new Vec3(incoming.x >= 0.0D ? 1.0D : -1.0D, 0.0D, 0.0D);
            }
            return new Vec3(0.0D, 0.0D, incoming.z >= 0.0D ? 1.0D : -1.0D);
        }

        // Столкновения не было (среда или погрешность) — отражаемся от пола.
        return new Vec3(0.0D, 1.0D, 0.0D);
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
        // Отскок необратим: после перезагрузки мира гильза не должна
        // подпрыгивать заново.
        tag.putBoolean("GilseBounced", this.bounced);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt("GilseAge");
        this.shotTick = tag.getInt("GilseShotTick");
        if (tag.hasUUID("GilseShooter")) {
            this.shooterUUID = tag.getUUID("GilseShooter");
        }
        this.bounced = tag.getBoolean("GilseBounced");
    }
}