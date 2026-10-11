package com.trd.entity.weapons.grenades;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Базовый класс для всех гранат мода.
 * <p>
 * Обеспечивает:
 * 1. Честную физику лежания и коллизий (MoverType.SELF) — предотвращает проваливание сквозь блоки,
 *    но оставляет гранату подверженной внешним воздействиям (гравитация при сносе блока,
 *    течение воды, импульсы от взрывов).
 * 2. Плавную интерполяцию вращения (yaw, pitch, roll) между тиками для рендера с высокой частотой кадров (без рывков).
 * 3. Отслеживание импульса вращения от отскоков и переваливания с боку на бок.
 */
public abstract class AbstractGrenadeEntity extends ThrowableItemProjectile {

    protected static final EntityDataAccessor<Boolean> DATA_LANDED =
            SynchedEntityData.defineId(AbstractGrenadeEntity.class, EntityDataSerializers.BOOLEAN);

    protected static final float MIN_BOUNCE_SPEED = 0.1f;

    // Плавная интерполяция поворотов
    protected float prevVisualYaw;
    protected float visualYaw;
    protected float prevVisualPitch;
    protected float visualPitch;
    protected float prevVisualRoll;
    protected float visualRoll;
    protected float rollVelocity;

    public AbstractGrenadeEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public AbstractGrenadeEntity(EntityType<? extends ThrowableItemProjectile> entityType, LivingEntity thrower, Level level) {
        super(entityType, thrower, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LANDED, false);
    }

    public boolean isLanded() {
        return this.entityData.get(DATA_LANDED);
    }

    public void setLanded(boolean landed) {
        this.entityData.set(DATA_LANDED, landed);
        if (landed) {
            this.hasImpulse = true;
        }
    }

    @Override
    public void tick() {
        updateVisualRotation();

        if (isLanded()) {
            this.baseTick();
            tickLandedPhysics();
        } else {
            super.tick();
        }
    }

    /**
     * Физика лежащей/катящейся гранаты.
     * Не даёт гранате проваливаться сквозь блоки, но реагирует на поломку блоков,
     * воду и толчки.
     */
    protected void tickLandedPhysics() {
        Vec3 motion = this.getDeltaMovement();
        // Гравитация и легкое сопротивление воздуха
        motion = new Vec3(motion.x * 0.85D, motion.y - 0.04D, motion.z * 0.85D);
        this.setDeltaMovement(motion);

        // move() проверяет реальные коллизии с вокселями блоков в мире
        this.move(MoverType.SELF, motion);

        this.updateInWaterStateAndDoFluidPushing();

        if (this.onGround()) {
            Vec3 cur = this.getDeltaMovement();
            double hSpeed = Math.sqrt(cur.x * cur.x + cur.z * cur.z);
            if (hSpeed < 0.01D) {
                this.setDeltaMovement(0.0D, 0.0D, 0.0D);
            } else {
                this.setDeltaMovement(cur.x * 0.65D, 0.0D, cur.z * 0.65D);
            }
        }
    }

    /**
     * Вызывается при касании блока при малой скорости — переводит гранату в лежачее состояние.
     */
    protected void landOnBlock(BlockHitResult result) {
        setLanded(true);
        if (result.getDirection() == Direction.UP) {
            this.setPos(this.getX(), result.getLocation().y, this.getZ());
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.hasImpulse = true;
    }

    /**
     * Импульс вращения от отскока.
     */
    public void onBounceImpulse(float impactSpeed) {
        float kick = (float) (impactSpeed * 35.0F);
        if (this.random.nextBoolean()) kick = -kick;
        this.rollVelocity += kick;
    }

    /**
     * Обновление визуальных углов вращения каждый тик (на сервере и клиенте).
     */
    protected void updateVisualRotation() {
        prevVisualYaw = visualYaw;
        prevVisualPitch = visualPitch;
        prevVisualRoll = visualRoll;

        Vec3 motion = this.getDeltaMovement();
        double horizSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        double totalSpeed = motion.length();

        if (!isLanded()) {
            if (totalSpeed > 0.01D) {
                float targetYaw = (float) (Mth.atan2(motion.x, motion.z) * (180.0D / Math.PI));
                float targetPitch = (float) (Mth.atan2(motion.y, horizSpeed) * (180.0D / Math.PI));

                // Плавное следование за направлением (без мгновенного скачка на 180 при отскоке)
                visualYaw = Mth.rotLerp(0.35F, visualYaw, targetYaw);
                visualPitch = Mth.lerp(0.35F, visualPitch, targetPitch);
            }

            // Вращение в полёте
            float spinRate = (float) (totalSpeed * 35.0F) + rollVelocity;
            visualRoll += spinRate;
            rollVelocity *= 0.92F;
        } else {
            // В лежачем состоянии
            if (horizSpeed > 0.01D) {
                float targetYaw = (float) (Mth.atan2(motion.x, motion.z) * (180.0D / Math.PI));
                visualYaw = Mth.rotLerp(0.25F, visualYaw, targetYaw);

                // Качение по полу пропорционально пройденному расстоянию
                float rollRate = (float) (horizSpeed * 40.0F);
                visualRoll += rollRate;
            }

            // Ложится плашмя по pitch
            visualPitch = Mth.lerp(0.2F, visualPitch, 0.0F);

            // Затухание качания с боку на бок при остановке
            if (Math.abs(rollVelocity) > 0.01F) {
                visualRoll += rollVelocity;
                rollVelocity *= 0.8F;
            }
        }
    }

    public float getInterpolatedYaw(float partialTick) {
        return Mth.rotLerp(partialTick, prevVisualYaw, visualYaw);
    }

    public float getInterpolatedPitch(float partialTick) {
        return Mth.lerp(partialTick, prevVisualPitch, visualPitch);
    }

    public float getInterpolatedRoll(float partialTick) {
        return Mth.lerp(partialTick, prevVisualRoll, visualRoll);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Landed", isLanded());
        tag.putFloat("VisualRoll", visualRoll);
        tag.putFloat("VisualYaw", visualYaw);
        tag.putFloat("VisualPitch", visualPitch);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Landed")) {
            setLanded(tag.getBoolean("Landed"));
        }
        if (tag.contains("VisualRoll")) {
            visualRoll = tag.getFloat("VisualRoll");
            prevVisualRoll = visualRoll;
        }
        if (tag.contains("VisualYaw")) {
            visualYaw = tag.getFloat("VisualYaw");
            prevVisualYaw = visualYaw;
        }
        if (tag.contains("VisualPitch")) {
            visualPitch = tag.getFloat("VisualPitch");
            prevVisualPitch = visualPitch;
        }
    }
}
