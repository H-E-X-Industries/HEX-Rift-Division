package com.trd.entity.weapons.bullets;

import com.trd.entity.ModEntities;
import com.trd.item.weapons.ammo.AmmoRegistry;
import com.trd.main.MainRegistry;
import com.trd.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Пупка 20 мм. Общая снасть для пушки и турелей: тип боезаряда приходит из
 * {@link AmmoRegistry} и определяет пробитие, урон и поведение при попадании.
 * <p>
 * Тип боезаряда и время полёта раздаются клиенту через {@link SynchedEntityData},
 * а не через дополнительные данные спавна — в NeoForge 1.21 механика
 * {@code IEntityAdditionalSpawnData} удалена, а синхронизированные данные
 * уходят вместе с пакетом появления сущности.
 * <p>
 * <b>Никакого GeckoLib.</b> Пуля не анимируется и не скелетная: у неё есть
 * только собственное вращение вокруг оси полёта, а его считает сервер и
 * раздаёт в {@code SPIN}. Раньше класс был {@code GeoEntity} с пустым
 * {@code registerControllers} и кешем анимаций — то есть тянул в себя всю
 * библиотеку ради ничего: пуля рисуется GemRender'ом напрямую из glTF
 * (см. {@code TurretBulletGltfRenderer}) и от геколибовского рендерера не
 * зависит вообще. Аналогично снят GeckoLib и с патронов: они стали обычными
 * предметами с двумерными спрайтами.
 */
public class TurretBulletEntity extends AbstractArrow {

    private static final EntityDataAccessor<String> AMMO_ID =
            SynchedEntityData.defineId(TurretBulletEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> AMMO_TYPE =
            SynchedEntityData.defineId(TurretBulletEntity.class, EntityDataSerializers.STRING);

    /**
     * Трассерный ли заряженный патрон, в синхронизированных данных.
     * <p>
     * Именно синхронизированные, а не обычное поле: модель и яркость пули
     * выбирает клиент, а у него своей копии предмета нет, и до появления пули в
     * мире смотреть не на что. Флаг ставится на сервере до
     * {@code addFreshEntity}, поэтому первый кадр уже рисует нужную модель, а не
     * обычную и не ничего.
     */
    private static final EntityDataAccessor<Boolean> AMMO_TRACER =
            SynchedEntityData.defineId(TurretBulletEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Integer> FLIGHT_TIME =
            SynchedEntityData.defineId(TurretBulletEntity.class, EntityDataSerializers.INT);

    /**
     * Собственное вращение пули вокруг оси полёта.
     * <p>
     * Именно синхронизированные данные, а не обычное поле: {@code spin} растёт
     * каждый тик, и без синхронизации клиент держал бы свой ноль, из-за чего
     * пуля на экране игрока не крутилась, пока на сервере вертелась. Пакет
     * появления несёт только yaw/pitch, поэтому первое значение нужно
     * выставить до {@code addFreshEntity}.
     */
    private static final EntityDataAccessor<Float> SPIN =
            SynchedEntityData.defineId(TurretBulletEntity.class, EntityDataSerializers.FLOAT);

    /**
     * Точное направление вылета, в градусах: те же два угла, что и у
     * {@link #alignToVelocity()}, но отдельными значениями.
     * <p>
     * Пакет появления несёт только yaw/pitch, а те квантуются до байта — шаг
     * получается 1.4 градуса. Для летящей пули, проходящей полсотни блоков за
     * секунду, этого хватает, но на первом кадре заметно: рендер успевает
     * взять направление из поворота, а уже со второго кадра берёт его из
     * разности координат, и пуля видимо доворачивается сразу после появления.
     * Точное направление вылета едет вместе с пакетом появления, поэтому
     * первый кадр совпадает со всеми остальными.
     */
    private static final EntityDataAccessor<Float> LAUNCH_YAW =
            SynchedEntityData.defineId(TurretBulletEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LAUNCH_PITCH =
            SynchedEntityData.defineId(TurretBulletEntity.class, EntityDataSerializers.FLOAT);

    public static final float BULLET_GRAVITY = 0.01F;
    public static final float AIR_RESISTANCE = 0.99F;
    public static final float MAX_FLIGHT_DISTANCE = 256.0F;

    /**
     * Предмет-подстановка вместо пустого pickup item. В 1.21 {@code AbstractArrow}
     * пишет его в NBT безусловно, и пустой стак ломает сохранение сущности.
     */
    private static final ItemStack PICKUP_PLACEHOLDER = new ItemStack(Items.ARROW);

    /** С какого тика у радио-боезаряда включается увеличенный хитбокс. */
    private static final int RADIO_FUSE_ACTIVATION = 5;

    /**
     * На сколько блоков нарисованная пуля может отставать от настоящей.
     * <p>
     * Без зажима отставание равно целому тику полёта, а пуля летит на шесть
     * блоков за тик.
     */
    private static final double MAX_RENDER_LAG = 0.25D;

    /** Запас к хитбоксу взрывателя, чтобы цель у края всё же срабатывала. */
    private static final double PROXIMITY_FUSE_MARGIN = 0.25D;

    /**
     * Радиус взрыва фугасного патрона, в блоках.
     * <p>
     * Вдвое меньше, чем у неразрушающего взрыва ракеты (4.0), и за счёт этого он
     * остаётся локальным: боезаряд не достаёт до цели за стеной тоньше блока,
     * а в комнате не выбивает всё, что в неё влезло. Ровно два — это ровно
     * предел, на котором ванильный взрыв ещё берёт «маленькую» частицу вместо
     * большой: {@code Explosion#finalizeExplosion} выбирает её по радиусу, и
     * при 2.0 нам ровно тот вид, что нужен пуле.
     */
    private static final float HE_EXPLOSION_RADIUS = 2.0F;

    /**
     * Урон взрыва фугасного патрона в эпицентре, с линейным спадом к нулю на
     * краю радиуса.
     * <p>
     * Раньше здесь стояло 16, и на одну лошадь уходило три-пять пуль. Причина
     была не в числе, а в точке подрыва: он срабатывал у самого края хитбокса, где
     * спад отнимает почти всё, — теперь пуля рвётся у центра, где урон полный.
     * Сверху число поднято с 16 до 26: без брони это переживает любой крупный
     * зверь с одного попадания, а полный комплект брони съедает 80% и оставляет
     * около пяти — то есть два-три патрона на броненосного противника.
     */
    private static final float HE_EXPLOSION_DAMAGE = 26.0F;

    /**
     * Насколько близко к центру цели должна долететь пуля, чтобы рвануть.
     * <p>
     * Полметра с запасом: радиус взрыва 2.0, и детонация в любой точке ближе
     * этого к центру бьёт цель полным уроном. Проверяется по пройденному за тик
     * отрезку, а не по текущей точке, — иначе на крупной сущности пуля,
     * пролетающая шесть блоков за тик, просто перескакивала бы через центр
     * между двумя положениями и рвалась позади цели.
     */
    private static final double HE_FUSE_RADIUS = 0.5D;
    private static final double HE_FUSE_RADIUS_SQR = HE_FUSE_RADIUS * HE_FUSE_RADIUS;

    /**
     * Сколько тиков фугасный может лететь после касания цели, не долетев до её
     * центра. Страховка: цель могла уйти, умереть от чего-то другого или оказаться
     * так, что пуля её прошла насквозь мимо центра, — тогда пуля рвётся на текущем
     * месте, а не улетает дальше мёртвым грузом.
     */
    private static final int HE_FUSE_TIMEOUT = 10;

    /**
     * Предыдущая позиция, пришедшая с сервера. Нужна клиенту для интерполяции
     * отрисовки: {@code xOld} затирается {@code setOldPosAndRot()} каждый тик,
     * поэтому собственный якорь приходится хранить отдельно.
     * <p>
     * Направление полёта тоже берётся из разности этих двух точек, а не из
     * {@code deltaMovement}: скорость в пакетах появления и движения клампится
     * до ±3.9 блока/тик, а пуля летит быстрее, и локальный вектор оказывался
     * неверным. Разность координат при этом точная — квантование по позиции
     * здесь не применяется.
     */
    private double serverPrevX;
    private double serverPrevY;
    private double serverPrevZ;

    /**
     * Счётчик пакетов позиции, принятых клиентом, и его значение на прошлом
     * клиентском тике.
     * <p>
     * Пакеты позиции шлются не каждый тик, а только если смещение превысило
     * 1/4096 блока: {@code ServerEntity} сравнивает его с накопленным от
     * предыдущего пакета. Пока пуля летит, порог всегда превышен, но как
     * только она остановилась — а при попадании в щит или в воду это случается
     * — пакеты перестают приходить, и якорь остался бы на точке, с которой
     * пуля встала. Рендер интерполировал бы вечно оттуда, то есть пуля
     * висела бы позади своей настоящей позиции.
     * <p>
     * Отличать «пакет пришёл» от «пакета не было» можно по счётчику: пакеты
     * разбираются до тика сущностей ({@code MultiPlayerGameMode#tick} вызывает
     * разбор пакетов, {@code Minecraft#tick} — {@code level.tickEntities()} уже
     * после), поэтому к моменту нашего {@code tick()} счётчик за этот тик уже
     * изменился, если пакет был.
     */
    private long packetsSeen;
    private long packetsSeenAtLastTick;

    private float baseDamage = 4.0f;
    private float baseSpeed = 3.0f;
    private AmmoType ammoType = AmmoType.NORMAL;
    private float initialSpeed = 0.0f;
    private Vec3 initialPosition = null;
    public float spin = 0;

    /** Цель последнего касания и таймер — только для радио-боезаряда. */
    private LivingEntity lastHitTarget = null;
    private int hitTickTimer = 0;

    /**
     * Цель, к центру которой фугасный летит перед подрывом, и сколько тиков он
     * уже летит.
     * <p>
     * Отдельные поля, а не общие с радио-боезарядом: у того своя логика —
     * он держится в воздухе и рвётся по таймеру после касания, здесь же пуля
     * продолжает лететь сквозь цель, пока не окажется у её центра.
     */
    private LivingEntity heTarget = null;
    private int heFuseTicks = 0;
    private Vec3 heClosestPoint = null;
    private double heClosestDistanceSqr = Double.MAX_VALUE;
    private static final double CENTER_DETONATE_RADIUS_SQR = 0.09D;

    /**
     * Сколько блоков пробила бронебойная пуля.
     * <p>
     * Считаются именно те блоки, сквозь которые она прошла насквозь: стекло и
     * лёд разбиваются и не тратят пробитие, потому что их и так не остаётся.
     */
    private int blocksPierced = 0;

    /**
     * Кого бронебойная пуля уже прошила.
     * <p>
     * Пуля летит на шесть блоков за тик, и её трассировка накрывает разом
     * несколько метров пути, так что без этого списка та же цель цеплялась бы
     * снова на следующем тике и урон падал бы не вдвое, а на каждый тик полёта.
     */
    private final List<Integer> piercedTargets = new ArrayList<>();

    /** Сколько блоков пробивает бронебойная пуля. */
    private static final int PIERCING_BLOCK_BUDGET = 2;

    /** Во столько раз падает урон по каждой следующей прошитой цели. */
    private static final float PIERCING_DAMAGE_FALLOFF = 2.0F;

    /** Столько целей бронебойная пуля снимает за один тик полёта. */
    private static final int PIERCING_MAX_SWEEP_HITS = 8;

    /** Насколько луч продолжается за прошитой целью. */
    private static final double PIERCING_TARGET_STEP = 0.15D;

    /**
 * Тип боезаряда: определяет поведение при попадании.
 * <p>
 * Фугасный добавлен последним, и это не случайно: {@link #ordinal()} задаёт
 * полосу в атласе текстур (см. {@code TurretBulletVariants}), а новое значение
 * в середине списка сдвинуло бы все полосы и перерисовало бы старые пули другой
 * картинкой. В конце списка старые ordinal'ы остаются на своих местах.
 */
    public enum AmmoType {
        NORMAL("normal"), PIERCING("piercing"), HOLLOW("hollow"), INCENDIARY("incendiary"), RADIO("radio"),
        HE("he");

        public final String id;

        AmmoType(String id) {
            this.id = id;
        }

        public static AmmoType fromString(String str) {
            for (AmmoType type : AmmoType.values()) {
                if (type.id.equals(str)) return type;
            }
            return NORMAL;
        }
    }

    public TurretBulletEntity(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public TurretBulletEntity(Level level, LivingEntity shooter) {
        // firedFromWeapon обязан быть null: AbstractArrow бросает IllegalArgumentException
        // на непустом стеке, а пустой ItemStack всё равно считается «непустым» аргументом.
        super(ModEntities.TURRET_BULLET.get(), shooter, level, PICKUP_PLACEHOLDER.copy(), null);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (getAmmoType() == AmmoType.RADIO && getFlightDuration() >= RADIO_FUSE_ACTIVATION) {
            // fixed() = абсолютные размеры (1.5 x 1.5 блока)
            return EntityDimensions.fixed(1.5F, 1.5F);
        }
        return super.getDimensions(pose);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AMMO_ID, "default");
        builder.define(AMMO_TYPE, "normal");
        builder.define(AMMO_TRACER, false);
        builder.define(FLIGHT_TIME, 0);
        builder.define(SPIN, 0.0F);
        builder.define(LAUNCH_YAW, 0.0F);
        builder.define(LAUNCH_PITCH, 0.0F);
    }

    /**
     * Разбирает id патрона на сердцевину и признак трассера.
     * <p>
     * Ид патронов теперь устроены как {@code turret_ammo_ap_tracer}, то есть
     * {@code <вид>_<тип>_<трассер>}, и разбирать их приходится по словам, а не
     * поиском подстроки: подстрока «ap» есть и в бронебойном, и в
     * {@code trd:turret_ammo}, а «piercing», по которому сортировали раньше, в
     * новых id не встречается вовсе. Слово «trasser» выброшено наравне с
     * «tracer» — в id оно именно в таком написании.
     */
    private static AmmoType bulletTypeOf(String ammoId) {
        String path = ammoId.contains(":") ? ammoId.substring(ammoId.indexOf(':') + 1) : ammoId;

        for (String word : path.split("_")) {
            switch (word) {
                case "ap", "piercing" -> {
                    return AmmoType.PIERCING;
                }
                case "hollow" -> {
                    return AmmoType.HOLLOW;
                }
                case "fire", "incendiary" -> {
                    return AmmoType.INCENDIARY;
                }
                case "radio" -> {
                    return AmmoType.RADIO;
                }
                case "he" -> {
                    return AmmoType.HE;
                }
                default -> {
                }
            }
        }
        return AmmoType.NORMAL;
    }

    public void setAmmoType(AmmoRegistry.AmmoType ammoType) {
        if (ammoType == null) return;
        this.baseDamage = ammoType.damage;
        this.baseSpeed = ammoType.speed;
        this.entityData.set(AMMO_ID, ammoType.id);

        // Трассер известен из предмета, а не выводится из id: он приезжает в
        // AmmoRegistry.AmmoType вместе с уроном и скоростью, и если бы
        // разбирался тут, то пустой боеприпас без предмета (все запасные
        // варианты в setAmmoType ниже) выглядел бы трассером по одному слову в
        // id и рисовался бы светящимся.
        this.entityData.set(AMMO_TRACER, ammoType.tracer);

        this.ammoType = bulletTypeOf(ammoType.id);
        this.entityData.set(AMMO_TYPE, this.ammoType.id);

        this.setBaseDamage(baseDamage);
    }

    public String getAmmoId() {
        return this.entityData.get(AMMO_ID);
    }

    public AmmoType getAmmoType() {
        return AmmoType.fromString(this.entityData.get(AMMO_TYPE));
    }

    /**
     * Трассерная ли пуля: от неё зависят модель и яркость отрисовки.
     *
     * @see TurretBulletEntity#AMMO_TRACER
     */
    public boolean isTracer() {
        return this.entityData.get(AMMO_TRACER);
    }

    public int getFlightDuration() {
        return this.entityData.get(FLIGHT_TIME);
    }

    private void setFlightDuration(int ticks) {
        this.entityData.set(FLIGHT_TIME, ticks);
    }

    public void setBallisticTrajectory(Vec3 startPos, Vec3 velocity) {
        this.setPos(startPos.x, startPos.y, startPos.z);
        this.initialSpeed = (float) velocity.length();
        this.initialPosition = startPos;
        setLaunchDirection(velocity);
    }

    public void shootBallisticFromRotation(LivingEntity shooter, float pitch, float yaw, float rollOffset,
                                           float speed, float divergence) {
        Vec3 lookDir = getLookDirFromRotation(pitch, yaw);
        if (divergence > 0) lookDir = addDispersion(lookDir, divergence);
        Vec3 velocity = lookDir.scale(speed);
        double startX = shooter.getX();
        double startY = shooter.getEyeY() - 0.1;
        double startZ = shooter.getZ();
        Vec3 offset = lookDir.normalize().scale(0.5);
        Vec3 startPos = new Vec3(startX, startY, startZ).add(offset);
        setBallisticTrajectory(startPos, velocity);
    }

    private static Vec3 getLookDirFromRotation(float pitch, float yaw) {
        float pitchRad = pitch * ((float) Math.PI / 180.0F);
        float yawRad = yaw * ((float) Math.PI / 180.0F);
        return new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad));
    }

    private Vec3 addDispersion(Vec3 baseDir, float divergence) {
        Vec3 normalized = baseDir.normalize();
        double dx = normalized.x + (this.random.nextGaussian() * divergence * 0.1);
        double dy = normalized.y + (this.random.nextGaussian() * divergence * 0.1);
        double dz = normalized.z + (this.random.nextGaussian() * divergence * 0.1);
        return new Vec3(dx, dy, dz).normalize().scale(baseDir.length());
    }

    @Override
    public void tick() {
        // Физику считает только сервер. Раньше клиент прогонял тот же tick(),
        // из-за чего позиция считалась дважды: пакетами ServerEntity и ещё раз
        // локально. Пакеты при этом клампят дельту до ±3.9 блока/тик, а пуля
        // быстрее, поэтому локальная траектория расходилась с серверной и
        // пуля дёргалась на экране. Теперь клиент только принимает готовые
        // координаты и сглаживает их в рендере (см. flightDirection).
        if (this.level().isClientSide) {
            // Позицию не двигаем и xOld не подменяем: рендер сглаживает её сам
            // по двум точкам (serverPrev и текущей). Подменять xOld здесь было
            // бессмысленно — тогда обе точки в смещении рендера совпадали и
            // интерполяция давала ноль, то есть пуля шла дискретно.
            expireRenderAnchor();
            return;
        }

        if (this.isRemoved() || this.inGround) {
            this.discard();
            return;
        }

        this.spin = (this.spin + 20.0F) % 360.0F;
        this.entityData.set(SPIN, this.spin);
        setFlightDuration(getFlightDuration() + 1);

        // Расширение хитбокса с сохранением центра (только у радио-боезаряда)
        if (getFlightDuration() == RADIO_FUSE_ACTIVATION && getAmmoType() == AmmoType.RADIO) {
            this.refreshDimensions();
        }

        if (initialPosition != null && this.position().distanceTo(initialPosition) > MAX_FLIGHT_DISTANCE) {
            this.discard();
            return;
        }

        if (this.tickCount > 200) {
            this.discard();
            return;
        }

        // Логика детонации (RADIO)
        if (getAmmoType() == AmmoType.RADIO && lastHitTarget != null) {
            this.hitTickTimer++;

            if (this.hitTickTimer >= 1 || !lastHitTarget.isAlive()) {
                applyRadioExplosion(this.position());
                this.discard();
                return;
            }
        }

        // Сохраняем позицию ДО движения
        Vec3 startPos = this.position();
        Vec3 motion = this.getDeltaMovement();
        Vec3 endPos = startPos.add(motion);

        HitResult hit = traceHit(startPos, endPos);
        if (hit.getType() != HitResult.Type.MISS) {
            if (getAmmoType() == AmmoType.PIERCING) {
                handlePiercingHits(startPos, endPos);
            } else {
                handleHitResult(hit);
            }
            if (this.isRemoved()) {
                return;
            }
        }

        // Фугасный после касания цели не рвётся сразу: он продолжает лететь и
        // детонирует у её центра. Раньше он подрывался там, где трассировка
        // впервые задела хитбокс, то есть у самого края, а урон падает линейно к
        // нулю на радиусе — на большой сущности от края до центра это почти
        // ничего, и выстрел оказывался без урона вовсе.
        //
        // Проверяется по всему пройденному за тик отрезку, а не по текущей
        // точке: пуля летит на шесть блоков за тик и на крупной цели может
        // проскочить мимо центра между двумя своими положениями.
        if (heTarget != null) {
            heFuseTicks++;

            Vec3 center = heTarget.getBoundingBox().getCenter();
            Vec3 closest = closestPointOnSegment(startPos, endPos, center);
            double distanceSqr = closest.distanceToSqr(center);

            // Запоминаем точку, где пуля подошла к центру ближе всего. Если в этом
            // тике оказалось дальше, чем было в прошлом, значит центр уже пройден
            // и рваться надо там, где было ближе, а не там, где пуля сейчас: иначе
            // на крупной сущности она улетела бы на десятки блоков и взорвалась в
            // пустоте.
            if (distanceSqr > heClosestDistanceSqr && heClosestDistanceSqr < Double.MAX_VALUE) {
                closest = heClosestPoint;
                distanceSqr = heClosestDistanceSqr;
            } else {
                heClosestPoint = closest;
                heClosestDistanceSqr = distanceSqr;
            }

            if (distanceSqr <= HE_FUSE_RADIUS_SQR
                    || !heTarget.isAlive()
                    || heFuseTicks >= HE_FUSE_TIMEOUT) {
                this.setPos(closest.x, closest.y, closest.z);
                detonate();
                return;
            }
        }

        // Движение (только один раз)
        this.setPos(endPos.x, endPos.y, endPos.z);
        motion = motion.scale(AIR_RESISTANCE).add(0.0, -BULLET_GRAVITY, 0.0);
        this.setDeltaMovement(motion);
        this.alignToVelocity();
    }

    private void applyRadioExplosion(Vec3 center) {
        playHitSound();

        if (!this.level().isClientSide) {
            AABB box = new AABB(
                    center.x - 1.8D, center.y - 1.8D, center.z - 1.8D,
                    center.x + 1.8D, center.y + 1.8D, center.z + 1.8D
            );

            List<Entity> entities = this.level().getEntities(this, box, e ->
                    e instanceof LivingEntity living && living.isAlive() && living != this.getOwner()
            );

            for (Entity e : entities) {
                LivingEntity living = (LivingEntity) e;
                double distSqr = living.distanceToSqr(center);

                if (distSqr > (1.8D * 1.8D)) continue;

                double dist = Math.sqrt(distSqr);
                float falloff = (float) (1.0 - (dist / 1.8D) * 0.7F);
                float hollowDamage = calculateHollowDamage(living.getArmorValue());

                float finalDamage = Math.max(hollowDamage * falloff * 0.6f, hollowDamage * 0.3f);

                DamageSource source = bulletDamageSource();

                living.invulnerableTime = 0;

                living.hurt(source, finalDamage);
                checkAndCountKill(living);
            }
        }
    }

    /**
     * Ставит скорость и ориентацию пули на вылет — <b>до</b> появления в мире.
     * <p>
     * Зовётся стрелком сразу перед {@code addFreshEntity}. Ориентация обязана
     * быть готова именно к этому моменту: пакет появления формируется при
     * добавлении сущности в уровень и несёт только поворот сущности, так что
     * выставить углы позже — уже некуда, первый кадр увидит пулю неориентированной.
     * <p>
     * Точное направление вылета кладётся ещё и в синхронизированные данные: на
     * клиенте до первого пакета позиции брать его больше неоткуда, а поворот из
     * пакета квантуется до байта и гуляет на полтора градуса. С ним пуля
     * доворачивалась бы на первом кадре.
     */
    public void setLaunchDirection(Vec3 velocity) {
        this.setDeltaMovement(velocity);

        if (velocity.lengthSqr() < 1.0E-8D) {
            return;
        }

        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float yaw = (float) (Math.atan2(velocity.x, velocity.z) * (180D / Math.PI));
        float pitch = (float) (Math.atan2(velocity.y, horizontal) * (180D / Math.PI));

        this.setYRot(yaw);
        this.setXRot(pitch);
        this.yRotO = yaw;
        this.xRotO = pitch;

        this.entityData.set(LAUNCH_YAW, yaw);
        this.entityData.set(LAUNCH_PITCH, pitch);
    }

    /**
     * Разворачивает пулю вдоль её скорости: обе оси (yaw и pitch) считаются из
     * вектора движения, как в 1.20.1.
     * <p>
     * Если скорости ещё нет — например, на клиенте между пакетом появления и
     * пакетом движения успевает пройти тик, — обе оси не трогаются. Иначе
     * {@code atan2(0, 0)} обнулял поворот, который только что пришёл в пакете
     * появления, и пуля до прихода скорости смотрела не туда.
     */
    public void alignToVelocity() {
        Vec3 velocity = this.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-8D) {
            return;
        }

        double horizontalDist = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        this.setYRot((float) (Math.atan2(velocity.x, velocity.z) * (180D / Math.PI)));
        this.setXRot((float) (Math.atan2(velocity.y, horizontalDist) * (180D / Math.PI)));

        if (this.tickCount == 0) {
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
        }
    }

    /**
     * Направление полёта для рендера — разность двух последних позиций,
     * пришедших с сервера.
     * <p>
     * Раньше здесь читался {@code deltaMovement}, но пакеты появления и
     * движения клампят скорость до ±3.9 блока/тик, так что локальный вектор
     * на быстрой пуле был попросту неверным. Разность координат квантованию по
     * скорости не подвержена и совпадает с реальной траекторией.
     */
    public Vec3 flightDirection() {
        double dx = this.getX() - this.serverPrevX;
        double dy = this.getY() - this.serverPrevY;
        double dz = this.getZ() - this.serverPrevZ;

        Vec3 delta = new Vec3(dx, dy, dz);
        if (delta.lengthSqr() > 1.0E-8D) {
            return delta.normalize();
        }

        // Предыдущей координаты ещё нет: до первого пакета позиции берём точное
        // направление вылета, пришедшее вместе с появлением. Поворот из пакета
        // появления для этого не годится — он квантуется до байта и вдобавок
        // переворачивает вертикаль, так что пуля дёргалась бы на первом кадре.
        return launchDirection();
    }

    /** Направление вылета ровно тем, каким его задал стрелок. */
    public Vec3 launchDirection() {
        float yaw = this.entityData.get(LAUNCH_YAW);
        float pitch = this.entityData.get(LAUNCH_PITCH);

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double horizontal = Math.cos(pitchRad);

        return new Vec3(
                Math.sin(yawRad) * horizontal,
                Math.sin(pitchRad),
                Math.cos(yawRad) * horizontal
        );
    }

    /**
     * Прижимает рендерный якорь к текущей точке.
     * <p>
     * Нужна на появлении сущности: до первого пакета позиции двигаться не от
     * чего, поэтому предыдущая точка обязана совпадать с текущей.
     */
    private void snapRenderAnchor() {
        this.serverPrevX = this.getX();
        this.serverPrevY = this.getY();
        this.serverPrevZ = this.getZ();
    }

    /** Сбрасывает якорь, если сервер перестал двигать пулю. См. {@link #packetsSeen}. */
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

        Vec3 lag = new Vec3(
                Mth.lerp(partialTick, this.serverPrevX, this.getX()) - this.getX(),
                Mth.lerp(partialTick, this.serverPrevY, this.getY()) - this.getY(),
                Mth.lerp(partialTick, this.serverPrevZ, this.getZ()) - this.getZ()
        );

        // Отставание без ограничения равно целому тику полёта. Пуля летит на
        // шесть блоков за тик, то есть на первом кадре тика нарисованная пуля
        // ещё там, где была в прошлом тике, — на шесть блоков позади настоящей.
        // Для пули, которая входит в моб и летит дальше, это читалось не как
        // пролёт, а как вытянутый луч, а после мобов — как исчезновение.
        //
        // Зажим не даёт нарисованной точке уходить дальше четверти блока от
        // настоящей позиции: она остаётся на пуле, а не на её прошлом тике.
        double lagSqr = lag.lengthSqr();
        if (lagSqr > MAX_RENDER_LAG * MAX_RENDER_LAG) {
            lag = lag.scale(MAX_RENDER_LAG / Math.sqrt(lagSqr));
        }
        return lag;
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        super.lerpMotion(x, y, z);
        // Поворот на клиенте ведём только из пакетов, а не из скорости: пакет
        // скорости приходит отдельно от пакета позиции, и между ними ствол
        // пули успевал дёрнуться. alignToVelocity здесь и был источником шатания.
        if (!this.level().isClientSide) {
            this.alignToVelocity();
        }
    }

    /**
     * Позиция приходит из пакета, а {@link AbstractArrow#lerpTo} делает
     * {@code setPos}, не трогая {@code xOld}. Из-за этого у рендера не было
     * двух точек для интерполяции, и пуля шла ступеньками по тикам.
     * <p>
     * Здесь текущая позиция запоминается как предыдущая, а новая ставится
     * поверх. Рендер потом сглаживает между ними сам — см.
     * {@link #getServerPrevX()} и {@link #flightDirection()}.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (this.level().isClientSide) {
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
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        // Якорь первой интерполяции: до первого пакета позиции двигаться не
        // от чего, поэтому предыдущая точка совпадает с текущей. Направление
        // вылета при этом уже есть — оно пришло вместе с появлением.
        this.packetsSeen++;
        this.snapRenderAnchor();
    }

    private void handleHitResult(HitResult hit) {
        if (hit.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hit;
            handleEntityHit(entityHit.getEntity());
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            this.onHitBlock((BlockHitResult) hit);
        }
    }

    /**
     * Бронебойный снимает за один тик всю шеренгу и доходит до стены за ней.
     * <p>
     * Пуля летит на шесть блоков за тик, и путь тика накрывает сразу несколько
     * целей, стоящих друг за другом. {@link ProjectileUtil#getEntityHitResult}
     * возвращает только ближайшую, а следующий тик пуля уже пролетела дальше —
     * то есть вся шеренга кроме первой осталась бы нетронутой. Здесь трассировка
     * по целям повторяется: каждая найденная получает урон (вдвое меньше
     * предыдущей), а луч продолжается за ней.
     * <p>
     * Блок ищется отдельно от целей, а не берётся из {@link #traceHit}: там при
     * попадании в моб возвращается цель, и стена за ней осталась бы незамеченной
     * — пуля пролетела бы сквозь неё, ни потратив пробитие, ни остановившись.
     */
    private void handlePiercingHits(Vec3 start, Vec3 end) {
        BlockHitResult wall = this.level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this
        ));

        // Цели ищутся только до стены: за ней их всё равно не видно.
        Vec3 limit = wall.getType() == HitResult.Type.MISS ? end : wall.getLocation();
        Vec3 origin = start;
        int guard = 0;

        while (guard++ < PIERCING_MAX_SWEEP_HITS) {
            EntityHitResult mob = nearestMob(origin, limit);
            if (mob == null) {
                break;
            }

            handleEntityHit(mob.getEntity());
            if (this.isRemoved()) {
                return;
            }
            // Луч продолжается сразу за найденной целью; сама цель уже в
            // piercedTargets и повторно не вернётся. Шаг идёт по текущему
            // курсу, а не по направлению вылета: пуля с первых тиков уже
            // просела по гравитации, и шаг по старому курсу увел бы луч с
            // настоящей траектории.
            origin = mob.getLocation().add(currentDirection().scale(PIERCING_TARGET_STEP));
        }

        if (wall.getType() == HitResult.Type.BLOCK) {
            this.onHitBlock(wall);
        }
    }

    /**
     * Ближайшая ещё не прошитая цель на отрезке луча.
     * <p>
     * Именно {@link LivingEntity}, а не любая сущность: {@link #handleEntityHit}
     * неживые игнорирует и в счётчик пробитых не кладёт, поэтому такая цель
     * возвращалась бы тем же лучом снова и снова, и цикл в
     * {@link #handlePiercingHits} выедал бы все восемь итераций, не дойдя до
     * мобов за ней.
     */
    private EntityHitResult nearestMob(Vec3 start, Vec3 limit) {
        AABB sweep = this.getBoundingBox().expandTowards(limit.subtract(start)).inflate(0.5F);
        return ProjectileUtil.getEntityHitResult(
                this.level(), this,
                start, limit,
                sweep,
                e -> e instanceof LivingEntity living
                        && living.isAlive()
                        && e != this.getOwner()
                        && e.isPickable()
                        && !piercedTargets.contains(e.getId())
        );
    }

    /**
     * Курс пули прямо сейчас.
     * <p>
     * Скорость на сервере точная, в отличие от направления вылета: гравитация
     * и сопротивление воздуха успевают изменить траекторию, и через десяток
     * тиков старый курс уходит от настоящего на заметный угол.
     */
    private Vec3 currentDirection() {
        Vec3 motion = this.getDeltaMovement();
        return motion.lengthSqr() > 1.0E-8D ? motion.normalize() : launchDirection();
    }

    /**
     * Тип урона пули.
     * <p>
     * Отдельный нужен ради блока неуязвимости: {@code LivingEntity#hurt}
     * сравнивает урон с предыдущим и отбрасывает его, если он не больше, когда у
     * цели уже стоит неуязвимость от только что нанесённого урона. Огонь от
     * зажигательной пули как раз и оставляет такую неуязвимость, и следующая
     * пуля того же залпа уходила в молоко. Тип помечен в
     * {@code bypasses_cooldown}, и проверка не выполняется вовсе.
     */
    private static final ResourceKey<DamageType> BULLET_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "turret_bullet"));

    /**
     * Урон пули.
     * <p>
     * Наносится своим типом, который обходит блок неуязвимости, поэтому
     * очередь зажигательных пулей не съедает сама себя: подожжённая первым
     * цель не мешает второму попасть.
     */
    private DamageSource bulletDamageSource() {
        Entity owner = this.getOwner();
        return owner instanceof LivingEntity livingOwner
                ? this.damageSources().source(BULLET_DAMAGE, livingOwner, this)
                : this.damageSources().source(BULLET_DAMAGE, null, this);
    }

    private void handleEntityHit(Entity target) {
        if (!(target instanceof LivingEntity livingTarget)) return;

        AmmoType currentType = getAmmoType();

        // Фугасный не бьёт точным попаданием и не рвётся на границе хитбокса: он
        // запоминает цель, продолжает лететь и детонирует у её центра — см.
        // фитиль в tick(). Иначе цель в эпицентре получала бы и прямое
        // попадание, и взрыв, то есть вдвое больше заданного урона за одну пулю.
        if (currentType == AmmoType.HE) {
            heTarget = livingTarget;
            heFuseTicks = 0;
            heClosestDistanceSqr = Double.MAX_VALUE;
            return;
        }

        if (currentType == AmmoType.RADIO) {
            if (lastHitTarget == null) {
                this.lastHitTarget = livingTarget;
                this.hitTickTimer = 0;

                float contactDamage = calculateHollowDamage(livingTarget.getArmorValue());
                DamageSource source = bulletDamageSource();

                livingTarget.invulnerableTime = 0;

                livingTarget.hurt(source, contactDamage * 0.4f);
                checkAndCountKill(livingTarget);
            }
            return;
        }

        float finalDamage = calculateDamage(livingTarget, currentType);
        DamageSource source = bulletDamageSource();

        livingTarget.invulnerableTime = 0;

        // Бронебойный идёт насквозь: каждая следующая цель получает вдвое
        // меньше, потому что боезаряд прошёл через столько-то препятствий.
        boolean piercing = currentType == AmmoType.PIERCING;
        if (piercing) {
            finalDamage /= (float) Math.pow(PIERCING_DAMAGE_FALLOFF, piercedTargets.size());
        }

        if (livingTarget.hurt(source, finalDamage)) {
            applySpecialEffect(livingTarget, currentType);
            checkAndCountKill(livingTarget);
        }

        if (piercing) {
            piercedTargets.add(livingTarget.getId());
            playHitSound();
            // Пуля летит дальше: следующая цель стоит впереди по курсу.
            return;
        }

        playHitSound();
        this.discard();
    }

    private HitResult traceHit(Vec3 start, Vec3 end) {
        // Радио-боезаряд — не пуля, а воздушный взрыватель: его enlarged хитбокс
        // (1.5 x 1.5 после 5 тиков) должен срабатывать по близости, а не по лучу.
        if (getAmmoType() == AmmoType.RADIO && getFlightDuration() >= RADIO_FUSE_ACTIVATION) {
            EntityHitResult proximity = findProximityHit();
            if (proximity != null) return proximity;
        }

        HitResult blockHit = this.level().clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        ));

        Vec3 endForEntities = end;
        if (blockHit.getType() != HitResult.Type.MISS) {
            endForEntities = blockHit.getLocation();
        }

        float raycastSize = getAmmoType() == AmmoType.RADIO && getFlightDuration() >= RADIO_FUSE_ACTIVATION
                ? 1.0F : 0.5F;
        AABB sweep = this.getBoundingBox().expandTowards(end.subtract(start)).inflate(raycastSize);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                this.level(), this,
                start, endForEntities,
                sweep,
                e -> e.isAlive() && e != this.getOwner() && e.isPickable() && !piercedTargets.contains(e.getId())
        );

        return entityHit != null ? entityHit : blockHit;
    }

    /**
     * Ближайшая к {@code point} точка на отрезке {@code start..end}.
     * <p>
     * Нужна фитилю фугасного: пуля летит на шесть блоков за тик, и на крупной
     * цели её положения в начале и в конце тика лежат по разные стороны от
     * центра, так что проверка по текущей точке его бы пропустила.
     */
    private static Vec3 closestPointOnSegment(Vec3 start, Vec3 end, Vec3 point) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-8D) {
            return start;
        }

        double t = Mth.clamp(point.subtract(start).dot(segment) / lengthSqr, 0.0D, 1.0D);
        return start.add(segment.scale(t));
    }

    /** Ближайшая живая цель внутри хитбокса взрывателя, либо null. */
    private EntityHitResult findProximityHit() {
        AABB fuse = this.getBoundingBox().inflate(PROXIMITY_FUSE_MARGIN);

        Entity closest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity candidate : this.level().getEntities(this, fuse,
                e -> e instanceof LivingEntity living
                        && living.isAlive()
                        && e != this.getOwner()
                        && e.isPickable())) {
            double distance = candidate.distanceToSqr(this.position());
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = candidate;
            }
        }

        return closest == null ? null : new EntityHitResult(closest, this.position());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (this.level().isClientSide) {
            return;
        }

        BlockPos pos = result.getBlockPos();
        BlockState state = this.level().getBlockState(pos);

        // Фугасный разрывается о любой блок: он не выбивает стекло, не пробивает
        // стену и не поджигает — он просто рвётся там, где остановился. Взрыв
        // неразрушающий, так что блок под ним остаётся целым.
        if (getAmmoType() == AmmoType.HE) {
            detonate();
            return;
        }

        // Стекло и обычный лёд выбиваются любым боезарядом, и на этом пуля не
        // останавливается: рыхлый материал она пробивает без всякой траты
        // пробития. Останавливало, и как раз ломало всё остальное: пуля умирала
        // на первом же стекле, а мобы за ним оставались невредимы.
        if (isBreakable(state)) {
            this.level().destroyBlock(pos, true);
            return;
        }

        if (canPierceBlock()) {
            // Бронебойный проходит насквозь и летит дальше: блок не трогаем, но
            // каждая пробутая стена стоит ему одного из двух.
            blocksPierced++;
            return;
        }

        // Зажигательный поджигает: и горящий блок, в который попал, и обычный.
        if (getAmmoType() == AmmoType.INCENDIARY) {
            ignite(pos);
        }

        playGroundSound();
        this.discard();
    }

    /** Остался ли ещё запас на пробитие блока. */
    private boolean canPierceBlock() {
        return getAmmoType() == AmmoType.PIERCING && blocksPierced < PIERCING_BLOCK_BUDGET;
    }

    /**
     * Фугасный патрон разрывается: взрыв, эффект и звук на убийство.
     * <p>
     * Вызывается только с сервера и только когда пуля уже долетела до цели
     * (см. фитиль в {@link #tick()}) либо упёрлась в блок.
     * <p>
     * Урон и отбрасывание отдаёт
     * {@link com.trd.explosion.logic.ExplosionHENonDestructive#explode} — тот же
     * неразрушающий взрыв, что и у ракеты, но с радиусом
     * {@link #HE_EXPLOSION_RADIUS}. Он же бесплатно даёт ванильный взрывной
     * звук и «маленькую» частицу взрыва: {@code Explosion#finalizeExplosion}
     * выбирает её по радиусу, а при 2.0 это ровно маленькая. Сверху добавляется
     * тот же вид, что при эволюции червя, — две {@code ParticleTypes.EXPLOSION}
     * с разбросом 0.15, — чтобы бум читался как один, а не как наложение.
     * <p>
     * Про убийство звук играется только когда взрыв кого-то действительно убил:
     * список живых целей рядом снимается до взрыва, а после проверяется
     * {@code isAlive()}. Отдельно это делать нельзя — взрыв возвращает только
     * флаг, а урон раздаётся на все живые сразу, и «убил кого-нибудь» из него
     * не узнать.
     */
    private void detonate() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            this.discard();
            return;
        }

        Entity owner = this.getOwner();
        Vec3 center = this.position();

        // Снимок живых целей рядом до взрыва: после он уже ничего не покажет.
        // Сама пуля в список не попадает — она стрела, а не живое существо.
        List<LivingEntity> nearby = serverLevel.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(HE_EXPLOSION_RADIUS),
                living -> living.isAlive() && living != owner && living.isPickable());

        com.trd.explosion.logic.ExplosionHENonDestructive.explode(
                serverLevel, center, owner, HE_EXPLOSION_RADIUS, HE_EXPLOSION_DAMAGE);

        // Вид эволюции червя: две ванильные вспышки взрыва с небольшим разбросом.
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                center.x, center.y, center.z,
                2,
                0.15D, 0.15D, 0.15D,
                0.02D);

        boolean killed = false;
        for (LivingEntity living : nearby) {
            if (!living.isAlive()) {
                killed = true;
                break;
            }
        }
        if (killed) {
            // Рёв босса Wither: ровно тот звук, которым он сопровождает вход в
            // усиленную стадию. Тише и ниже ванильного, потому что это хруст
            // боезаряда, а не сам босс.
            serverLevel.playSound(null, center.x, center.y, center.z,
                    SoundEvents.WITHER_BREAK_BLOCK,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    2.0F,
                    1F + serverLevel.random.nextFloat() * 0.1F);
        }

        this.discard();
    }

    /** В 1.21.1 {@code AbstractGlassBlock} больше нет: стекло — это подтипы {@code TransparentBlock}. */
    private static boolean isGlass(BlockState state) {
        return state.getBlock() instanceof StainedGlassBlock
                || state.getBlock() instanceof TintedGlassBlock
                || state.getBlock() instanceof StainedGlassPaneBlock;
    }

    /**
     * Что пуля выбивает насквозь без траты пробития.
     * <p>
     * Только ванильное стекло и обычный лёд: упакованный и инейный лёд — это
     * уже другие блоки, и ломать их пуля не должна.
     */
    private static boolean isBreakable(BlockState state) {
        return isGlass(state) || state.is(Blocks.ICE);
    }

/**
     * Зажигательный поджигает то, во что попал.
     * <p>
     * Два случая, и оба приводят к огню, а не к одному его виду:
     * попадание в уже горящий блок перекидывает огонь на ближайшую горюю
     * поверхность рядом с ним, а попадание в горючий блок поджигает его самого —
     * и не только сверху, но и со всех сторон, потому что горящая трава или
     * забор стоят вплотную к соседям и огонь на них переходит сам.
     */
    private void ignite(BlockPos pos) {
        BlockState hit = this.level().getBlockState(pos);

        if (hit.getBlock() instanceof FireBlock) {
            igniteAround(pos);
            return;
        }

        if (!hit.isFlammable(this.level(), pos, Direction.UP)) {
            return;
        }

        // Огонь ставится в пустую клетку, прилегающую к блоку, и только если он
        // там вообще может стоять, то есть если под ним или сбоку есть
        // твёрдая опора. Направление — от клетки огня к опоре, как это делает
        // сам {@code FireBlock}.
        for (Direction dir : Direction.values()) {
            placeFire(pos.relative(dir), dir.getOpposite());
        }
    }


    /**
     * Перекидывает огонь с горящего блока на ближайшую горюю поверхность.
     * <p>
     * За тик попадания поджигается одна точка: иначе пуля разжигала бы куст
     * леса.
     */
    private void igniteAround(BlockPos firePos) {
        for (BlockPos around : BlockPos.betweenClosed(firePos.offset(-1, -1, -1), firePos.offset(1, 1, 1))) {
            if (around.equals(firePos) || !this.level().getBlockState(around).isAir()) {
                continue;
            }
            if (!this.level().getBlockState(around.below())
                    .isFlammable(this.level(), around.below(), Direction.UP)) {
                continue;
            }
            this.level().setBlockAndUpdate(around, Blocks.FIRE.defaultBlockState());
            return;
        }
    }

    /** Ставит огонь, если его там вообще можно поставить. */
    private void placeFire(BlockPos pos, Direction support) {
        if (!this.level().getBlockState(pos).isAir()) {
            return;
        }
        if (!FireBlock.canBePlacedAt(this.level(), pos, support)) {
            return;
        }
        this.level().setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
    }

    private float calculateDamage(LivingEntity target, AmmoType type) {
        float armor = (float) target.getArmorValue();
        switch (type) {
            case PIERCING:
                return calculatePiercingDamage(armor);
            case HOLLOW:
                return calculateHollowDamage(armor);
            case RADIO:
                return calculateHollowDamage(armor);
            case INCENDIARY:
                return calculateIncendiaryDamage(armor);
            default:
                return Math.max(baseDamage * (1.0f - armor * 0.02f), baseDamage * 0.4f);
        }
    }

    /**
     * Бронебойный: высокий урон и почти полное игнорирование брони.
     * Голый против брони — 19, в полном комплекте — всё ещё ~16, потому что
     * множитель урона зависит от брони лишь на 55%.
     */
    private float calculatePiercingDamage(float armor) {
        float penetration = Math.min(0.60f, 0.20f + baseDamage * 0.02f + baseSpeed * 0.05f);
        float raw = baseDamage * (1.0f + penetration);
        float armorFactor = 1.0f - (armor / (armor + 80.0f));
        return Math.max(raw * (0.45f + 0.55f * armorFactor), baseDamage * 0.75f);
    }

    /**
     * Экспансивный: сильнее всех без брони, но быстро вязнет в ней.
     * Голый — 13.6, в полном комплекте — 4.7. Радио-контакт и взрыв в воздухе
     * используют ту же формулу, так как это тот же тип сердечника.
     */
    private float calculateHollowDamage(float armor) {
        float armorMultiplier = Math.max(0.40f, 1.70f - (armor / 18.0f));
        return baseDamage * armorMultiplier;
    }

    /** Зажигательный: игнорирует броню почти полностью, но бьёт слабо и поджигает. */
    private float calculateIncendiaryDamage(float armor) {
        return Math.max(baseDamage * (1.0f - armor * 0.015f), baseDamage * 0.5f);
    }

    private void applySpecialEffect(LivingEntity target, AmmoType type) {
        if (type == AmmoType.INCENDIARY) target.igniteForSeconds(5);
    }

    private void playHitSound() {
        if (ModSounds.BULLET_IMPACT.isBound()) {
            this.playSound(ModSounds.BULLET_IMPACT.get(), 0.5F, 0.9F + this.random.nextFloat() * 0.2F);
        } else {
            this.playSound(SoundEvents.GENERIC_HURT, 0.5F, 1.0F);
        }
    }

    private void playGroundSound() {
        if (ModSounds.BULLET_GROUND.isBound()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.BULLET_GROUND.get(), net.minecraft.sounds.SoundSource.PLAYERS,
                    0.5F, 0.9F + this.random.nextFloat() * 0.2F);
        } else {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.STONE_HIT, net.minecraft.sounds.SoundSource.PLAYERS,
                    0.5F, 1.0F);
        }
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return ModSounds.BULLET_GROUND.isBound() ? ModSounds.BULLET_GROUND.get() : SoundEvents.ARROW_HIT;
    }

    /**
     * В 1.20.1 пуля не подбиралась и {@code getPickupItem} возвращал пустой стак,
     * а {@code AbstractArrow#addAdditionalSaveData} писал его в NBT только если
     * он непустой. В 1.21 запись безусловная, и пустой стак роняет сохранение
     * сущности ({@code IllegalStateException: Cannot encode empty ItemStack}), так
     * что предмет-заглушка обязателен.
     * <p>
     * Игрок его всё равно не получит: {@link AbstractArrow.Pickup} у стрел по
     * умолчанию {@code DISALLOWED}, а пуля сама удаляется при попадании.
     */
    @Override
    protected ItemStack getDefaultPickupItem() {
        return PICKUP_PLACEHOLDER.copy();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.initialPosition != null) {
            tag.putDouble("InitialX", this.initialPosition.x);
            tag.putDouble("InitialY", this.initialPosition.y);
            tag.putDouble("InitialZ", this.initialPosition.z);
        }
        tag.putFloat("InitialSpeed", this.initialSpeed);
        tag.putInt("FlightTime", getFlightDuration());
        // Направление вылета переживает перезагрузку чанка: без него пуля,
        // загруженная с диска, на первом кадре смотрела бы в юг вместо
        // собственного курса — синхронизированные данные с появлением не летят.
        tag.putFloat("LaunchYaw", this.entityData.get(LAUNCH_YAW));
        tag.putFloat("LaunchPitch", this.entityData.get(LAUNCH_PITCH));
        // Пробитие переживает перезагрузку чанка: без него загруженная с диска
        // пуля получила бы вторую попытку пробить столько же блоков.
        tag.putInt("BlocksPierced", blocksPierced);
        tag.putIntArray("PiercedTargets", piercedTargets);
        if (lastHitTarget != null) {
            tag.putUUID("LastHitUUID", lastHitTarget.getUUID());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("InitialX")) {
            this.initialPosition = new Vec3(
                    tag.getDouble("InitialX"),
                    tag.getDouble("InitialY"),
                    tag.getDouble("InitialZ")
            );
        }
        this.initialSpeed = tag.getFloat("InitialSpeed");
        setFlightDuration(tag.getInt("FlightTime"));
        this.entityData.set(LAUNCH_YAW, tag.getFloat("LaunchYaw"));
        this.entityData.set(LAUNCH_PITCH, tag.getFloat("LaunchPitch"));
        blocksPierced = tag.getInt("BlocksPierced");
        piercedTargets.clear();
        for (int id : tag.getIntArray("PiercedTargets")) {
            piercedTargets.add(id);
        }
    }

    /**
     * Счётчик убийств ведёт турель, из которой выпущена пуля. Сами турели
     * приезжают следующей пачкой, поэтому пока это только точка расширения.
     */
    private void checkAndCountKill(LivingEntity target) {
        if (target.isDeadOrDying()) {
            Entity owner = this.getOwner();
            if (owner instanceof com.trd.entity.weapons.turrets.TurretLightLinkedEntity turret) {
                net.minecraft.core.BlockPos pos = turret.getParentBlock();
                if (pos != null && this.level().getBlockEntity(pos)
                        instanceof com.trd.block.entity.weapons.TurretLightPlacerBlockEntity be) {
                    be.incrementKills();
                }
            }
        }
    }

    /**
     * Ищет тип боезаряда по строковому id предмета.
     */
    public static AmmoRegistry.AmmoType lookupAmmoType(String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        return item == null ? null : AmmoRegistry.getAmmoTypeFromItem(item);
    }
}
