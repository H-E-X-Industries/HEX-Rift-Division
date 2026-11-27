package com.trd.entity.industrial.drilling;

import com.trd.block.basic.ModBlocks;
import com.trd.entity.ModEntities;
import com.trd.sound.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Вылетевшая при выбросе/детонации скважины буровая труба (1×1×4).
 * Падает с физикой и подбирается правым кликом (возвращает предмет мультиблока трубы).
 */
public class LaunchedDrillPipeEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_SPIN_YAW =
            SynchedEntityData.defineId(LaunchedDrillPipeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SPIN_PITCH =
            SynchedEntityData.defineId(LaunchedDrillPipeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_YAW_RATE =
            SynchedEntityData.defineId(LaunchedDrillPipeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH_RATE =
            SynchedEntityData.defineId(LaunchedDrillPipeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_SETTLED =
            SynchedEntityData.defineId(LaunchedDrillPipeEntity.class, EntityDataSerializers.BOOLEAN);

    private float prevRenderYaw;
    private float renderYaw;
    private float prevRenderPitch;
    private float renderPitch;
    private int bounceCount = 0;

    public LaunchedDrillPipeEntity(EntityType<? extends LaunchedDrillPipeEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public static LaunchedDrillPipeEntity launchFromWellhead(Level level, Vec3 pos, Vec3 velocity,
                                                             float initYaw, float yawRate, float pitchRate) {
        LaunchedDrillPipeEntity entity = new LaunchedDrillPipeEntity(ModEntities.LAUNCHED_DRILL_PIPE.get(), level);
        entity.setPos(pos.x, pos.y, pos.z);
        entity.setDeltaMovement(velocity);
        entity.entityData.set(DATA_SPIN_YAW, initYaw);
        entity.entityData.set(DATA_SPIN_PITCH, 0.0f);
        entity.entityData.set(DATA_YAW_RATE, yawRate);
        entity.entityData.set(DATA_PITCH_RATE, pitchRate);
        entity.entityData.set(DATA_SETTLED, false);
        entity.renderYaw = initYaw;
        entity.prevRenderYaw = initYaw;
        entity.renderPitch = 0.0f;
        entity.prevRenderPitch = 0.0f;
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SPIN_YAW, 0.0f);
        builder.define(DATA_SPIN_PITCH, 0.0f);
        builder.define(DATA_YAW_RATE, 0.0f);
        builder.define(DATA_PITCH_RATE, 0.0f);
        builder.define(DATA_SETTLED, false);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.renderYaw = this.entityData.get(DATA_SPIN_YAW);
        this.prevRenderYaw = this.renderYaw;
        this.renderPitch = this.entityData.get(DATA_SPIN_PITCH);
        this.prevRenderPitch = this.renderPitch;
    }

    @Override
    public void tick() {
        super.tick();

        this.prevRenderYaw = this.renderYaw;
        this.prevRenderPitch = this.renderPitch;

        Vec3 motion = this.getDeltaMovement();
        if (!this.isNoGravity()) {
            motion = motion.add(0.0, -0.045, 0.0);
        }

        double prevY = motion.y;
        this.move(MoverType.SELF, motion);

        Vec3 after = this.getDeltaMovement();
        if (this.onGround()) {
            if (prevY < -0.18 && bounceCount < 3) {
                bounceCount++;
                after = new Vec3(after.x * 0.68, -prevY * 0.28, after.z * 0.68);
                if (!this.level().isClientSide) {
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            ModSounds.PICKAXE_HIT.get(), SoundSource.BLOCKS, 0.7f, 0.65f + this.random.nextFloat() * 0.2f);
                }
            } else {
                after = new Vec3(after.x * 0.65, 0.0, after.z * 0.65);
                if (after.horizontalDistanceSqr() < 0.0004 && !this.entityData.get(DATA_SETTLED)) {
                    if (!this.level().isClientSide) {
                        this.entityData.set(DATA_SETTLED, true);
                        this.entityData.set(DATA_SPIN_PITCH, 90.0f);
                        this.entityData.set(DATA_SPIN_YAW, this.renderYaw);
                    }
                }
            }
        } else {
            after = after.scale(0.985);
            if (!this.level().isClientSide && this.entityData.get(DATA_SETTLED)) {
                this.entityData.set(DATA_SETTLED, false);
            }
        }
        this.setDeltaMovement(after);

        if (this.entityData.get(DATA_SETTLED)) {
            this.renderPitch = Mth.lerp(0.35f, this.renderPitch, 90.0f);
        } else {
            this.renderYaw += this.entityData.get(DATA_YAW_RATE);
            this.renderPitch += this.entityData.get(DATA_PITCH_RATE);
            if (!this.level().isClientSide) {
                this.entityData.set(DATA_SPIN_YAW, this.renderYaw);
                this.entityData.set(DATA_SPIN_PITCH, this.renderPitch);
            }
        }
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            ItemStack stack = new ItemStack(ModBlocks.DRILL_PIPE.get());
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 0.9f);
            this.discard();
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    public float getRenderYaw(float partialTick) {
        return Mth.lerp(partialTick, prevRenderYaw, renderYaw);
    }

    public float getRenderPitch(float partialTick) {
        return Mth.lerp(partialTick, prevRenderPitch, renderPitch);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        float yaw = tag.getFloat("SpinYaw");
        float pitch = tag.getFloat("SpinPitch");
        this.entityData.set(DATA_SPIN_YAW, yaw);
        this.entityData.set(DATA_SPIN_PITCH, pitch);
        this.entityData.set(DATA_YAW_RATE, tag.getFloat("YawRate"));
        this.entityData.set(DATA_PITCH_RATE, tag.getFloat("PitchRate"));
        this.entityData.set(DATA_SETTLED, tag.getBoolean("Settled"));
        this.bounceCount = tag.getInt("Bounces");
        this.renderYaw = yaw;
        this.prevRenderYaw = yaw;
        this.renderPitch = pitch;
        this.prevRenderPitch = pitch;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("SpinYaw", this.entityData.get(DATA_SPIN_YAW));
        tag.putFloat("SpinPitch", this.entityData.get(DATA_SPIN_PITCH));
        tag.putFloat("YawRate", this.entityData.get(DATA_YAW_RATE));
        tag.putFloat("PitchRate", this.entityData.get(DATA_PITCH_RATE));
        tag.putBoolean("Settled", this.entityData.get(DATA_SETTLED));
        tag.putInt("Bounces", this.bounceCount);
    }
}
