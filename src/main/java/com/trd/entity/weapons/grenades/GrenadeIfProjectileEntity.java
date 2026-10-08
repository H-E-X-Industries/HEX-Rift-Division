package com.trd.entity.weapons.grenades;

import com.trd.explosion.logic.ExplosionFire;
import com.trd.explosion.logic.ExplosionHE;
import com.trd.explosion.logic.ExplosionStandard;
import com.trd.explosion.logic.VanillaExplosionSound;
import com.trd.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Ударная граната с инерционным взрывателем: после первого касания запускается
 * фиксированный таймер (4с), липучий тип прилипает к блоку/сущности.
 */
public class GrenadeIfProjectileEntity extends ThrowableItemProjectile {

    private static final EntityDataAccessor<Boolean> TIMER_ACTIVATED =
            SynchedEntityData.defineId(GrenadeIfProjectileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DETONATION_TIME =
            SynchedEntityData.defineId(GrenadeIfProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> GRENADE_IF_TYPE_ID =
            SynchedEntityData.defineId(GrenadeIfProjectileEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_STUCK =
            SynchedEntityData.defineId(GrenadeIfProjectileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> STUCK_ENTITY_ID =
            SynchedEntityData.defineId(GrenadeIfProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STUCK_AT_TICK =
            SynchedEntityData.defineId(GrenadeIfProjectileEntity.class, EntityDataSerializers.INT);

    private static final int FUSE_SECONDS = 4;
    private static final float MIN_BOUNCE_SPEED = 0.1f;
    private static final float BOUNCE_MULTIPLIER = 0.4f;

    private GrenadeIfType grenadeType;
    private boolean exploded = false;
    private int stuckEntityId = -1;

    /**
     * Смещение относительно центра цели; {@code null}, пока граната ни к чему не
     * прилипла.
     * <p>
     * Синхронизировать его не нужно: клиент берёт смещение из собственной позиции
     * в тот тик, когда узнаёт о прилипании, — граната в этот момент стоит там
     * же, где её поставил сервер.
     */
    @Nullable
    private Vec3 stuckOffset;

    public GrenadeIfProjectileEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public GrenadeIfProjectileEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level,
                                     LivingEntity thrower, GrenadeIfType type) {
        super(entityType, thrower, level);
        this.grenadeType = type;
        this.entityData.set(GRENADE_IF_TYPE_ID, type.name());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TIMER_ACTIVATED, false);
        builder.define(DETONATION_TIME, 0);
        builder.define(GRENADE_IF_TYPE_ID, GrenadeIfType.GRENADE_IF.name());
        builder.define(DATA_STUCK, false);
        builder.define(STUCK_ENTITY_ID, -1);
        builder.define(STUCK_AT_TICK, -1);
    }

    /**
     * Тип ударной гранаты.
     * <p>
     * Поле {@link #grenadeType} есть только у той копии снаряда, что создана на
     * сервере: на клиенте сущность поднимается фабрикой без типа, и он приезжает
     * в синхронизированных данных. Поэтому значение читается оттуда и кэшируется
     * — но только успешное: если тип ещё не приехал, кэшировать нечего, иначе
     * первый же неудачный чих навсегда оставил бы рендер на базовой текстуре.
     * <p>
     * Рендеру вообще полагаться на это не стоит: у снаряда наверняка известен
     * тип сущности, и по нему тип находится однозначно — см.
     * {@link GrenadeIfType#typeOf}.
     */
    public GrenadeIfType getGrenadeType() {
        if (grenadeType == null) {
            try {
                grenadeType = GrenadeIfType.valueOf(this.entityData.get(GRENADE_IF_TYPE_ID));
            } catch (Exception e) {
                return GrenadeIfType.GRENADE_IF;
            }
        }
        return grenadeType;
    }

    @Override
    protected Item getDefaultItem() {
        return getGrenadeType().getItem();
    }

    /**
     * Скорость догоняния цели, доля расстояния за тик.
     * <p>
     * Половина — как у брутального червя: на такую скорость граната отстаёт от
     * цели примерно на один тик, а глазом это не читается. Меньше — граната
     * заметно отстаёт при резких поворотах, больше — дёргается на каждой
     * смене направления у цели.
     */
    private static final double FOLLOW_SHARPNESS = 0.5D;

    @Override
    public void tick() {
        super.tick();

        // Прилипание ведётся на ОБЕИХ сторонах — это и есть разница с прежним
        // поведением. Раньше код стоял за `if (level().isClientSide) return`, и
        // клиент про прилипание знал только из синхронизированных данных: ждал
        // позицию от сервера, а сервер отправляет пакет перемещения не чаще
        // раза в 20 тиков — граната дёргалась по цели раз в секунду.
        // Теперь каждый кадр обе стороны считают точку сами, причём на клиенте по
        // уже сглаженной позиции цели, и граната идёт за ней гладко.
        followStuckTarget();

        if (level().isClientSide) return;

        if (this.entityData.get(DATA_STUCK) && stuckEntityId == -1) {
            this.setDeltaMovement(Vec3.ZERO);
        }

        if (this.entityData.get(TIMER_ACTIVATED)) {
            if (this.tickCount >= this.entityData.get(DETONATION_TIME)) {
                explode(this.blockPosition());
            }
        }
    }

    /**
     * Держит гранату на цели: тянет её к точке {@code центр цели + смещение}.
     * <p>
     * Целевой id берётся из синхронизированных данных, а не из поля: поле
     * заполняется только на сервере, и на клиенте оно осталось бы равным -1 —
     * тогда бы вся эта логика просто не выполнялась. Потерявшаяся цель взрывает
     * гранату, но только на сервере: клиенту нечего решать, сервер всё равно
     * снимет снаряд следующим же пакетом.
     */
    private void followStuckTarget() {
        if (!this.entityData.get(DATA_STUCK)) return;

        int targetId = this.entityData.get(STUCK_ENTITY_ID);
        if (targetId <= 0) {
            // Прилипла к блоку: следить не за чем, стоит на месте.
            return;
        }

        Entity target = level().getEntity(targetId);
        if (target == null || !target.isAlive()) {
            if (!level().isClientSide) {
                explode(this.blockPosition());
            }
            return;
        }

        Vec3 center = target.getBoundingBox().getCenter();

        // Смещение запоминается один раз, на той стороне, с которой граната
        // коснулась цели: пока оно неизвестно, тянуть не к чему.
        if (stuckOffset == null) {
            stuckOffset = this.position().subtract(center);
        }

        Vec3 current = this.position();
        Vec3 next = current.lerp(center.add(stuckOffset), FOLLOW_SHARPNESS);
        this.setPos(next.x, next.y, next.z);

        // Скорость на сервере нужна ещё и трекеру: по ней он видит, что сущность
        // движется, и шлёт позицию чаще, чем раз в 20 тиков. На клиенте она
        // только мешает — следующий тик граната всё равно встаёт на расчётную
        // точку, а лишнее движение сдвинуло бы её на кадр.
        if (!level().isClientSide) {
            this.setDeltaMovement(target.getDeltaMovement());
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // НЕ вызываем super.onHitBlock для неслаймовых, иначе граната удалится (discard)
        if (level().isClientSide || exploded) return;

        activateTimer();

        if (getGrenadeType() == GrenadeIfType.GRENADE_IF_SLIME) {
            super.onHitBlock(result); // слаймовую не трогаем, оставляем старую логику
            this.entityData.set(DATA_STUCK, true);
            this.entityData.set(STUCK_AT_TICK, this.tickCount);
            this.setDeltaMovement(Vec3.ZERO);
            this.setNoGravity(true);
            Vec3 pos = result.getLocation();
            this.setPos(pos.x, pos.y, pos.z);
        } else {
            handleBounce(result);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide || exploded) return;

        activateTimer();

        if (getGrenadeType() == GrenadeIfType.GRENADE_IF_SLIME) {
            super.onHitEntity(result); // слаймовую не трогаем
            stickToEntity(result.getEntity());
        }
    }

    private void activateTimer() {
        if (!this.entityData.get(TIMER_ACTIVATED)) {
            this.entityData.set(TIMER_ACTIVATED, true);
            this.entityData.set(DETONATION_TIME, this.tickCount + (FUSE_SECONDS * 20));
        }
    }

    private void handleBounce(BlockHitResult result) {
        Vec3 velocity = this.getDeltaMovement();
        float speed = (float) velocity.length();

        if (speed < MIN_BOUNCE_SPEED) {
            // Отскакивать больше не от чего: горизонтальное движение гасится, и
            // граната просто падает под тяжестью до самого подрыва.
            // Раньше здесь стоял ещё и setNoGravity(true), и граната намертво
            // зависала в воздухе, откуда её так и не сдвинуть — до самого таймера.
            // Липучая сюда не доходит: у неё своя ветка выше, она прилипает и
            // должна висеть.
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        BlockPos blockPos = result.getBlockPos();
        level().playSound(null, blockPos, ModSounds.BOUNCE_RANDOM.get(), SoundSource.NEUTRAL, 2.1F, 1.0F);

        Vec3 currentVelocity = this.getDeltaMovement();
        Vec3 hitNormal = Vec3.atLowerCornerOf(result.getDirection().getNormal());
        Vec3 reflectedVelocity = currentVelocity.subtract(hitNormal.scale(2 * currentVelocity.dot(hitNormal)));
        this.setDeltaMovement(reflectedVelocity.scale(BOUNCE_MULTIPLIER));
        this.hasImpulse = true;
    }

    /**
     * Прилипание к сущности: граната встаёт вплотную к телу снаружи и дальше
     * едет вместе с ним.
     * <p>
     * Точка крепления — это ровно то смещение от центра цели, которое потом
     * держит {@link #followStuckTarget()}. Расхождение между местом, куда граната
     * реально встала, и точкой, к которой её затем тянут, читалось бы как рывок
     * на первом же тике после касания — раньше именно это и было: смещение
     * считалось от центра, а граната ставилась на {@code attachPos.y - высота
     * гранаты / 2 + высота цели / 2}, то есть на другой высоте.
     */
    private void stickToEntity(Entity entity) {
        this.entityData.set(DATA_STUCK, true);
        this.entityData.set(STUCK_AT_TICK, this.tickCount);
        this.stuckEntityId = entity.getId();
        this.setNoGravity(true);
        this.entityData.set(STUCK_ENTITY_ID, entity.getId());

        Vec3 center = entity.getBoundingBox().getCenter();
        Vec3 toGrenade = this.position().subtract(center);
        double dist = toGrenade.length();

        Vec3 dir = dist < 1.0E-6
                ? new Vec3(0.0, 1.0, 0.0)
                : toGrenade.scale(1.0 / dist);

        // Не ближе, чем полусумма ширин: вплотную к телу, но не внутри него.
        double distance = Math.max(entity.getBbWidth() * 0.5 + this.getBbWidth() * 0.5, dist);
        stuckOffset = dir.scale(distance);

        this.setPos(center.x + stuckOffset.x, center.y + stuckOffset.y, center.z + stuckOffset.z);
        this.setDeltaMovement(entity.getDeltaMovement());
    }

    /**
     * Сколько секунд граната уже летит — по этому числу рендер крутит модель.
     * <p>
     * У прилипшей гранаты отсчёт останавливается на моменте касания: она больше
     * не летит, и продолжать вращение вокруг оси на месте — это вертящийся
     * снаряд, приклеенный к цели. Даже {@code partialTick} не добавляется, иначе
     * в каждом кадре модель всё равно смещалась бы на долю оборота.
     * <p>
     * Тик заморозки синхронизирован, поэтому замерший угол переживает и
     * перезагрузку чанка: без этого граната после перезагрузки дёрнулась бы на
     * пол-оборота и поехала дальше крутиться.
     */
    public float flightSeconds(float partialTick) {
        int stuckAt = this.entityData.get(STUCK_AT_TICK);
        return (stuckAt >= 0 ? stuckAt : this.tickCount + partialTick) / 20.0F;
    }

    /**
     * Подрыв ударной гранаты.
     * <p>
     * Звук у всех четырёх видов один — свой, {@code trd:grenade_explosion}, вместо
     * ванильного {@code entity.generic.explode}: ударная граната с инерционным
     * взрывателем звучит иначе, чем обычная. У осколочной и фугасной он
     * подставляется прямо в ванильный взрыв (см. {@link VanillaExplosionSound}),
     * у зажигательной меняется в самой {@link ExplosionFire} — у неё звук
     * играется явно, а не внутри ванильного взрыва.
     */
    private void explode(BlockPos pos) {
        if (level().isClientSide || this.isRemoved() || exploded) return;
        exploded = true;
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Level level = level();
        float radius = grenadeType.getExplosionPower();
        Holder<SoundEvent> sound = ModSounds.GRENADE_EXPLOSION.getDelegate();
        switch (grenadeType) {
            case GRENADE_IF -> ExplosionStandard.explode(level, center, this.getOwner(), radius, grenadeType.getCustomDamage(), sound);
            case GRENADE_IF_HE -> ExplosionHE.explode(level, center, this.getOwner(), radius, grenadeType.getCustomDamage(), sound);
            case GRENADE_IF_FIRE -> ExplosionFire.explode((ServerLevel) level, center, this.getOwner(), radius, true);
            case GRENADE_IF_SLIME -> ExplosionStandard.explode(level, center, this.getOwner(), radius, grenadeType.getCustomDamage(), sound);
        }
        this.discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("TimerActivated", this.entityData.get(TIMER_ACTIVATED));
        tag.putInt("DetonationTime", this.entityData.get(DETONATION_TIME));
        if (grenadeType != null) tag.putString("GrenadeType", grenadeType.name());
        tag.putBoolean("Exploded", exploded);
        tag.putBoolean("Stuck", this.entityData.get(DATA_STUCK));
        tag.putInt("StuckEntityId", stuckEntityId);
        tag.putInt("StuckAtTick", this.entityData.get(STUCK_AT_TICK));
        if (stuckOffset != null) {
            tag.putDouble("StuckOffsetX", stuckOffset.x);
            tag.putDouble("StuckOffsetY", stuckOffset.y);
            tag.putDouble("StuckOffsetZ", stuckOffset.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(TIMER_ACTIVATED, tag.getBoolean("TimerActivated"));
        this.entityData.set(DETONATION_TIME, tag.getInt("DetonationTime"));
        if (tag.contains("GrenadeType")) {
            String typeName = tag.getString("GrenadeType");
            this.grenadeType = GrenadeIfType.valueOf(typeName);
            this.entityData.set(GRENADE_IF_TYPE_ID, typeName);
        }
        this.exploded = tag.getBoolean("Exploded");
        this.entityData.set(DATA_STUCK, tag.getBoolean("Stuck"));
        this.stuckEntityId = tag.getInt("StuckEntityId");
        this.entityData.set(STUCK_AT_TICK, tag.getInt("StuckAtTick"));
        if (tag.contains("StuckOffsetX")) {
            this.stuckOffset = new Vec3(
                    tag.getDouble("StuckOffsetX"),
                    tag.getDouble("StuckOffsetY"),
                    tag.getDouble("StuckOffsetZ")
            );
        }
    }
}
