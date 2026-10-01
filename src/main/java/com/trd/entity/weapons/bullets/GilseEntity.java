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
    private static final double DRAG = 0.7D;

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

    /**
     * Направление полёта гильзы: yaw и pitch в градусах плюс собственное
     * вращение вокруг оси полёта.
     * <p>
     * Именно здесь, а не в {@code setYRot}, потому что у гильзы не
     * синхронизируется скорость. Пуля рендерит углы, посчитанные клиентом из
     * {@code getDeltaMovement()}, и это допустимо только потому, что её
     * скорость приходит пакетами {@code ClientboundSetEntityMotionPacket} и
     * совпадает с серверной. У гильзы клиент физичит сам, причём {@code bounce()}
     * отрабатывает только на сервере, — поэтому выведенный из скорости угол
     * разошёлся бы с реальным и гильза смотрела бы не туда, куда летит.
     * <p>
     * Эти три float едут через {@code SynchedEntityData}, а не в пакете
     * поворота: пакет поворота квантуется до байта ({@code yaw * 256 / 360}),
     * это 1.4 градуса шага и заметные рывки, плюс он шлёт раз в
     * {@code updateInterval} тиков — то есть раз в 4 тика. Данные же
     * отправляются каждый тик без квантования.
     */
    private static final EntityDataAccessor<Float> FLIGHT_YAW =
            SynchedEntityData.defineId(GilseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FLIGHT_PITCH =
            SynchedEntityData.defineId(GilseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FLIGHT_SPIN =
            SynchedEntityData.defineId(GilseEntity.class, EntityDataSerializers.FLOAT);

    /** Текущий yaw полёта в градусах. Публичный: читает рендерер. */
    public float flightYawDeg;
    /** Текущий pitch полёта в градусах. Публичный: читает рендерер. */
    public float flightPitchDeg;
    /** Собственное вращение вокруг оси полёта. Публичный: читает рендерер. */
    public float flightSpinDeg;

    private float prevFlightYawDeg;
    private float prevFlightPitchDeg;
    private float prevFlightSpinDeg;

    public float getRenderYaw(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, this.prevFlightYawDeg, this.flightYawDeg);
    }

    public float getRenderPitch(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, this.prevFlightPitchDeg, this.flightPitchDeg);
    }

    public float getRenderSpin(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, this.prevFlightSpinDeg, this.flightSpinDeg);
    }

    /**
     * Радиус столкновения гильз между собой.
     * <p>
     * Чуть меньше половины хитбокса (0.2, то есть 0.1): иначе две гильзы,
     * просто рядом лежащие, числились бы пересекающимися и разлетались бы
     * каждую секунду, не переставая.
     */
    private static final double CASING_RADIUS = 0.09D;

    /**
     * Процент возврата скорости при ударе гильзы о гильзу. 0.4 — чтобы они
     * отскакивали друг от друга заметно, но не играли в пинбол: при единице
     * столкновения почти наверху пара уходит в бесконечные прыжки, потому что
     * обе гильзы каждый тик снова входят в контакт.
     */
    private static final double CASING_RESTITUTION = 0.4D;

    /**
     * Градусов собственного вращения за тик на один блок/тик горизонтальной
     * скорости. Подобрано так, чтобы на типичной скорости вылета гильза
     * делала примерно один оборот за треть секунды — как настоящая.
     */
    private static final double SPIN_PER_SPEED = 90.0D;

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

        // Направление — по собственной скорости гильзы, а не по повороту
        // стрелка. Гильза вылетает вбок из-под ствола, и поворот игрока тут
        // ни при чём: раньше гильза выставлялась по нему и первый кадр
        // смотрела туда, куда смотрит игрок, а не туда, куда летит сама.
        this.syncFlightAngle();
        // Прошлые углы прижимаем к текущим, иначе рендер в первый кадр
        // интерполирует поворот от нуля и гильза доворачивается на месте.
        this.prevFlightYawDeg = this.flightYawDeg;
        this.prevFlightPitchDeg = this.flightPitchDeg;
        this.prevFlightSpinDeg = this.flightSpinDeg;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SHOT_TICK, 0);
        builder.define(FLIGHT_YAW, 0.0F);
        builder.define(FLIGHT_PITCH, 0.0F);
        builder.define(FLIGHT_SPIN, 0.0F);
    }

    @Override
    public void tick() {
        if (++this.age >= LIFETIME_TICKS) {
            this.discard();
            return;
        }

        super.tick();

        // Запоминаем предыдущие углы до того, как посчитаем новые: между ними
        // рендер интерполирует, иначе гильза вращалась бы рывками по тику.
        this.prevFlightYawDeg = this.flightYawDeg;
        this.prevFlightPitchDeg = this.flightPitchDeg;
        this.prevFlightSpinDeg = this.flightSpinDeg;

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

        // Гильзы отскакивают друг от друга. Именно здесь, а не до move():
        // к этому моменту обе уже разошлись блоками и move() успел обработать
        // столкновения с миром, так что толчок от другой гильзы не уйдёт в
        // стену.
        collideWithOtherCasings();

        // Угол считаем последним: к этому моменту скорость уже изменилась и
        // от отскока о блок, и от удара о другую гильзу. Посчитали бы раньше —
        // гильза отрисовалась бы по устаревшему направлению.
        syncFlightAngle();

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
     * Пересчитывает направление полёта и публикует его клиентам.
     * <p>
     * На сервере угол считается из скорости и кладётся в
     * {@link SynchedEntityData}; на клиенте, наоборот, только читается оттуда и
     * ничего сам не вычисляет. Односторонность тут принципиальна: клиентская
     * физика гильзы не совпадает с серверной (отскок и столкновения играются
     * только на сервере), поэтому если бы клиент ещё и считал углы у себя, они
     * бы постоянно перетягивали значение туда, куда гильза уже не летит.
     * <p>
     * Плюс к yaw/pitch идёт собственное вращение вокруг оси полёта: гильза —
     * цилиндр, и без прокрутки вокруг своей оси она выглядит просто
     * наклоняющейся, а не кувыркающейся.
     */
    private void syncFlightAngle() {
        if (this.level().isClientSide) {
            this.flightYawDeg = this.entityData.get(FLIGHT_YAW);
            this.flightPitchDeg = this.entityData.get(FLIGHT_PITCH);
            this.flightSpinDeg = this.entityData.get(FLIGHT_SPIN);
            return;
        }

        Vec3 dir = this.getDeltaMovement();
        double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);

        if (dir.lengthSqr() > 1.0E-6D) {
            // Направление на спине гильзы смотрит вдоль полёта. Формулы те же,
            // что у пули в TurretBulletGltfRenderer.
            float yawDeg = (float) (Math.atan2(dir.x, dir.z) * (180.0D / Math.PI));
            float pitchDeg = (float) (Math.atan2(dir.y, horizontal) * (180.0D / Math.PI));

            // Вращение копится, а не задаётся заново: полный оборот должен
            // занимать столько же времени, сколько идёт круг вокруг оси.
            this.flightSpinDeg =
                    (this.flightSpinDeg + (float) (horizontal * SPIN_PER_SPEED)) % 360.0F;

            this.setYRot(yawDeg);
            this.setXRot(pitchDeg);

            this.flightYawDeg = yawDeg;
            this.flightPitchDeg = pitchDeg;
        }
        // При почти нулевой скорости углы не трогаем: пересчёт в ноль дёргал бы
        // улегшуюся гильзу на последнем тике перед остановкой.

        this.entityData.set(FLIGHT_YAW, this.flightYawDeg);
        this.entityData.set(FLIGHT_PITCH, this.flightPitchDeg);
        this.entityData.set(FLIGHT_SPIN, this.flightSpinDeg);
    }

    /**
     * Толкает гильзу при столкновении с другой гильзой.
     * <p>
     * У {@link Entity} нет столкновений между сущностями — {@code move()}
     * разруливает только блоки. Гильзы же вылетают плотной струёй и сразу
     * наезжают друг на друга, поэтому контакт разбираем вручную, как
     * столкновение двух шариков: сначала разводим позиции по нормали, затем
     * обмениваемся скоростью вдоль этой нормали.
     * <p>
     * Только сервер: и позиции, и скорость здесь меняются из расчёта,
     * клиентская копия физики их не повторит, а разойтись им после этого
     * разрешено — authoritative-пакет всё перезапишет.
     */
    private void collideWithOtherCasings() {
        if (this.level().isClientSide) {
            return;
        }

        List<GilseEntity> others = new ArrayList<>();
        this.level().getEntities(EntityTypeTest.forClass(GilseEntity.class),
                this.getBoundingBox().inflate(CASING_RADIUS),
                (other) -> other != this, others);

        for (GilseEntity other : others) {
            Vec3 diff = this.position().subtract(other.position());
            double distSqr = diff.lengthSqr();
            double minDist = CASING_RADIUS * 2.0D;

            // Полностью совпавшие позиции (дистанция ровно ноль) нормаль не
            // определит — пропускаем такую пару, пусть просто останутся
            // на месте, вместо деления на ноль.
            if (distSqr < 1.0E-8D || distSqr >= minDist * minDist) {
                continue;
            }

            double dist = Math.sqrt(distSqr);
            Vec3 normal = diff.scale(1.0D / dist);

            // Разводим позиции на половину взаимного проникновения каждая.
            // Именно setPos, а не push(): push() в Entity не двигает сущность,
            // он добавляет импульс к скорости — это совсем другое по смыслу и
            // развело бы гильзы только по скорости, оставив визуальное
            // пересечение.
            double overlap = (minDist - dist) * 0.5D;
            this.setPos(this.getX() + normal.x * overlap,
                    this.getY() + normal.y * overlap,
                    this.getZ() + normal.z * overlap);
            other.setPos(other.getX() - normal.x * overlap,
                    other.getY() - normal.y * overlap,
                    other.getZ() - normal.z * overlap);

            Vec3 relative = this.getDeltaMovement().subtract(other.getDeltaMovement());
            double approachSpeed = relative.dot(normal);

            // Уже расходятся — не мешаем им разойтись, иначе толчок в обратную
            // сторону заставил бы пару дребезжать на месте.
            if (approachSpeed >= 0.0D) {
                continue;
            }

            // Упругий удар вдоль нормали. Обмен компонентами взаимный, так что
            // импульс уходит симметрично и физика не нарушается.
            double impulse = (1.0D + CASING_RESTITUTION) * approachSpeed;
            this.setDeltaMovement(this.getDeltaMovement().subtract(normal.scale(impulse)));
            other.setDeltaMovement(other.getDeltaMovement().add(normal.scale(impulse)));

            this.hasImpulse = true;
            other.hasImpulse = true;
        }
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
        // Вращение копится с момента вылета. Без него перезагрузка мира
        // обнулила бы его, и гильза дёрнулась бы на произвольный угол.
        tag.putFloat("GilseSpin", this.flightSpinDeg);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt("GilseAge");
        this.shotTick = tag.getInt("GilseShotTick");
        if (tag.hasUUID("GilseShooter")) {
            this.shooterUUID = tag.getUUID("GilseShooter");
        }
        this.bounced = tag.getBoolean("GilseBounced");
        this.flightSpinDeg = tag.getFloat("GilseSpin");
        this.entityData.set(FLIGHT_SPIN, this.flightSpinDeg);
    }
}