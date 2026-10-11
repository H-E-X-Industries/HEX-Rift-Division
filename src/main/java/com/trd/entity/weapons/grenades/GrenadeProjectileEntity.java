package com.trd.entity.weapons.grenades;

import com.trd.explosion.logic.ExplosionFire;
import com.trd.explosion.logic.ExplosionHE;
import com.trd.explosion.logic.ExplosionStandard;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Осколочная/фугасная/зажигательная/липучая/умная граната.
 * Отскакивает от блоков заданное число раз, потом взрывается; липучка прилипает,
 * умная взрывается от касания сущности.
 *
 * <p><b>Липучка детонирует, когда пропадает то, к чему прилипла.</b> Прилипшая к
 * блоку граната следит за ним и взрывается, как только блока на месте не станет —
 * его снесли, взрывом перебило, поставили другой. Прилипшая к мобу и так уже
 * detonates вместе с ним: {@link #tick()} видит, что сущности больше нет, и
 * роняет её на месте последнего касания. Смысл один — липучка это мина, и она
 * срабатывает по событию, а не просто по таймеру.
 */
public class GrenadeProjectileEntity extends ThrowableItemProjectile {

    static final EntityDataAccessor<String> GRENADE_TYPE_ID =
            SynchedEntityData.defineId(GrenadeProjectileEntity.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<Integer> STUCK_ENTITY_ID =
            SynchedEntityData.defineId(GrenadeProjectileEntity.class, EntityDataSerializers.INT);

    private int bounceCount = 0;
    private GrenadeType grenadeType;
    private boolean stuck = false;
    private int stuckTimer = 0;
    private boolean exploded = false;
    private int stuckEntityId = -1;
    private Vec3 stuckOffset = Vec3.ZERO;

    /** Блок, к которому прилипла граната: по нему и проверяется, что опора не пропала. */
    private BlockPos stuckBlockPos;

    /** Блок, который стоял в {@link #stuckBlockPos} в момент прилипания. */
    private Block stuckBlock;

    /** Фитиль прилипания к блоку — обычный фитиль липучей гранаты, две секунды. */
    private static final int BLOCK_STICK_FUSE_TICKS = 40;

    /**
     * Фитиль прилипания к мобу, в тиках.
     * <p>
     * Заметно длиннее фитиля на блоке, и это не украшение: прилипание к мобу и
     * есть подготовка мины, а мина ставится затем, чтобы кто-то чужой её потом
     * убил. На двух секундах такая мина не успела бы дождаться ничего — цель
     * ушла бы раньше, чем что-то с ней случилось, и граната рванула бы впустую.
     */
    private static final int ENTITY_STICK_FUSE_TICKS = 200;

    /** Радиус взрыва умной гранаты при детонации от контакта с сущностью. */
    private static final float SMART_CONTACT_RADIUS = 7.0f;

    /** Аварийный таймер (10с) для гранат, которые так и не попали в блок. */
    private static final int FAILSAFE_TICKS = 200;

    public GrenadeProjectileEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public GrenadeProjectileEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level,
                                   LivingEntity livingEntity, GrenadeType type) {
        super(entityType, livingEntity, level);
        this.grenadeType = type;
        this.entityData.set(GRENADE_TYPE_ID, type.name());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(GRENADE_TYPE_ID, GrenadeType.STANDARD.name());
        builder.define(STUCK_ENTITY_ID, -1);
    }

    @Override
    protected Item getDefaultItem() {
        if (grenadeType == null) {
            try {
                grenadeType = GrenadeType.valueOf(this.entityData.get(GRENADE_TYPE_ID));
            } catch (Exception e) {
                grenadeType = GrenadeType.STANDARD;
            }
        }
        return grenadeType != null ? grenadeType.getItem() : Items.SNOWBALL;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        // Плавное прилипание к сущности — логика как у червя
        if (stuck && stuckEntityId != -1) {
            Entity entity = level().getEntity(stuckEntityId);
            if (entity != null && entity.isAlive()) {
                Vec3 mobCenter = entity.getBoundingBox().getCenter();
                Vec3 desiredPos = mobCenter.add(stuckOffset);

                Vec3 currentPos = this.position();
                double lerp = 0.5;
                double newX = currentPos.x + (desiredPos.x - currentPos.x) * lerp;
                double newY = currentPos.y + (desiredPos.y - currentPos.y) * lerp;
                double newZ = currentPos.z + (desiredPos.z - currentPos.z) * lerp;

                this.setPos(newX, newY, newZ);
                this.setDeltaMovement(entity.getDeltaMovement());
            } else {
                explode(false);
                return;
            }
            stuckTimer--;
            if (stuckTimer <= 0) {
                explode(false);
                return;
            }
        }

        if (stuck && stuckEntityId == -1) {
            // Прилипла к блоку: сперва проверяем, что блок вообще на месте.
            if (supportGone()) {
                explode(false);
                return;
            }
            stuckTimer--;
            if (stuckTimer <= 0) explode(false);
        }

        // Страховка (10с). Осколочные/фугасные/зажигательные взрываются только по отскокам,
        // а липучка — по таймеру прилипания. Если граната так и не встретила блок (бросок
        // в пустоту, лава, застревание), она падала бы вечно и никогда не взорвалась.
        // Покрывает и SMART, которому нужен тот же аварийный срок.
        if (!stuck && !exploded && this.tickCount > FAILSAFE_TICKS) {
            explode(false);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (level().isClientSide || exploded || stuck) return;
        if (grenadeType == null) {
            grenadeType = GrenadeType.valueOf(this.entityData.get(GRENADE_TYPE_ID));
        }

        BlockPos blockPos = result.getBlockPos();
        level().playSound(null, blockPos, ModSounds.BOUNCE_RANDOM.get(), SoundSource.NEUTRAL, 2.1F, 1.0F);

        if (grenadeType == GrenadeType.SLIME) {
            // Прилипает и держит фитиль: детонирует либо по таймеру, либо раньше,
            // если опора под ней пропала (см. supportGone).
            stick(result.getBlockPos(), result.getLocation());
        } else if (grenadeType == GrenadeType.SMART) {
            bounceCount++;
            if (bounceCount >= grenadeType.getMaxBounces() || this.tickCount > 200) {
                explode(false);
            } else {
                bounce(result);
            }
        } else {
            this.bounceCount++;
            if (this.bounceCount >= grenadeType.getMaxBounces()) {
                explode(blockPos);
            } else {
                bounce(result);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide || exploded || stuck) return;
        if (grenadeType == null) {
            grenadeType = GrenadeType.valueOf(this.entityData.get(GRENADE_TYPE_ID));
        }

        if (grenadeType == GrenadeType.SMART) {
            explode(true);
        } else if (grenadeType == GrenadeType.SLIME) {
            stickToEntity(result.getEntity());
        } else if (grenadeType.explodesOnEntity()) {
            explode(result.getEntity().blockPosition());
        }
    }

    private void bounce(BlockHitResult result) {
        Vec3 currentVelocity = this.getDeltaMovement();
        Vec3 hitNormal = Vec3.atLowerCornerOf(result.getDirection().getNormal());
        Vec3 reflectedVelocity = currentVelocity.subtract(hitNormal.scale(2 * currentVelocity.dot(hitNormal)));
        this.setDeltaMovement(reflectedVelocity.scale(grenadeType.getBounceMultiplier()));
        this.hasImpulse = true;
    }

    private void stick(BlockPos supportPos, Vec3 pos) {
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(pos.x, pos.y, pos.z);
        this.stuck = true;
        this.stuckTimer = BLOCK_STICK_FUSE_TICKS;
        this.setNoGravity(true);

        // Опорой считается именно тот блок, в который ударились, а не тот, в
        // который попадёт точка касания. Точка лежит на грани, и
        // BlockPos.containing() от неё почти всегда уезжает в соседний блок —
        // то есть в воздух. С таким якорём supportGone() срабатывал бы в тот же
        // тик, и граната рвалась сразу, не провисев и двух секунд.
        this.stuckBlockPos = supportPos.immutable();
        BlockState support = level().getBlockState(this.stuckBlockPos);
        this.stuckBlock = support.isAir() ? null : support.getBlock();
    }

    /**
     * Пропала ли опора, к которой прилипла граната.
     * <p>
     * Проверяется «на месте ли стоит тот самый блок». Отдельной проверки на
     * воздух не нужно и быть не должно: воздух — это тоже «уже не тот блок».
     * А вот когда опору записать не удалось, сравнивать не с чем, и граната
     * просто доживает до фитиля — иначе она рвалась бы в тот же тик.
     */
    private boolean supportGone() {
        if (stuckBlockPos == null || stuckBlock == null) {
            return false;
        }
        return !level().getBlockState(stuckBlockPos).is(stuckBlock);
    }

    private void stickToEntity(Entity entity) {
        this.stuck = true;
        this.stuckEntityId = entity.getId();
        this.setNoGravity(true);
        this.entityData.set(STUCK_ENTITY_ID, entity.getId());

        // Вычисляем смещение от центра моба так, чтобы граната касалась его хитбокса снаружи
        Vec3 mobCenter = entity.getBoundingBox().getCenter();
        Vec3 toGrenade = this.position().subtract(mobCenter);
        double dist = toGrenade.length();
        if (dist < 0.001) {
            toGrenade = new Vec3(0, 1, 0);
            dist = 1.0;
        }
        Vec3 dir = toGrenade.scale(1.0 / dist);

        // Расстояние от центра моба до точки касания
        double desiredDist = entity.getBbWidth() * 0.5 + this.getBbWidth() * 0.5;
        // Если граната уже дальше — не притягиваем внутрь
        desiredDist = Math.max(desiredDist, dist);

        Vec3 attachPos = mobCenter.add(dir.scale(desiredDist));
        this.stuckOffset = attachPos.subtract(mobCenter);

        // Корректируем Y чтобы граната "сидела" на поверхности, а не в центре
        this.setPos(attachPos.x, attachPos.y - this.getBbHeight() * 0.5 + entity.getBbHeight() * 0.5, attachPos.z);

        this.stuckTimer = ENTITY_STICK_FUSE_TICKS;
    }

    private void explode(boolean smartEntityHit) {
        if (exploded) return;
        exploded = true;
        Vec3 pos = this.position();
        Level level = level();
        float radius = grenadeType.getExplosionPower();

        // Тот же trd:grenade_explosion, что у ударных гранат. Раньше здесь стоял
        // ванильный entity.generic.explode, и обычная граната звучала как обычная
        // динамитная шашка Minecraft: водородная и гравитационная гремят
        // отдельно, а вся остальная линия была одинаковой и никак не отличалась от
        // взрыва чего угодно ещё в игре.
        Holder<SoundEvent> sound = ModSounds.GRENADE_EXPLOSION.getDelegate();

        switch (grenadeType) {
            case STANDARD -> ExplosionStandard.explode(level, pos, this.getOwner(), radius, grenadeType.getCustomDamage(), sound);
            case HE -> ExplosionHE.explode(level, pos, this.getOwner(), radius, grenadeType.getCustomDamage(), sound);
            case FIRE -> ExplosionFire.explode((ServerLevel) level, pos, this.getOwner(), radius, true);
            case SMART -> {
                if (smartEntityHit) {
                    ExplosionHE.explode(level, pos, this.getOwner(), SMART_CONTACT_RADIUS, 40.0f, sound);
                    ExplosionFire.explode((ServerLevel) level, pos, this.getOwner(), 2.0f, true);
                } else {
                    ExplosionStandard.explode(level, pos, this.getOwner(), radius, grenadeType.getCustomDamage(), sound);
                }
            }
            case SLIME -> ExplosionStandard.explode(level, pos, this.getOwner(), radius, grenadeType.getCustomDamage(), sound);
        }
        this.discard();
    }

    private void explode(BlockPos pos) {
        explode(false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BounceCount", this.bounceCount);
        tag.putString("GrenadeType", this.entityData.get(GRENADE_TYPE_ID));
        tag.putBoolean("Stuck", this.stuck);
        tag.putInt("StuckTimer", this.stuckTimer);
        tag.putBoolean("Exploded", this.exploded);
        tag.putInt("StuckEntityId", this.stuckEntityId);
        if (stuckBlockPos != null) {
            tag.putInt("StuckBlockX", stuckBlockPos.getX());
            tag.putInt("StuckBlockY", stuckBlockPos.getY());
            tag.putInt("StuckBlockZ", stuckBlockPos.getZ());
        }
        if (stuckOffset != null) {
            tag.putDouble("StuckOffsetX", stuckOffset.x);
            tag.putDouble("StuckOffsetY", stuckOffset.y);
            tag.putDouble("StuckOffsetZ", stuckOffset.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.bounceCount = tag.getInt("BounceCount");
        if (tag.contains("GrenadeType")) {
            this.entityData.set(GRENADE_TYPE_ID, tag.getString("GrenadeType"));
            this.grenadeType = GrenadeType.valueOf(tag.getString("GrenadeType"));
        }
        this.stuck = tag.getBoolean("Stuck");
        this.stuckTimer = tag.getInt("StuckTimer");
        this.exploded = tag.getBoolean("Exploded");
        this.stuckEntityId = tag.getInt("StuckEntityId");
        if (tag.contains("StuckBlockX")) {
            this.stuckBlockPos = new BlockPos(
                    tag.getInt("StuckBlockX"), tag.getInt("StuckBlockY"), tag.getInt("StuckBlockZ"));
            // Сам блок не сохраняем: после перезагрузки мира он мог смениться, и
            // сверяться надо с тем, что стоит на месте сейчас, а не с тем, что
            // стояло в момент прилипания.
            BlockState support = level().getBlockState(this.stuckBlockPos);
            this.stuckBlock = support.isAir() ? null : support.getBlock();
        }
        if (tag.contains("StuckOffsetX")) {
            this.stuckOffset = new Vec3(
                    tag.getDouble("StuckOffsetX"),
                    tag.getDouble("StuckOffsetY"),
                    tag.getDouble("StuckOffsetZ")
            );
        }
    }
}
