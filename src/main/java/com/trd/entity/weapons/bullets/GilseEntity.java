package com.trd.entity.weapons.bullets;

import com.trd.entity.ModEntities;
import com.trd.sound.ModSounds;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Гильза, остающаяся после выстрела.
 * <p>
 * Живёт {@value #LIFETIME_TICKS} тиков (30 секунд), потом исчезает. Больше
 * {@value #MAX_PER_SHOOTER} штук на одного стрелка быть не может: при
 * автоматическом огне иначе за минуту оседает несколько сотен сущностей и
 * клиент начинает захлёбываться. При переполнении вытесняется самая старая.
 * <p>
 * <b>Физика.</b> Движение целиком ведёт {@link Entity#move}: он же гасит
 * скорость о препятствия, отражает её от блоков и выставляет
 * {@code verticalCollisionBelow}. Позиция нигде не подгоняется вручную и не
 * телепортируется — поэтому гильза стоит ровно там, куда её привело
 * столкновение, а не висит над полом на половину блока.
 * <p>
 * Гравитация применяется <em>всегда</em>, в том числе когда гильза уже лежит.
 * Именно это держит её на земле без всякой опоры со стороны кода: пока под
 * гильзой блок, вертикальная скорость гасится самим {@code move()}, и
 * {@code Entity#onGround} остаётся включённым ровно на всё время лежания.
 * Штатный {@code ServerEntity} шлёт пакет телепорта только при смене этого
 * флага, то есть за жизнь гильзы их выходит ровно один — в момент касания, и
 * то только если она потом снова оторвётся от земли.
 * <p>
 * <b>Поза.</b> Три состояния: {@link #STATE_FLIGHT} — ось гильзы следует за
 * скоростью, {@link #STATE_SETTLE} — гильза ложится и раскачивается по
 * затухающей пружине, {@link #STATE_REST} — всё замерло. Переворотов «как
 * попало» больше нет: в полёте поза не дёргается, а на поверхности гильза
 * один раз кладётся плашмя и до этого осевания качается.
 * <p>
 * Физику и позу считает только сервер. Клиент принимает готовые координаты и
 * углы и сглаживает их в рендере; собственных столкновений он не считает
 * вовсе, поэтому расхождений с сервером не бывает.
 */
public class GilseEntity extends Entity {

    /** Время жизни: 30 секунд. */
    public static final int LIFETIME_TICKS = 600;

    /** Максимум гильз на одного стрелка. */
    public static final int MAX_PER_SHOOTER = 5;

    // === ФИЗИКА ===

    /**
     * Гравитация, блоков за тик в квадрате. У самой {@code Entity} она 0.08;
     * берём чуть больше, чтобы латунная гильза падала читаемо.
     * <p>
     * Значение вместе с {@link #VERTICAL_DRAG} задаёт предельную скорость
     * падения {@code GRAVITY / (1 - VERTICAL_DRAG)} = 0.375 блока/тик, около
     * 7.5 м/с. Прежние 0.22 и 0.55 давали 0.49, а самые первые 0.12 и 0.3 —
     * 0.17, то есть гильза с трёх блоков летела больше секунды и читалась как
     * зависшая в воздухе.
     */
    private static final double GRAVITY = 0.12D;

    /** Сопротивление воздуха по вертикали. */
    private static final double VERTICAL_DRAG = 0.68D;

    /**
     * Сопротивление воздуха по горизонтали в полёте.
     * <p>
     * Важно именно вместе с {@link #VERTICAL_DRAG}: при слишком слабом трении
     * суммарный путь по горизонтали равен {@code v * VERTICAL_DRAG /
     * HORIZONTAL_DRAG}, и вылетевшая со скоростью 0.3 гильза улетела бы на
     * полтора блока — мимо ног стрелка. С такими значениями она уходит из-под
     * ствола, но остаётся рядом.
     */
    private static final double HORIZONTAL_DRAG = 0.72D;

    /**
     * Трение о поверхность. Сильнее воздушного: улегшаяся гильза быстро
     * останавливается, а не скользит через полкомнаты.
     */
    private static final double SURFACE_FRICTION = 0.52D;

    /**
     * Насколько гильза каждый тик надавливает в пол, когда уже лежит.
     * <p>
     * Нужно, чтобы {@code move()} постоянно чувствовал опору снизу: пока хоть
     * какое-то вертикальное движение блокируется, {@code Entity#onGround}
     * остаётся включённым, и сеть не шлёт пакетов телепорта. Само значение
     * маленькое, потому что оно целиком гасится столкновением — в пол гильза
     * не проваливается, а на убранный блок падает с нуля, без остаточной
     * скорости.
     */
    private static final double SURFACE_PRESS = 0.02D;

    /** Порог горизонтальной скорости, ниже которого гильза считается остановившейся. */
    private static final double REST_SPEED = 0.012D;

    /**
     * Насколько гильза проверяет опору под собой, в блоках.
     * <p>
     * Это замена {@code Entity#onGround}, которой раньше пользовался этот
     * класс. У {@code Entity#onGround} флаг обнуляется каждый раз, когда
     * вертикальная скорость не была заблокирована, то есть у скользящей гильзы
     * он мигал через тик, а мигание превращалось в череду пакетов телепорта.
     * Собственная проверка от скорости не зависит.
     */
    private static final double SUPPORT_SKIN = 0.02D;

    // === ОСЕДАНИЕ ===

    /**
     * Жёсткость пружины, которая кладёт гильзу плашмя.
     * <p>
     * Вместе с {@link #SETTLE_DAMPING} даёт два-три заметных колебания примерно
     * за полторы секунды: гильза падает на ребро, переворачивается через
     * горизонталь и ещё раз откатывается назад, прежде чем лечь. Слишком слабое
     * затухание растягивало бы это на три секунды и выглядело бы как
     * подпрыгивающий мячик, а не как гильза.
     */
    private static final double SETTLE_STIFFNESS = 0.22D;

    /** Затухание той же пружины. */
    private static final double SETTLE_DAMPING = 0.42D;

    /** Насколько сильно удар выбивает ось гильзы из полётного положения. */
    private static final double SETTLE_KICK = 2.0D;

    /** Минимальный толчок при ударе, даже по плашмя. */
    private static final double SETTLE_MIN_KICK = 5.0D;

    /** Затухание вращения вокруг собственной оси гильзы о поверхность. */
    private static final double ROLL_DAMPING = 0.55D;

    /** Насколько сползание доворачивает лежащую гильзу вокруг вертикали. */
    private static final double SLIDE_YAW_TRACKING = 0.10D;

    /** Градусов доворачивания вокруг вертикали на один блок/тик сползания. */
    private static final double SLIDE_YAW_PER_SPEED = 26.0D;

    /** Порог угла и скорости, за которыми гильза считается осевшей. */
    private static final double REST_ANGLE_EPS = 0.5D;
    private static final double REST_RATE_EPS = 1.0D;

    /** Страховка: осаждение не может длиться бесконечно. */
    private static final int SETTLE_MAX_TICKS = 45;

    /**
     * Сколько тиков подряд опоры не должно быть, чтобы гильза снова полетела.
     * <p>
     * Гистерезис нужен из-за пиксельных неровностей: одиночный тик без опоры
     * посреди лежания иначе вернул бы гильзу в полёт, и поза дёрнулась бы на
     * направление скорости — то есть почти вертикально вниз.
     */
    private static final int UNSUPPORTED_TICKS = 4;

    /**
     * Сколько тиков после появления касание поверхности ещё не считается ударом.
     * <p>
     * Гильза появляется вплотную к стволу и к игроку, то есть заведомо внутри
     * объёма, где что-то может быть: стена, в которую упёрся стрелок, блок под
     * ногами при выстреле вниз. Без этой паузы щелчок проигрывался бы в первый
     * же тик жизни, ещё на выходе из ствола.
     */
    private static final int SPAWN_GRACE_TICKS = 2;

    /**
     * Максимальный сдвиг гильзы за тик, в блоках. Всё, что длиннее, — не
     * сдвиг, а пакет телепорта, и интерполировать по нему нечего.
     * <p>
     * Порог заведомо выше предельной скорости гильзы (около 0.4 блока/тик) и
     * ниже половины блока: настоящий сдвиг никогда в него не попадёт, а
     * телепорт всегда.
     */
    private static final double MAX_STEP = 2.0D;

    /**
     * Процент возврата скорости при ударе гильзы о гильзу. Латунь не резиновая:
     * при единице столкновения почти наверху пара уходит в бесконечные прыжки,
     * потому что обе гильзы каждый тик снова входят в контакт.
     */
    private static final double CASING_RESTITUTION = 0.35D;

    /**
     * Градусов собственного вращения за тик на один блок/тик горизонтальной
     * скорости. Подобрано так, чтобы на типичной скорости вылета гильза
     * делала примерно один оборот за треть секунды — как настоящая.
     */
    private static final double SPIN_PER_SPEED = 110.0D;

    private static final int STATE_FLIGHT = 0;
    private static final int STATE_SETTLE = 1;
    private static final int STATE_REST = 2;

    // === СИНХРОНИЗАЦИЯ ===

    private static final EntityDataAccessor<Integer> SHOT_TICK =
            SynchedEntityData.defineId(GilseEntity.class, EntityDataSerializers.INT);

    /**
     * Поза гильзы едет через {@code SynchedEntityData}, а не в пакете поворота:
     * тот квантуется до байта (шаг 1.4 градуса) и шлётся раз в
     * {@code updateInterval} тиков. Значения выставлены сервером до
     * {@code addFreshEntity} и уходят вместе с пакетом появления, поэтому
     * первый же кадр показывает гильзу уже в той позе, в какой она вылетела.
     */
    private static final EntityDataAccessor<Float> POSE_YAW =
            SynchedEntityData.defineId(GilseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> POSE_PITCH =
            SynchedEntityData.defineId(GilseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> POSE_ROLL =
            SynchedEntityData.defineId(GilseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(GilseEntity.class, EntityDataSerializers.INT);

    // === СОСТОЯНИЕ ===

    /** Ось гильзы в градусах: поворот вокруг вертикали. */
    public float yawDeg;
    /** Ось гильзы в градусах: наклон вверх-вниз. */
    public float pitchDeg;
    /** Собственное вращение вокруг оси гильзы. */
    public float rollDeg;

    private float prevYawDeg;
    private float prevPitchDeg;
    private float prevRollDeg;

    private float pitchRate;
    private float rollRate;
    private float yawRate;

    private int state = STATE_FLIGHT;
    private int settleTicks;
    private int unsupportedTicks;
    private int shotTick;
    private int age;

    /**
     * Предыдущая серверная позиция — для сглаживания отрисовки.
     * <p>
     * Встроенная интерполяция уровня бесполезна: {@code ClientLevel#tickNonPassenger}
     * перед тиком вызывает {@code setOldPosAndRot()}, поэтому {@code xOld} на
     * клиенте всегда равен текущей позиции и подстановка в рендер не сглаживает
     * ничего. Позиция гильзы приходит с сервера дискретно, а её собственную
     * физику клиент больше не считает, поэтому без этого сдвига гильза шла бы
     * ступеньками по 20 раз в секунду.
     */
    private double serverPrevX;
    private double serverPrevY;
    private double serverPrevZ;

    /**
     * Счётчик пакетов позиции, принятых клиентом, и его значение на прошлом
     * клиентском тике. Нужен, чтобы отличать «сервер двигает гильзу» от
     * «гильза стоит и пакетов не приходит»: во втором случае интерполировать
     * больше не к чему и якорь надо прижать к текущей точке. Подробнее — в
     * {@link #expireRenderAnchor()}.
     */
    private long packetsSeen;
    private long packetsSeenAtLastTick;

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

        // Поза задаётся здесь, до появления в мире: первый же кадр на экране
        // должен показать гильзу уже летящую боком из казённика, а не стоящую
        // произвольно и не доворачивающуюся на месте. Считаем по собственной
        // скорости вылета, а не по повороту стрелка: гильза вылетает вбок, и
        // поворот игрока тут ни при чём.
        faceVelocity(velocity);
    }

    // === ПОЗА ===

    public float getRenderYaw(float partialTick) {
        return Mth.lerp(partialTick, this.prevYawDeg, this.yawDeg);
    }

    public float getRenderPitch(float partialTick) {
        return Mth.lerp(partialTick, this.prevPitchDeg, this.pitchDeg);
    }

    public float getRenderRoll(float partialTick) {
        return Mth.lerp(partialTick, this.prevRollDeg, this.rollDeg);
    }

    /**
     * Разворачивает гильзу вдоль её скорости и публикует позу клиентам.
     * <p>
     * Зовётся только на вылете. Дальше в полёте позу обновляет
     * {@link #integrateFlightPose()}, а на поверхности — осаждение, и обе
     * фазы идут от текущего значения, поэтому переход между ними
     * непрерывен: гильза нигде не перескакивает из одного угла в другой.
     */
    private void faceVelocity(Vec3 velocity) {
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizontal < 1.0E-6D && Math.abs(velocity.y) < 1.0E-6D) {
            return;
        }

        this.yawDeg = (float) (Math.atan2(velocity.x, velocity.z) * (180.0D / Math.PI));
        this.pitchDeg = (float) (Math.atan2(velocity.y, horizontal) * (180.0D / Math.PI));

        // Небольшой начальный крен вокруг оси: гильза вылетает крутясь, и
        // жёстко нулевой спин выглядел бы как подшитый к вилке цилиндр.
        this.rollRate = (float) (horizontal * SPIN_PER_SPEED);
        this.rollDeg = Mth.wrapDegrees(this.rollDeg + this.rollRate);

        publishPose();
    }

    /** Поза в полёте: ось следует за скоростью, спин крутится ровно. */
    private void integrateFlightPose(Vec3 velocity) {
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizontal > 1.0E-6D || Math.abs(velocity.y) > 1.0E-6D) {
            this.yawDeg = (float) (Math.atan2(velocity.x, velocity.z) * (180.0D / Math.PI));
            this.pitchDeg = (float) (Math.atan2(velocity.y, horizontal) * (180.0D / Math.PI));
        }

        this.rollRate = (float) (horizontal * SPIN_PER_SPEED);
        this.rollDeg = Mth.wrapDegrees(this.rollDeg + this.rollRate);
    }

    /**
     * Осаждение: гильза ложится плашмя и качается по затухающей пружине.
     * <p>
     * Пружина именно на {@code pitch}, а не мгновенная установка в ноль: так
     * читается сам удар — гильза падает на ребро, отскакивает от пола
     * половиной амплитуды и ещё два раза качается, прежде чем лечь. Раньше
     * поза просто замирала в полётном положении, и гильза на поверхности
     * выглядела омертвевшей.
     */
    private void integrateSettlePose(double horizontalSpeed) {
        this.settleTicks++;

        // Ось стремится к горизонтали и гасится о пол.
        this.pitchRate += (float) ((0.0D - this.pitchDeg) * SETTLE_STIFFNESS);
        this.pitchRate -= this.pitchRate * (float) SETTLE_DAMPING;
        this.pitchDeg = Mth.wrapDegrees(this.pitchDeg + this.pitchRate);

        // Вращение вокруг собственной оси просто сходит на нет.
        this.rollRate -= this.rollRate * (float) ROLL_DAMPING;
        this.rollDeg = Mth.wrapDegrees(this.rollDeg + this.rollRate);

        // Сползание доворачивает гильзу вокруг вертикали — она же огибает
        // неровности пола, а не едет по прямой.
        float targetYawRate = (float) (horizontalSpeed * SLIDE_YAW_PER_SPEED);
        this.yawRate += (targetYawRate - this.yawRate) * (float) SLIDE_YAW_TRACKING;
        this.yawDeg = Mth.wrapDegrees(this.yawDeg + this.yawRate);

        if (this.settleTicks >= SETTLE_MAX_TICKS || isSettled(horizontalSpeed)) {
            this.state = STATE_REST;
            this.pitchRate = 0.0F;
            this.rollRate = 0.0F;
            this.yawRate = 0.0F;
            this.pitchDeg = 0.0F;
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    private boolean isSettled(double horizontalSpeed) {
        return Math.abs(this.pitchDeg) < REST_ANGLE_EPS
                && Math.abs(this.pitchRate) < REST_RATE_EPS
                && Math.abs(this.rollRate) < REST_RATE_EPS
                && Math.abs(this.yawRate) < REST_RATE_EPS
                && horizontalSpeed < REST_SPEED;
    }

    /**
     * Первый удар о поверхность: гильза переходит в осаждение.
     * <p>
     * Толчок по скорости удара обязателен. Без него гильза, упавшая плашмя,
     * осела бы за один тик и никакого качания не читалось бы — а именно
     * качание и делает удар убедительным.
     *
     * @param playSound играть ли щелчок: у точки вылета касание — это ещё
     *                  не удар, а стена, в которую упёрся стрелок
     */
    private void startSettling(double impactSpeed, boolean playSound) {
        this.state = STATE_SETTLE;
        this.settleTicks = 0;
        this.unsupportedTicks = 0;

        float kick = (float) Math.max(SETTLE_MIN_KICK, impactSpeed * SETTLE_KICK);
        if (this.random.nextBoolean()) {
            kick = -kick;
        }
        this.pitchRate += kick;

        // Спин не обрывается намертво, а сходит на нет о пол.
        float spin = (float) Math.max(Math.abs(this.rollRate), impactSpeed * 40.0D);
        if (this.random.nextBoolean()) {
            spin = -spin;
        }
        this.rollRate += spin;
        this.yawRate = 0.0F;

        if (playSound) {
            playImpactSound(impactSpeed);
        }
    }

    private void publishPose() {
        this.prevYawDeg = this.yawDeg;
        this.prevPitchDeg = this.pitchDeg;
        this.prevRollDeg = this.rollDeg;

        this.setYRot(this.yawDeg);
        this.setXRot(this.pitchDeg);

        this.entityData.set(POSE_YAW, this.yawDeg);
        this.entityData.set(POSE_PITCH, this.pitchDeg);
        this.entityData.set(POSE_ROLL, this.rollDeg);
        this.entityData.set(STATE, this.state);
    }

    private void readPoseFromEntityData() {
        this.yawDeg = this.entityData.get(POSE_YAW);
        this.pitchDeg = this.entityData.get(POSE_PITCH);
        this.rollDeg = this.entityData.get(POSE_ROLL);
        this.state = this.entityData.get(STATE);
    }

    // === СЕТКА ===

    /**
     * Прижимает рендерный якорь к текущей точке: интерполировать больше не от
     * чего.
     * <p>
     * Зовётся при появлении сущности, при скачке больше тикового сдвига и
     * когда сервер перестал двигать гильзу. Без последнего случая якорь остался
     * бы на точке, с которой гильза упала, и рендер тянул бы её туда вечно.
     */
    private void snapRenderAnchor() {
        this.serverPrevX = this.getX();
        this.serverPrevY = this.getY();
        this.serverPrevZ = this.getZ();
    }

    /**
     * Сбрасывает якорь, если сервер перестал двигать гильзу.
     * <p>
     * Пакеты позиции шлются не каждый тик, а только если смещение превысило
     * 1/4096 блока: {@code ServerEntity} сравнивает его с накопленным от
     * предыдущего пакета. Улегшаяся гильза под этим порогом, и пакеты
     * перестают приходить совсем — а якорь остался бы на точке, с которой
     * гильза упала на пол. Рендер интерполировал бы вечно оттуда, то есть
     * гильза навечно висела бы над поверхностью на высоте последнего
     * шага падения. Именно это и читалось как «зависает».
     * <p>
     * Отличать «пакет пришёл» от «пакета не было» можно по счётчику: пакеты
     * разбираются до тика сущностей ({@code MultiPlayerGameMode#tick} вызывает
     * разбор пакетов, {@code Minecraft#tick} — {@code level.tickEntities()} уже
     * после), поэтому к моменту нашего {@code tick()} счётчик за этот тик уже
     * изменился, если пакет был.
     */
    private void expireRenderAnchor() {
        if (this.packetsSeen == this.packetsSeenAtLastTick) {
            this.snapRenderAnchor();
        }
        this.packetsSeenAtLastTick = this.packetsSeen;
    }

    /**
     * Смещение отрисовки: насколько модель сместить, чтобы попасть в
     * сглаженную точку вместо дискретной серверной.
     */
    public Vec3 renderOffset(float partialTick) {
        if (!this.level().isClientSide) {
            return Vec3.ZERO;
        }
        return new Vec3(
                Mth.lerp(partialTick, this.serverPrevX, this.getX()) - this.getX(),
                Mth.lerp(partialTick, this.serverPrevY, this.getY()) - this.getY(),
                Mth.lerp(partialTick, this.serverPrevZ, this.getZ()) - this.getZ()
        );
    }

    // === ОПОРА И СТОЛКНОВЕНИЯ ===

    /** Есть ли под гильзой блок, на который она может опереться. */
    private boolean supportedBelow() {
        AABB box = this.getBoundingBox();
        // Застряв внутри блока опорой считать нечего: иначе гильза замерла бы в
        // стене. Собственная проверка не срабатывает — и move() выталкивает её
        // ближайшее свободное место.
        if (hasBlockCollision(box)) {
            return false;
        }
        return hasBlockCollision(box.move(0.0D, -SUPPORT_SKIN, 0.0D));
    }

    private boolean hasBlockCollision(AABB box) {
        // getBlockCollisions отдаёт Iterable, а не список: emptiness проверяем
        // через итератор, лишнюю коллекцию ради одного флага не заводим.
        return this.level().getBlockCollisions(this, box).iterator().hasNext();
    }

    /** Проверяет блоки по хитбоксу, сдвинутому на {@code push}: не впихнёт ли разведение в стену. */
    private boolean hitsBlock(AABB box, Vec3 push) {
        return hasBlockCollision(box.move(push));
    }

    /**
     * Проверяет, дотянется ли гильза до блока за этот тик, и где именно.
     * <p>
     * У {@code move()} нормали нет — он лишь гасит компоненты движения, — а
     * угадывать её по тому, какая из них изменилась, ненадёжно: при скольжении
     * вдоль стены меняются две оси сразу, и нормаль выходила случайной. Луч
     * даёт и нормаль, и сам факт касания.
     */
    private BlockHitResult traceAhead(Vec3 motion) {
        if (motion.lengthSqr() < 1.0E-8D) {
            return null;
        }
        Vec3 from = this.position();
        BlockHitResult hit = this.level().clip(new ClipContext(from, from.add(motion),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SHOT_TICK, 0);
        builder.define(POSE_YAW, 0.0F);
        builder.define(POSE_PITCH, 0.0F);
        builder.define(POSE_ROLL, 0.0F);
        builder.define(STATE, STATE_FLIGHT);
    }

    @Override
    public void tick() {
        // Предыдущие углы запоминаем до того, как посчитаем новые: между ними
        // рендер интерполирует, иначе гильза доворачивалась бы рывками по тику.
        this.prevYawDeg = this.yawDeg;
        this.prevPitchDeg = this.pitchDeg;
        this.prevRollDeg = this.rollDeg;

        // Физику и позу считает только сервер. Раньше клиент прогонял тот же
        // tick(), из-за чего позиция получалась дважды: локально и ещё раз из
        // authoritative-пакетов. Клиентские столкновения гильз между собой при
        // этом не считались вовсе, так что клиент показывал пересекающиеся
        // гильзы и провалившиеся в стены.
        if (this.level().isClientSide) {
            // Позу клиент только читает: её публикует сервер, и больше её никто
            // не пересчитывает.
            readPoseFromEntityData();
            expireRenderAnchor();
            return;
        }

        if (++this.age >= LIFETIME_TICKS) {
            this.discard();
            return;
        }

        super.tick();

        // Опора решает всё: и режим, и то, останется ли гильза на месте.
        boolean supported = supportedBelow();
        Vec3 motion = this.getDeltaMovement();

        // Шаг полёта: тот же вектор пойдёт и в луч, и в move(). Иначе удар и
        // перемещение считались бы по разным скоростям, и щелчок с осадением
        // срабатывали бы не в том тике, в котором гильза реально коснулась пола.
        Vec3 flightStep = new Vec3(
                motion.x * HORIZONTAL_DRAG,
                motion.y * VERTICAL_DRAG - GRAVITY,
                motion.z * HORIZONTAL_DRAG
        );

        // Переходы между режимами — до движения, чтобы удар, решивший начать
        // оседание, и само оседание оказались в одном тике.
        if (this.state == STATE_FLIGHT) {
            BlockHitResult contact = supported ? null : traceAhead(flightStep);
            boolean landed = contact != null && contact.getDirection() == Direction.UP;

            if (supported || landed) {
                // Первые тики после появления — это ещё выход из ствола, а не
                // удар: осаждение начинаем сразу, чтобы поза не дёрнулась на
                // вертикальную скорость, но щелчок ещё не играем.
                startSettling(Math.max(flightStep.length(), Math.abs(flightStep.y)),
                        this.age > SPAWN_GRACE_TICKS);
            }
        } else if (supported) {
            this.unsupportedTicks = 0;
        } else if (++this.unsupportedTicks > UNSUPPORTED_TICKS) {
            // Опоры нет несколько тиков подряд: либо съехали с края, либо убрали
            // блок. Гистерезис обязателен — одиночный промах зонда посреди
            // лежания вернул бы гильзу в полёт, и поза прыгнула бы на
            // направление скорости, то есть почти вертикально вниз.
            this.state = STATE_FLIGHT;
            this.settleTicks = 0;
            this.unsupportedTicks = 0;
            this.pitchRate = 0.0F;
            this.yawRate = 0.0F;
        }

        Vec3 step = this.state == STATE_FLIGHT ? flightStep : surfaceStep(motion);

        this.setDeltaMovement(step);
        this.move(MoverType.SELF, step);

        // Флаг Entity#onGround сюда не пишем: его и так выставил move() по
        // факту блокировки, а любая запись поверх него рискует раскачать флаг
        // между тиками. ServerEntity шлёт полный пакет телепорта каждый раз,
        // когда onGround у сущности меняется, то есть такая запись — прямой
        // источник рывков. Пока вертикальная скорость давит гильзу в пол (см.
        // SURFACE_PRESS), флаг стоит ровно столько же, сколько гильза лежит.

        // Гильзы разводятся друг от друга последними: к этому моменту обе уже
        // разошлись блоками и move() успел обработать столкновения с миром.
        collideWithOtherCasings();

        // Позу считаем по тому, чем гильза реально проехала, а не по задуманной
        // скорости: move() погасил заблокированные оси, и ось гильзы должна
        // смотреть туда, куда гильза едет на самом деле, — иначе у стены цилиндр
        // втыкался бы в неё боком.
        Vec3 actual = this.getDeltaMovement();
        if (this.state == STATE_FLIGHT) {
            integrateFlightPose(actual);
        } else {
            double slideSpeed = Math.sqrt(actual.x * actual.x + actual.z * actual.z);
            if (this.state == STATE_SETTLE) {
                integrateSettlePose(slideSpeed);
            } else {
                // Лежит намертво: обнуляем и остаточное давление в пол, иначе
                // сеть получала бы пакет скорости каждый тик без нужды.
                this.setDeltaMovement(Vec3.ZERO);
            }
        }

        publishPose();

        // Просевшая гильза не должна раз в тик будить сеть пакетом позиции.
        if (this.getDeltaMovement().lengthSqr() > 1.0E-5D) {
            this.hasImpulse = true;
        }
    }

    /**
     * Шаг по поверхности: сползание с трением и постоянное давление в пол.
     * <p>
     * Давление идёт и в состоянии покоя — именно оно держит {@code onGround}
     * включённым, а через него и определяет, полетит ли {@code ServerEntity}
     * пакет телепорта.
     */
    private Vec3 surfaceStep(Vec3 motion) {
        double friction = this.state == STATE_SETTLE ? SURFACE_FRICTION : 0.0D;
        return new Vec3(motion.x * friction, -SURFACE_PRESS, motion.z * friction);
    }

    /**
     * Щелчок гильзы при касании блока.
     * <p>
     * Только на сервере: {@code level().playSound} сам рассылает звук всем
     * nearby-игрокам, а на клиенте тот же вызов проиграл бы его повторно у
     * самого стрелка — получился бы двойной щелчок.
     * <p>
     * Сила удара берётся из скорости <em>до</em> move(): внутри {@code move()}
     * вызывается {@code checkFallDamage}, который обнуляет {@code fallDistance},
     * и к моменту проверки он уже всегда ноль.
     */
    private void playImpactSound(double impactSpeed) {
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

        // Четверть от полной. При автоматическом огне гильз сыплется сразу по
        // несколько штук, и на полной громкости щелчки сливались в непрерывный
        // треск, в котором отдельную гильзу уже не разобрать; вдвое тише всё
        // равно оставалось слышно, как Machine Gun'овский лайк.
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.GILSE_LIGHT.get(), SoundSource.PLAYERS, volume * 0.25F, pitch);
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
     * Разводим именно когда хитбоксы реально пересекаются, то есть по
     * {@code getBbWidth()}. Прежняя константа 0.09 была меньше половины
     * хитбокса, и настоящие пересечения не разрешались вовсе — гильзы
     * проходили друг сквозь друга.
     * <p>
     * Разведение позиций проверяется на блоки. Раньше сдвиг применялся без
     * проверки, и зажатая между стеной и соседней гильзой гильза уезжала в
     * стену: {@code move()} считает столкновения от текущего хитбокса, а из
     * блока, в который он уже утоплен, вытолкнуть нельзя — там всегда ноль.
     */
    private void collideWithOtherCasings() {
        double minDist = this.getBbWidth();

        List<GilseEntity> others = new ArrayList<>();
        this.level().getEntities(EntityTypeTest.forClass(GilseEntity.class),
                this.getBoundingBox().inflate(minDist),
                (other) -> other != this, others);

        for (GilseEntity other : others) {
            Vec3 diff = this.position().subtract(other.position());
            double distSqr = diff.lengthSqr();

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
            Vec3 push = normal.scale(overlap);

            if (!hitsBlock(this.getBoundingBox(), push)) {
                this.setPos(this.getX() + push.x, this.getY() + push.y, this.getZ() + push.z);
            }

            Vec3 pushBack = push.scale(-1.0D);
            if (!hitsBlock(other.getBoundingBox(), pushBack)) {
                other.setPos(other.getX() + pushBack.x,
                        other.getY() + pushBack.y,
                        other.getZ() + pushBack.z);
            }

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

            // Случайный контакт выбивает позу: обе гильзы снова оседают.
            other.wakeUpFromRest(approachSpeed);
            this.wakeUpFromRest(approachSpeed);

            this.hasImpulse = true;
            other.hasImpulse = true;
        }
    }

    /**
     * Стык с другой гильзой снова запускает осаждение: лежащая гильза от него
     * отскакивает и докатывается, а не стоит столбом.
     *
     * @param approachSpeed скорость сближения вдоль нормали контакта; отрицательная
     *                      при наезде, то есть тем быстрее, чем сильнее удар
     */
    private void wakeUpFromRest(double approachSpeed) {
        if (this.state == STATE_REST) {
            this.state = STATE_SETTLE;
            this.settleTicks = 0;
        }
        if (this.state != STATE_SETTLE) {
            return;
        }
        float kick = (float) Math.max(0.0D, -approachSpeed * SETTLE_KICK);
        this.pitchRate += this.random.nextBoolean() ? kick : -kick;
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
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (this.level().isClientSide) {
            double dx = x - this.getX();
            double dy = y - this.getY();
            double dz = z - this.getZ();

            if (dx * dx + dy * dy + dz * dz > MAX_STEP * MAX_STEP) {
                // Слишком большой скачок — это пакет телепорта, а не сдвиг за
                // тик: предыдущей точки в нём по сути нет, и интерполировать
                // не от чего. Якорь прижимаем к новой позиции, иначе рендер
                // протянул бы гильзу через полкарты рывком вместо того, чтобы
                // просто переставить её на новое место.
                super.lerpTo(x, y, z, yRot, xRot, steps);
                this.packetsSeen++;
                this.snapRenderAnchor();
                return;
            }

            // Обычный пакет сдвига: старая точка становится якорем интерполяции.
            this.serverPrevX = this.getX();
            this.serverPrevY = this.getY();
            this.serverPrevZ = this.getZ();
            this.packetsSeen++;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);

        // Сервер прислал позу в пакете появления: она лежит в
        // SynchedEntityData и уходит вместе с ним. Читаем её сразу — иначе поля
        // остались бы нулевыми, предыдущие углы прижались бы к нулю, и первый
        // кадр интерполировал бы поворот от нуля. Гильза видимо доворачивалась
        // бы на месте сразу после появления.
        readPoseFromEntityData();
        this.prevYawDeg = this.yawDeg;
        this.prevPitchDeg = this.pitchDeg;
        this.prevRollDeg = this.rollDeg;

        this.packetsSeen++;
        this.snapRenderAnchor();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("GilseAge", this.age);
        tag.putInt("GilseShotTick", this.shotTick);
        tag.putUUID("GilseShooter", this.shooterUUID);
        tag.putInt("GilseState", this.state);
        tag.putInt("GilseSettleTicks", this.settleTicks);
        tag.putInt("GilseUnsupported", this.unsupportedTicks);
        tag.putFloat("GilseYaw", this.yawDeg);
        tag.putFloat("GilsePitch", this.pitchDeg);
        tag.putFloat("GilseRoll", this.rollDeg);
        tag.putFloat("GilsePitchRate", this.pitchRate);
        tag.putFloat("GilseRollRate", this.rollRate);
        tag.putFloat("GilseYawRate", this.yawRate);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt("GilseAge");
        this.shotTick = tag.getInt("GilseShotTick");
        if (tag.hasUUID("GilseShooter")) {
            this.shooterUUID = tag.getUUID("GilseShooter");
        }
        // Осаждение переживает перезагрузку мира, иначе первое же сохранение
        // подбросило бы улегшуюся гильзу на пол-блока вверх.
        this.state = tag.contains("GilseState") ? tag.getInt("GilseState") : STATE_FLIGHT;
        this.settleTicks = tag.getInt("GilseSettleTicks");
        this.unsupportedTicks = tag.getInt("GilseUnsupported");
        this.yawDeg = tag.getFloat("GilseYaw");
        this.pitchDeg = tag.getFloat("GilsePitch");
        this.rollDeg = tag.getFloat("GilseRoll");
        this.pitchRate = tag.getFloat("GilsePitchRate");
        this.rollRate = tag.getFloat("GilseRollRate");
        this.yawRate = tag.getFloat("GilseYawRate");

        this.prevYawDeg = this.yawDeg;
        this.prevPitchDeg = this.pitchDeg;
        this.prevRollDeg = this.rollDeg;

        publishPose();
        snapRenderAnchor();
    }
}
