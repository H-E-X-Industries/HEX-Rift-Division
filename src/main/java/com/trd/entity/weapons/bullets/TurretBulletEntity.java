package com.trd.entity.weapons.bullets;

import com.trd.entity.ModEntities;
import com.trd.item.weapons.ammo.AmmoRegistry;
import com.trd.sound.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * Пупка 20 мм. Общая снасть для пушки и турелей: тип боезаряда приходит из
 * {@link AmmoRegistry} и определяет пробитие, урон и поведение при попадании.
 * <p>
 * Тип боезаряда и время полёта раздаются клиенту через {@link SynchedEntityData},
 * а не через дополнительные данные спавна — в NeoForge 1.21 механика
 * {@code IEntityAdditionalSpawnData} удалена, а синхронизированные данные
 * уходят вместе с пакетом появления сущности.
 */
public class TurretBulletEntity extends AbstractArrow implements GeoEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<String> AMMO_ID =
            SynchedEntityData.defineId(TurretBulletEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> AMMO_TYPE =
            SynchedEntityData.defineId(TurretBulletEntity.class, EntityDataSerializers.STRING);
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
    /** Запас к хитбоксу взрывателя, чтобы цель у края всё же срабатывала. */
    private static final double PROXIMITY_FUSE_MARGIN = 0.25D;

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
    private static final double CENTER_DETONATE_RADIUS_SQR = 0.09D;

    public enum AmmoType {
        NORMAL("normal"), PIERCING("piercing"), HOLLOW("hollow"), INCENDIARY("incendiary"), RADIO("radio");

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
        builder.define(FLIGHT_TIME, 0);
        builder.define(SPIN, 0.0F);
        builder.define(LAUNCH_YAW, 0.0F);
        builder.define(LAUNCH_PITCH, 0.0F);
    }

    public void setAmmoType(AmmoRegistry.AmmoType ammoType) {
        if (ammoType == null) return;
        this.baseDamage = ammoType.damage;
        this.baseSpeed = ammoType.speed;
        this.entityData.set(AMMO_ID, ammoType.id);

        if (ammoType.id.contains("piercing")) {
            this.ammoType = AmmoType.PIERCING;
            this.entityData.set(AMMO_TYPE, "piercing");
        } else if (ammoType.id.contains("hollow")) {
            this.ammoType = AmmoType.HOLLOW;
            this.entityData.set(AMMO_TYPE, "hollow");
        } else if (ammoType.id.contains("fire") || ammoType.id.contains("incendiary")) {
            this.ammoType = AmmoType.INCENDIARY;
            this.entityData.set(AMMO_TYPE, "incendiary");
        } else if (ammoType.id.contains("radio")) {
            this.ammoType = AmmoType.RADIO;
            this.entityData.set(AMMO_TYPE, "radio");
        } else {
            this.ammoType = AmmoType.NORMAL;
            this.entityData.set(AMMO_TYPE, "normal");
        }

        this.setBaseDamage(baseDamage);
    }

    public String getAmmoId() {
        return this.entityData.get(AMMO_ID);
    }

    public AmmoType getAmmoType() {
        return AmmoType.fromString(this.entityData.get(AMMO_TYPE));
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
            handleHitResult(hit);
            if (this.isRemoved()) {
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

                Entity owner = this.getOwner();
                DamageSource source = owner instanceof LivingEntity livingOwner
                        ? this.damageSources().mobProjectile(this, livingOwner)
                        : this.damageSources().arrow(this, owner);

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
        return new Vec3(
                Mth.lerp(partialTick, this.serverPrevX, this.getX()) - this.getX(),
                Mth.lerp(partialTick, this.serverPrevY, this.getY()) - this.getY(),
                Mth.lerp(partialTick, this.serverPrevZ, this.getZ()) - this.getZ()
        );
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

    private void handleEntityHit(Entity target) {
        if (!(target instanceof LivingEntity livingTarget)) return;

        AmmoType currentType = getAmmoType();

        if (currentType == AmmoType.RADIO) {
            if (lastHitTarget == null) {
                this.lastHitTarget = livingTarget;
                this.hitTickTimer = 0;

                float contactDamage = calculateHollowDamage(livingTarget.getArmorValue());
                Entity owner = this.getOwner();
                DamageSource source = owner instanceof LivingEntity livingOwner
                        ? this.damageSources().mobProjectile(this, livingOwner)
                        : this.damageSources().arrow(this, owner);

                livingTarget.invulnerableTime = 0;

                livingTarget.hurt(source, contactDamage * 0.4f);
                checkAndCountKill(livingTarget);
            }
            return;
        }

        float finalDamage = calculateDamage(livingTarget, currentType);
        Entity owner = this.getOwner();
        DamageSource source = owner instanceof LivingEntity livingOwner
                ? this.damageSources().mobProjectile(this, livingOwner)
                : this.damageSources().arrow(this, owner);

        livingTarget.invulnerableTime = 0;

        if (livingTarget.hurt(source, finalDamage)) {
            applySpecialEffect(livingTarget, currentType);
            checkAndCountKill(livingTarget);
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
                e -> e.isAlive() && e != this.getOwner() && e.isPickable()
        );

        return entityHit != null ? entityHit : blockHit;
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
        if (!this.level().isClientSide) {
            BlockState state = this.level().getBlockState(result.getBlockPos());
            if (isGlass(state)) {
                this.level().destroyBlock(result.getBlockPos(), true);
            }
            playGroundSound();
            this.discard();
        }
    }

    /** В 1.21.1 {@code AbstractGlassBlock} больше нет: стекло — это подтипы {@code TransparentBlock}. */
    private static boolean isGlass(BlockState state) {
        return state.getBlock() instanceof StainedGlassBlock
                || state.getBlock() instanceof TintedGlassBlock
                || state.getBlock() instanceof StainedGlassPaneBlock;
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
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
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
