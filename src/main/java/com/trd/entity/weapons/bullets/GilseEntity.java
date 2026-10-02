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

    /**
     * Гравитация и трение подобраны под падение лёгкой латунной гильзы.
     * <p>
     * Раньше здесь стояло 0.12 и 0.3, и это давало падение заметно медленнее
     * любого другого предмета в игре: предельная вертикальная скорость
     * {@code GRAVITY / (1 - DRAG)} = 0.12 / 0.7 = 0.17 блока/тик, то есть
     * гильза с высоты трёх блоков летела больше секунды. Теперь предельная
     * скорость вдвое выше — 0.49 блока/тик, около 10 м/с, — и падение читается
     * как падение небольшого тела, а не как зависание в воздухе.
     */
    private static final double GRAVITY = 0.22D;

    /**
     * Трение о вертикальную скорость в полёте. Умеренное: и слишком слабое, и
     * слишком сильное трение уводит предельную скорость от нужной величины,
     * поэтому важно их соотношение, а не значение по отдельности.
     */
    private static final double DRAG = 0.55D;

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

    /**
     * Сколько тиков после появления касание поверхности ещё не считается
     * ударом — ни звука, ни отскока, ни переворота гильзы.
     * <p>
     * Гильза появляется в четверти блока от глаза и в упор к игроку, то есть
     * заведомо внутри объёма, где что-то может быть: стена, в которую
     * упёрся стрелок, блок под ногами при выстреле вниз. Без этой паузы щелчок
     * и отскок проигрывались в первый же тик жизни, ещё на выходе из ствола.
     * За два пропущенных тика гильза улетает примерно на блок, и всё, до чего
     * можно долететь за это время, — это реальное препятствие, а не точка
     * вылета.
     */
    private static final int SPAWN_GRACE_TICKS = 2;

    /**
     * Максимальный сдвиг гильзы за тик, в блоках. Всё, что длиннее, — не
     * полёт, а телепорт пакетами, и рендерный якорь надо сбрасывать на текущую
     * точку, иначе интерполяция протянется от старой позиции через полкарты.
     */
    private static final double MAX_STEP = 2.0D;

    /**
     * Насколько глубоко гильза проверяет опору под собой, в блоках.
     * <p>
     * Это замена {@code Entity#onGround}, которой раньше пользовался этот
     * класс, и причина главных бед гильзы. У {@code Entity#onGround} флаг
     * перезаписывается на каждом {@code move()} значением
     * {@code verticalCollisionBelow}, а оно истинно только когда вертикальная
     * скорость ненулевая. У лежащей гильзы вертикальная скорость как раз
     * обнуляется, поэтому флаг каждый тик сбрасывался, на следующем тике
     * применялась гравитация, и цикл повторялся: гильза то считалась лежащей,
     * то падающей. Побочный эффект был куда хуже: {@code ServerEntity} шлёт
     * пакет телепорта всякий раз, когда onGround у сущности меняется, то есть
     * мигающий флаг превращал полёт в череду телепортов, а рендерный якорь
     * клиента от них не обновлялся. Гильза дёргалась мелкими рывками вместо
     * плавного полёта. Собственная проверка опоры такой зависимости от
     * скорости не имеет.
     */
    private static final double GROUND_PROBE = 0.02D;

    private static final EntityDataAccessor<Integer> SHOT_TICK =
            SynchedEntityData.defineId(GilseEntity.class, EntityDataSerializers.INT);

    /**
     * Ориентация гильзы: yaw и pitch в градусах плюс собственное вращение
     * вокруг оси полёта.
     * <p>
     * Задаётся один раз — при вылете, ещё в конструкторе, то есть до
     * {@code addFreshEntity} и до первого кадра на экране. Дальше она не
     * трогается, пока гильза ни обо что не стукнётся: ни своя скорость, ни
     * гравитация её не двигают.
     * <p>
     * Раньше углы выводились из скорости каждый тик. Из-за этого падающая
     * гильза дёргала осью: на участках, где горизонтальная скорость близка к
     * нулю, pitch выходил около -90°, и цилиндр читался как воткнутый дном
     * в пол, будто у него центр тяжести внизу. Теперь в полёте углы стоят
     * смирно, а переворачивается гильза ровно один раз — при первом ударе.
     * <p>
     * Едут они через {@code SynchedEntityData}, а не в пакете поворота: тот
     * квантуется до байта ({@code yaw * 256 / 360}, шаг 1.4 градуса) и
     * шлётся раз в {@code updateInterval} тиков.
     */
    private static final EntityDataAccessor<Float> FLIGHT_YAW =
            SynchedEntityData.defineId(GilseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FLIGHT_PITCH =
            SynchedEntityData.defineId(GilseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FLIGHT_SPIN =
            SynchedEntityData.defineId(GilseEntity.class, EntityDataSerializers.FLOAT);

    /** Текущий yaw полёта в градусах. */
    public float flightYawDeg;
    /** Текущий pitch полёта в градусах. */
    public float flightPitchDeg;
    /** Собственное вращение вокруг оси полёта. */
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
     * Лежит ли гильза на опоре. Собственный флаг вместо
     * {@link Entity#onGround()}: см. {@link #GROUND_PROBE}.
     */
    private boolean grounded;

    /**
     * Предыдущая серверная позиция — для сглаживания отрисовки, ровно как у
     * пули в {@code TurretBulletEntity}.
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
     * Отскочила ли гильза от поверхности. Отскок и щелчок разрешены ровно
     * один раз: иначе на неровной поверхности гильза начинает клацать и
     * подпрыгивать по каждому бугорку.
     */
    private boolean bounced;

    /**
     * Переворачивали ли гильзу уже. Ориентация в полёте не меняется, и
     * переворот случается один раз — при первом ударе о блок или соседнюю
     * гильзу.
     */
    private boolean tumbled;

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

        // Направление задаётся здесь, до появления в мире: первый же кадр на
        // экране должен показать гильзу уже летящую боком из-под ствола, а не
        // стоящую произвольно. Считаем по собственной скорости вылета, а не по
        // повороту стрелка: гильза вылетает вбок, и поворот игрока тут ни при
        // чём.
        applyFlightAngle(velocity);

        this.serverPrevX = pos.x;
        this.serverPrevY = pos.y;
        this.serverPrevZ = pos.z;
    }

    /**
     * Ставит ориентацию гильзы по вектору скорости и публикует её клиентам.
     * <p>
     * Зовётся дважды за жизнь: на вылете и при первом ударе. Между ними
     * ориентация не меняется — ни своя скорость, ни гравитация её не трогают.
     */
    private void applyFlightAngle(Vec3 velocity) {
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        this.flightYawDeg = (float) (Math.atan2(velocity.x, velocity.z) * (180.0D / Math.PI));
        this.flightPitchDeg = (float) (Math.atan2(velocity.y, horizontal) * (180.0D / Math.PI));
        // Небольшой начальный крен вокруг оси: гильза вылетает крутясь, и
        // жёстко нулевой спин выглядел бы как подшитый к вилке цилиндр.
        this.flightSpinDeg = (float) (horizontal * SPIN_PER_SPEED);

        this.setYRot(this.flightYawDeg);
        this.setXRot(this.flightPitchDeg);

        // Предыдущие углы прижимаем к текущим, иначе рендер в первый кадр
        // интерполирует поворот от нуля и гильза доворачивается на месте.
        this.prevFlightYawDeg = this.flightYawDeg;
        this.prevFlightPitchDeg = this.flightPitchDeg;
        this.prevFlightSpinDeg = this.flightSpinDeg;

        this.entityData.set(FLIGHT_YAW, this.flightYawDeg);
        this.entityData.set(FLIGHT_PITCH, this.flightPitchDeg);
        this.entityData.set(FLIGHT_SPIN, this.flightSpinDeg);
    }

    /**
     * Удар переворачивает гильзу: дальше она летит уже новым углом, пока снова
     * не упрётся. Ровно один раз за жизнь.
     */
    private void tumbleOnImpact(Vec3 incoming) {
        if (incoming.lengthSqr() < 1.0E-8D) {
            return;
        }
        this.tumbled = true;
        applyFlightAngle(incoming);
    }

    /**
     * Разность двух последних координат, пришедших с сервера: на сколько
     * гильза сдвинулась за тик.
     */
    private Vec3 positionDelta() {
        return new Vec3(
                this.getX() - this.serverPrevX,
                this.getY() - this.serverPrevY,
                this.getZ() - this.serverPrevZ
        );
    }

    /**
     * Прижимает рендерный якорь к текущей точке, если гильза переместилась
     * слишком далеко для одного тика.
     * <p>
     * Так прилетает телепорт пакетами, а не {@code lerpTo}: в нём координаты
     * абсолютные и предыдущая точка не сохраняется. Якорь остался бы на старой
     * позиции, и интерполяция в рендере протянулась бы через полкарты —
     * гильза дёргалась бы мелкими рывками, вместо того чтобы просто переставить
     * себя на новое место.
     */
    private void snapRenderAnchorIfTeleported() {
        if (this.positionDelta().lengthSqr() > MAX_STEP * MAX_STEP) {
            this.serverPrevX = this.getX();
            this.serverPrevY = this.getY();
            this.serverPrevZ = this.getZ();
        }
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
        // Запоминаем предыдущие углы до того, как посчитаем новые: между ними
        // рендер интерполирует, иначе гильза доворачивалась бы рывками по тику.
        this.prevFlightYawDeg = this.flightYawDeg;
        this.prevFlightPitchDeg = this.flightPitchDeg;
        this.prevFlightSpinDeg = this.flightSpinDeg;

        // Физику и ориентацию считает только сервер, как у пули в
        // TurretBulletEntity. Раньше клиент прогонял тот же tick(), из-за чего
        // позиция получалась дважды: локально и ещё раз из authoritative-пакетов,
        // причём пакеты приходили раз в 4 тика (updateInterval). Клиентские
        // столкновения гильз между собой и отскок при этом не считались вовсе,
        // так что клиент показывал пересекающиеся гильзы и провалившиеся в
        // стены — то есть ровно то, чего не должно быть видно.
        if (this.level().isClientSide) {
            // Углы клиент только читает: их публикует сервер, и больше их
            // никто не пересчитывает.
            this.flightYawDeg = this.entityData.get(FLIGHT_YAW);
            this.flightPitchDeg = this.entityData.get(FLIGHT_PITCH);
            this.flightSpinDeg = this.entityData.get(FLIGHT_SPIN);
            snapRenderAnchorIfTeleported();
            return;
        }

        if (++this.age >= LIFETIME_TICKS) {
            this.discard();
            return;
        }

        super.tick();

        Vec3 motion = this.getDeltaMovement();

        if (this.grounded) {
            // Улеглась: ползём по земле и постепенно тормозим. Вертикальную
            // составляющую гасим полностью — прилипла к опоре.
            motion = new Vec3(motion.x * GROUND_DRAG, 0.0D, motion.z * GROUND_DRAG);
            if (motion.horizontalDistanceSqr() < RESTING_SPEED * RESTING_SPEED) {
                motion = Vec3.ZERO;
            }
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

        // Именно move() двигает сущность и разруливает столкновения с блоками.
        double yBefore = this.getY();
        this.move(MoverType.SELF, motion);
        double yAfter = this.getY();

        // Что именно заблокировало гильзу. move() гасит о препятствие
        // горизонтальные компоненты скорости, но вертикальную оставляет как
        // была, а о вертикальном ударе сообщает только сдвигом позиции.
        Vec3 afterMove = this.getDeltaMovement();
        boolean blockedDown = yAfter < yBefore - 1.0E-6D;
        boolean blockedUp = yAfter > yBefore + 1.0E-6D;
        boolean blockedX = Math.abs(afterMove.x - motion.x) > 1.0E-6D;
        boolean blockedZ = Math.abs(afterMove.z - motion.z) > 1.0E-6D;
        boolean touchedBlock = blockedDown || blockedUp || blockedX || blockedZ;

        // Опора под собой: собственная проверка вместо Entity#onGround, см.
        // GROUND_PROBE. Она работает и когда гильза ползёт по полу при
        // нулевой вертикальной скорости, и когда только что упала.
        //
        // «Летит вверх» опорой не считается: иначе отскок, который задаёт
        // положительную вертикальную скорость, тут же гасился бы следующим
        // же тиком, и гильза просто прилипала к полу без подскока.
        boolean wasGrounded = this.grounded;
        this.grounded = blockedDown || (supportedBelow() && motion.y <= 0.0D);

        // Флаг Entity#onGround сюда возвращаем намеренно. Он нужен не только
        // самому move(), но и ServerEntity: тот шлёт полный пакет телепорта
        // каждый раз, когда onGround у сущности меняется. У ползущей гильзы
        // вертикальная скорость обнулена, и Entity#onGround обнулялся тут же,
        // то есть флаг мигал через тик и на пакете, и на рендере — гильза
        // получала телепорт вместо сдвига и дёргалась мелкими рывками.
        this.setOnGround(this.grounded);

        // Первый удар о поверхность: щелчок, отскок и переворот гильзы. Всё
        // заперто на флаге bounced, так что за жизнь ровно один щелчок и один
        // отскок. Пауза после появления отсекает касание в точке вылета, а
        // порог по вертикали — случайное чиркнувшие о стену боком.
        boolean realImpact = Math.abs(motion.y) >= MIN_BOUNCE_SPEED;
        if (touchedBlock && !this.bounced && this.age > SPAWN_GRACE_TICKS && realImpact) {
            this.bounced = true;
            // Переворот идёт по скорости ДО отскока: та скорость, с которой
            // гильза пришла к поверхности, и есть то, как она на неё налетела.
            tumbleOnImpact(motion);
            playImpactSound(motion.y);
            bounce(motion, contactNormal(motion, blockedDown, blockedUp, blockedX, blockedZ));
        } else if (touchedBlock && !this.tumbled && motion.lengthSqr() > 1.0E-8D
                && this.age > SPAWN_GRACE_TICKS) {
            // Чиркнули боком, отскока не будет — но позу гильза всё равно
            // сбрасывает, удар есть.
            tumbleOnImpact(motion);
        }
        // Упала и не отскочила (например, порог отскока не пройден) — тоже
        // считаем приземлением, но уже без звука.
        if (!wasGrounded && this.grounded && !this.bounced) {
            this.bounced = true;
            tumbleOnImpact(motion);
            bounce(motion, new Vec3(0.0D, 1.0D, 0.0D));
        }

        // Гильзы разводятся друг от друга последними: к этому моменту обе уже
        // разошлись блоками и move() успел обработать столкновения с миром.
        collideWithOtherCasings();

        // Просевшая гильза не должна раз в тик будить сеть пакетом позиции.
        if (motion.lengthSqr() > 1.0E-5D) {
            this.hasImpulse = true;
        }
    }

    /** Есть ли под гильзой блок, на который она может опереться. */
    private boolean supportedBelow() {
        // getBlockCollisions отдаёт Iterable, а не список: emptiness проверяем
        // через итератор, лишнюю коллекцию ради одного флага не заводим.
        return this.level().getBlockCollisions(this,
                this.getBoundingBox().move(0.0D, -GROUND_PROBE, 0.0D)).iterator().hasNext();
    }

    /**
     * Проверяет блоки по хитбоксу, сдвинутому на {@code push}: не впихнёт ли
     * гильзу разведение в стену.
     */
    private boolean hitsBlock(net.minecraft.world.phys.AABB box, Vec3 push) {
        return this.level().getBlockCollisions(this, box.move(push)).iterator().hasNext();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (this.level().isClientSide) {
            this.serverPrevX = this.getX();
            this.serverPrevY = this.getY();
            this.serverPrevZ = this.getZ();
        }
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    @Override
    public void recreateFromPacket(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.serverPrevX = this.getX();
        this.serverPrevY = this.getY();
        this.serverPrevZ = this.getZ();
        this.prevFlightYawDeg = this.flightYawDeg;
        this.prevFlightPitchDeg = this.flightPitchDeg;
    }

    /** Предыдущая серверная позиция — читает рендерер для сглаживания. */
    public double getServerPrevX() {
        return this.serverPrevX;
    }

    public double getServerPrevY() {
        return this.serverPrevY;
    }

    public double getServerPrevZ() {
        return this.serverPrevZ;
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
     * было простое переворачивание вертикальной компоненты, из-за чего удар о
     * боковую стену гасил скорость совсем, а удар о пол не давал подброса под
     * углом.
     * <p>
     * Нормаль приходит снаружи: {@link #contactNormal} больше не угадывает её
     * по тому, какие компоненты скорости изменились, а берёт из факта
     * столкновения, разобранного в {@code tick()}.
     */
    private void bounce(Vec3 incoming, Vec3 normal) {
        // Упал так медленно, что отскок был бы незаметен: просто ложимся.
        if (incoming.length() < MIN_BOUNCE_SPEED) {
            return;
        }

        Vec3 reflected = incoming.subtract(normal.scale(2.0D * incoming.dot(normal)));
        this.setDeltaMovement(reflected.scale(BOUNCE));
        this.hasImpulse = true;
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
     * хитбокса, и настоящие пересечения (меньше 0.2) не разрешались вовсе —
     * гильзы проходили друг сквозь друга.
     * <p>
     * Разведение позиций проверяется на блоки. Раньше сдвиг применялся без
     * проверки, и зажатая между стеной и соседней гильзой гильза уезжала в
     * стену: {@code move()} считает столкновения от текущего хитбокса, а из
     * блока, в который он уже утоплен, вытолкнуть нельзя — там всегда ноль.
     * Дальше такая гильза оставалась в стене до конца жизни.
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

            // Удар о соседнюю гильзу — тоже удар: обе поворачиваются на
            // изменившейся скорости. Дальше до следующего касания поза держится.
            tumbleOnImpact(this.getDeltaMovement());
            tumbleOnImpact(other.getDeltaMovement());

            this.hasImpulse = true;
            other.hasImpulse = true;
        }
    }

    /**
     * Нормаль поверхности, в которую ударилась гильза.
     * <p>
     * У гранат она приходит из {@code BlockHitResult} вместе с лучом. У гильзы
     * своего луча нет — она просто перемещается вызовом {@code move()}, и тот
     * нормаль не отдаёт. Поэтому она собирается из флагов столкновения,
     * разобранных в {@code tick()}: пол, потолок и та горизонтальная ось, по
     * которой гасится компонента движения.
     */
    private static Vec3 contactNormal(Vec3 incoming, boolean blockedDown, boolean blockedUp,
                                     boolean blockedX, boolean blockedZ) {
        if (blockedDown) return new Vec3(0.0D, 1.0D, 0.0D);
        if (blockedUp) return new Vec3(0.0D, -1.0D, 0.0D);

        if (blockedX && !blockedZ) {
            return new Vec3(incoming.x >= 0.0D ? -1.0D : 1.0D, 0.0D, 0.0D);
        }
        if (blockedZ && !blockedX) {
            return new Vec3(0.0D, 0.0D, incoming.z >= 0.0D ? -1.0D : 1.0D);
        }

        // Две горизонтальные оси сразу — угловой удар о ребро. Берём нормаль по
        // преобладающей компоненте скорости: она указывает, с какой стороны
        // прилетел удар.
        double ax = Math.abs(incoming.x);
        double az = Math.abs(incoming.z);
        if (blockedX && ax >= az) {
            return new Vec3(incoming.x >= 0.0D ? -1.0D : 1.0D, 0.0D, 0.0D);
        }
        if (blockedZ) {
            return new Vec3(0.0D, 0.0D, incoming.z >= 0.0D ? -1.0D : 1.0D);
        }

        // Столкновения не было (погрешность) — отражаемся от пола.
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
        tag.putBoolean("GilseGrounded", this.grounded);
        tag.putBoolean("GilseTumbled", this.tumbled);
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
        // Опора переживает перезагрузку мира, иначе первое же сохранение
        // подбросило бы улегшуюся гильзу на пол-блока вверх.
        this.grounded = tag.getBoolean("GilseGrounded");
        this.tumbled = tag.getBoolean("GilseTumbled");
        this.flightSpinDeg = tag.getFloat("GilseSpin");
    }
}
