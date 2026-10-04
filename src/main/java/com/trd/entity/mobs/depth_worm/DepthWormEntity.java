package com.trd.entity.mobs.depth_worm;

import com.trd.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import com.trd.api.hive.HiveNetwork;
import com.trd.api.hive.HiveNetworkManager;
import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.hive.DepthWormNestBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class DepthWormEntity extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Boolean> IS_ATTACKING = SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_FLYING = SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> KILLS = SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RAW_KILLS = SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> BOUND_NEST_ID = SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> IS_RETREATING = SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> LAST_EXIT_POS = SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> IS_COLONIST = SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> COLONIST_TARGET = SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> IS_ANGRY = SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.BOOLEAN);

    private int meleeCooldown = 0;
    protected int digCooldown = 0;
    public int ignoreFallDamageTicks = 0;
    public BlockPos nestPos;
    private BlockPos homePos;
    private BlockPos formerHivePos = null;
    private Runnable onDeathCallback = null;

    public DepthWormEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 15.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 2.5D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D)
                .add(Attributes.FALL_DAMAGE_MULTIPLIER, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_COLONIST, false);
        builder.define(COLONIST_TARGET, "");
        builder.define(IS_ATTACKING, false);
        builder.define(IS_FLYING, false);
        builder.define(IS_ANGRY, false);
        builder.define(KILLS, 0);
        builder.define(RAW_KILLS, 0);
        builder.define(BOUND_NEST_ID, "");
        builder.define(IS_RETREATING, false);
        builder.define(LAST_EXIT_POS, "");
    }

    public void setOnDeathCallback(Runnable callback) {
        this.onDeathCallback = callback;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (onDeathCallback != null) {
            onDeathCallback.run();
        }
    }

    public void setHomePos(BlockPos pos) {
        this.homePos = pos;
    }

    public BlockPos getHomePos() {
        return this.homePos;
    }

    public float getMaxJumpHeight() {
        return 3.0F;
    }

    public int getMaxJumpHorizontalRange() {
        return 3;
    }

    @Override
    public float maxUpStep() {
        return Math.max(super.maxUpStep(), getMaxJumpHeight());
    }

    public void bindToNest(BlockPos nestPos) {
        if (nestPos != null) {
            this.entityData.set(BOUND_NEST_ID, nestPos.asLong() + "");
            this.nestPos = nestPos;
            this.formerHivePos = nestPos;
        }
    }

    public BlockPos getFormerHivePos() {
        return this.formerHivePos;
    }

    public void setFormerHivePos(BlockPos pos) {
        this.formerHivePos = pos;
    }


    @Override
    protected PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level) {
            @Override
            protected PathFinder createPathFinder(int pMaxVisitedNodes) {
                this.nodeEvaluator = new WalkNodeEvaluator() {
                    @Override
                    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
                        PathType type = super.getPathType(context, x, y, z);
                        if (type == PathType.BLOCKED) return type;
                        BlockPos checkPos = new BlockPos(x, y, z);
                        BlockState state = context.getBlockState(checkPos);
                        if (isPathBlockingPlant(context.level(), state, checkPos)) {
                            return PathType.BLOCKED;
                        }
                        return type;
                    }

                    private boolean isPathBlockingPlant(BlockGetter level, BlockState state, BlockPos pos) {
                        Block block = state.getBlock();
                        if (!(block instanceof BushBlock)) return false;
                        return !state.getCollisionShape(level, pos).isEmpty();
                    }
                };
                this.nodeEvaluator.setCanPassDoors(true);
                this.nodeEvaluator.setCanFloat(true);
                return new PathFinder(this.nodeEvaluator, 4096);
            }
        };
    }

    public BlockPos getBoundNestPos() {
        String id = this.entityData.get(BOUND_NEST_ID);
        if (id == null || id.isEmpty()) return null;
        try {
            return BlockPos.of(Long.parseLong(id));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void clearNestBinding() {
        this.entityData.set(BOUND_NEST_ID, "");
        this.nestPos = null;
    }

    public boolean isBoundToNest(BlockPos pos) {
        BlockPos bound = getBoundNestPos();
        return bound != null && bound.equals(pos);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayerSq) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kills", this.getKills());
        tag.putInt("RawKills", this.getRawKills());
        if (homePos != null) {
            tag.putLong("HomePos", homePos.asLong());
        }
        BlockPos boundNest = getBoundNestPos();
        if (boundNest != null) {
            tag.putLong("BoundNest", boundNest.asLong());
        }
        tag.putBoolean("Retreating", this.isRetreating());
        BlockPos lastExit = getLastExitPos();
        if (lastExit != null) {
            tag.putLong("LastExitPos", lastExit.asLong());
        }
        tag.putBoolean("IsColonist", this.isColonist());
        BlockPos colonistTarget = getColonistTarget();
        if (colonistTarget != null) {
            tag.putLong("ColonistTarget", colonistTarget.asLong());
        }
        if (formerHivePos != null) {
            tag.putLong("FormerHivePos", formerHivePos.asLong());
        }
        tag.putInt("DigCooldown", this.digCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(KILLS, tag.getInt("Kills"));
        this.entityData.set(RAW_KILLS, tag.getInt("RawKills"));
        if (tag.contains("DigCooldown")) {
            this.digCooldown = tag.getInt("DigCooldown");
        }
        if (tag.contains("HomePos")) {
            homePos = BlockPos.of(tag.getLong("HomePos"));
        } else {
            homePos = null;
        }
        if (tag.contains("BoundNest")) {
            BlockPos bound = BlockPos.of(tag.getLong("BoundNest"));
            bindToNest(bound);
        }
        if (tag.contains("FormerHivePos")) {
            formerHivePos = BlockPos.of(tag.getLong("FormerHivePos"));
        }
        if (tag.contains("Retreating")) {
            this.setRetreating(tag.getBoolean("Retreating"));
        }
        if (tag.contains("LastExitPos")) {
            setLastExitPos(BlockPos.of(tag.getLong("LastExitPos")));
        }
        if (tag.contains("IsColonist")) {
            this.entityData.set(IS_COLONIST, tag.getBoolean("IsColonist"));
        }
        if (tag.contains("ColonistTarget")) {
            this.entityData.set(COLONIST_TARGET, tag.getLong("ColonistTarget") + "");
        }
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null) {
            if (target.getType() == EntityType.BAT || target.isInLava() || target.isInWater()) {
                return;
            }
            BlockState below = target.level().getBlockState(target.blockPosition().below());
            if (below.is(net.minecraft.world.level.block.Blocks.LAVA)) {
                return;
            }
            if (this.isColonist() && target != this.getLastHurtByMob()) {
                return;
            }
        }
        super.setTarget(target);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof DepthWormEntity) return false;
        if (target.getType() == EntityType.BAT) return false;
        if (target.isInLava()) return false;
        BlockState belowTarget = target.level().getBlockState(target.blockPosition().below());
        if (belowTarget.is(net.minecraft.world.level.block.Blocks.LAVA)) return false;

        if (this.isColonist()) {
            LivingEntity hurtBy = this.getLastHurtByMob();
            return hurtBy != null && hurtBy == target && super.canAttack(target);
        }
        return super.canAttack(target);
    }

    public boolean isAngry() {
        return this.entityData.get(IS_ANGRY);
    }

    public void setAttacking(boolean attacking) {
        this.entityData.set(IS_ATTACKING, attacking);
    }

    public boolean isAttacking() {
        return this.entityData.get(IS_ATTACKING);
    }

    public void setFlying(boolean flying) {
        this.entityData.set(IS_FLYING, flying);
    }

    public boolean isFlying() {
        return this.entityData.get(IS_FLYING);
    }

    public void addKill() {
        this.entityData.set(KILLS, this.getKills() + 1);
    }

    public int getRawKills() {
        return this.entityData.get(RAW_KILLS);
    }

    public void setRawKills(int kills) {
        this.entityData.set(RAW_KILLS, kills);
    }

    public void addRawKill() {
        this.entityData.set(RAW_KILLS, getRawKills() + 1);
    }

    public boolean isRetreating() {
        return this.entityData.get(IS_RETREATING);
    }

    public void setRetreating(boolean v) {
        this.entityData.set(IS_RETREATING, v);
    }

    public int getRetreatKillThreshold() {
        return 10;
    }

    public int getDigCooldown() {
        return this.digCooldown;
    }

    public void setDigCooldown(int cooldown) {
        this.digCooldown = cooldown;
    }

    protected boolean isPreparingJump = false;

    public boolean isPreparingJump() {
        return this.isPreparingJump;
    }

    public void setPreparingJump(boolean preparing) {
        this.isPreparingJump = preparing;
    }

    public void setKills(int kills) {
        this.entityData.set(KILLS, kills);
    }

    public void setLastExitPos(BlockPos pos) {
        this.entityData.set(LAST_EXIT_POS, pos != null ? pos.asLong() + "" : "");
    }

    public BlockPos getLastExitPos() {
        String s = this.entityData.get(LAST_EXIT_POS);
        if (s == null || s.isEmpty()) return null;
        try {
            return BlockPos.of(Long.parseLong(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected void checkBrutalTransformation() {
        if (getRawKills() >= 5) {
            transformToBrutal();
        }
    }

    public boolean isOrphaned() {
        if (this.isColonist()) return false;
        BlockPos bound = getBoundNestPos();
        if (bound == null) return true;
        if (this.level().isLoaded(bound)) {
            return !this.level().getBlockState(bound).is(ModBlocks.DEPTH_WORM_NEST.get());
        }
        return false;
    }

    protected void checkHiveReconstruction() {
        if (this.level().isClientSide || this.tickCount % 20 != 0) return;
        if (this.isColonist() || !this.onGround() || this.isInWater() || this.isInLava()) return;
        if (!isOrphaned()) return;

        BlockPos bound = getBoundNestPos();
        if (bound != null && this.level().isLoaded(bound)) {
            if (!this.level().getBlockState(bound).is(ModBlocks.DEPTH_WORM_NEST.get())) {
                if (this.formerHivePos == null) this.formerHivePos = bound;
                clearNestBinding();
            }
        }

        // Find nearby living orphaned worms within 36 blocks
        List<DepthWormEntity> nearbyOrphans = this.level().getEntitiesOfClass(
                DepthWormEntity.class,
                this.getBoundingBox().inflate(36.0D),
                w -> w.isAlive() && w.isOrphaned() && !w.isInWater() && !w.isInLava()
        );

        int totalKills = 0;
        DepthWormEntity founder = this;
        for (DepthWormEntity orphan : nearbyOrphans) {
            totalKills += orphan.getKills();
            if (orphan.getKills() > founder.getKills()) {
                founder = orphan;
            }
        }

        // 25 kills required to establish a new hive core
        if (totalKills < 25 || founder != this) return;

        BlockPos floorPos = this.blockPosition();
        BlockState s = this.level().getBlockState(floorPos);
        if (s.isAir()) {
            floorPos = floorPos.below();
            s = this.level().getBlockState(floorPos);
        }

        if (s.isAir() || !s.getFluidState().isEmpty() || s.is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
            return;
        }

        // Consume 25 points from the group
        int needed = 25;
        for (DepthWormEntity orphan : nearbyOrphans) {
            int take = Math.min(needed, orphan.getKills());
            orphan.setKills(orphan.getKills() - take);
            needed -= take;
            if (needed <= 0) break;
        }

        // Place new nest (Hive Core)
        this.level().setBlock(floorPos, ModBlocks.DEPTH_WORM_NEST.get().defaultBlockState(), 3);
        UUID newNetId = UUID.randomUUID();

        BlockEntity be = this.level().getBlockEntity(floorPos);
        if (be instanceof DepthWormNestBlockEntity nest) {
            nest.setNetworkId(newNetId);
            HiveNetworkManager manager = HiveNetworkManager.get(this.level());
            if (manager != null) {
                manager.addNode(newNetId, floorPos, true);
                HiveNetwork network = manager.getNetwork(newNetId);
                if (network != null) {
                    network.addPoints(5, this.level());
                }
            }
        }

        // Spread hive soil patch around nest
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos soilPos = floorPos.offset(dx, 0, dz);
                BlockState soilState = this.level().getBlockState(soilPos);
                if (!soilState.isAir() && soilState.getFluidState().isEmpty() && !soilState.is(ModBlocks.DEPTH_WORM_NEST.get())) {
                    this.level().setBlock(soilPos, ModBlocks.HIVE_SOIL.get().defaultBlockState(), 3);
                    HiveNetworkManager manager = HiveNetworkManager.get(this.level());
                    if (manager != null) {
                        manager.addNode(newNetId, soilPos, false);
                    }
                }
            }
        }

        // Visual and sound effects
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, floorPos.getX() + 0.5, floorPos.getY() + 1.0, floorPos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
            serverLevel.sendParticles(ParticleTypes.SQUID_INK, floorPos.getX() + 0.5, floorPos.getY() + 1.0, floorPos.getZ() + 0.5, 30, 0.6, 0.6, 0.6, 0.1);
            serverLevel.sendParticles(ParticleTypes.ASH, floorPos.getX() + 0.5, floorPos.getY() + 1.0, floorPos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.05);
        }
        this.level().playSound(null, floorPos, net.minecraft.sounds.SoundEvents.SCULK_BLOCK_SPREAD, net.minecraft.sounds.SoundSource.BLOCKS, 1.5F, 0.8F);

        // Alert all orphaned worms in a wide 96-block radius to converge on the new hive!
        final BlockPos finalNestPos = floorPos;
        List<DepthWormEntity> allOrphans = this.level().getEntitiesOfClass(
                DepthWormEntity.class,
                new AABB(floorPos).inflate(96.0D),
                w -> w.isAlive() && w.isOrphaned()
        );

        for (DepthWormEntity orphan : allOrphans) {
            orphan.bindToNest(finalNestPos);
            orphan.setRetreating(true);
            orphan.setTarget(null);
            if (orphan.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, orphan.getX(), orphan.getY() + 0.5, orphan.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
            }
        }
    }

    public boolean canWormBreak(BlockPos pos) {
        if (!this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }

        BlockState state = this.level().getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }

        // Do not break hive blocks
        if (state.is(ModBlocks.DEPTH_WORM_NEST.get()) ||
            state.is(ModBlocks.HIVE_SOIL.get()) ||
            state.is(ModBlocks.DEPTH_WORM_NEST_DEAD.get()) ||
            state.is(ModBlocks.HIVE_SOIL_DEAD.get()) ||
            state.is(ModBlocks.HIVE_ROOTS.get())) {
            return false;
        }

        // Never break the block directly under any living worm
        AABB aboveBox = new AABB(pos.above());
        List<DepthWormEntity> wormsOnTop = this.level().getEntitiesOfClass(DepthWormEntity.class, aboveBox, Entity::isAlive);
        if (!wormsOnTop.isEmpty()) {
            return false;
        }
        if (pos.getX() == this.getBlockX() && pos.getZ() == this.getBlockZ() && pos.getY() <= this.getBlockY()) {
            return false;
        }

        // Safety: Do NOT break block if lava or water is directly BELOW
        BlockPos below = pos.below();
        FluidState belowFluid = this.level().getFluidState(below);
        if (belowFluid.is(FluidTags.LAVA) || belowFluid.is(FluidTags.WATER) ||
            this.level().getBlockState(below).is(Blocks.LAVA) ||
            this.level().getBlockState(below).is(Blocks.WATER)) {
            return false;
        }

        // Safety: Do NOT break block if lava or water is directly ABOVE
        FluidState aboveFluid = this.level().getFluidState(pos.above());
        if (aboveFluid.is(FluidTags.LAVA) || aboveFluid.is(FluidTags.WATER) ||
            this.level().getBlockState(pos.above()).is(Blocks.LAVA) ||
            this.level().getBlockState(pos.above()).is(Blocks.WATER)) {
            return false;
        }

        // Safety: Do NOT break block if lava or water is adjacent
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(dir);
            FluidState fluid = this.level().getFluidState(neighbor);
            if (fluid.is(FluidTags.LAVA) || fluid.is(FluidTags.WATER) ||
                this.level().getBlockState(neighbor).is(Blocks.LAVA) ||
                this.level().getBlockState(neighbor).is(Blocks.WATER)) {
                return false;
            }
        }

        float hardness = state.getDestroySpeed(this.level(), pos);
        // Deepslate hardness is 3.0F, cobbled deepslate is 3.5F.
        // Block must not be unbreakable (hardness < 0) and not harder than deepslate (<= 3.5F).
        return hardness >= 0.0F && hardness <= 3.5F;
    }

    @Nullable
    protected BlockPos findBlockToDig(LivingEntity target) {
        BlockPos wormPos = this.blockPosition();
        BlockPos targetPos = target.blockPosition();

        int dx = targetPos.getX() - wormPos.getX();
        int dy = targetPos.getY() - wormPos.getY();
        int dz = targetPos.getZ() - wormPos.getZ();

        // If target is within 3 blocks on the same level and directly visible, no digging needed
        double distSq = this.distanceToSqr(target);
        if (distSq <= 9.0 && Math.abs(dy) <= 1) {
            Vec3 wormEye = this.getEyePosition();
            Vec3 targetEye = target.getEyePosition();
            ClipContext clip = new ClipContext(wormEye, targetEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this);
            if (this.level().clip(clip).getType() == HitResult.Type.MISS) {
                return null;
            }
        }

        // 1. Determine primary horizontal step direction
        int stepX = 0;
        int stepZ = 0;
        if (Math.abs(dx) >= Math.abs(dz) && Math.abs(dx) > 0) {
            stepX = Integer.compare(dx, 0);
        } else if (Math.abs(dz) > 0) {
            stepZ = Integer.compare(dz, 0);
        } else {
            Direction facing = this.getDirection();
            stepX = facing.getStepX();
            stepZ = facing.getStepZ();
        }

        // Try primary direction ONLY if an open cavity / cave exists behind the thin wall (2-3 blocks)
        if (hasAirPocketBehindWall(wormPos, stepX, stepZ, dy, target)) {
            BlockPos candidate = findStaircaseBlockInDirection(wormPos, stepX, stepZ, dy);
            if (candidate != null) return candidate;
        }

        // Try alternative horizontal direction if primary lacks air pocket or is blocked
        int altX = 0;
        int altZ = 0;
        if (stepX != 0) {
            altZ = (dz != 0) ? Integer.compare(dz, 0) : 1;
        } else {
            altX = (dx != 0) ? Integer.compare(dx, 0) : 1;
        }
        if (hasAirPocketBehindWall(wormPos, altX, altZ, dy, target)) {
            BlockPos candidate = findStaircaseBlockInDirection(wormPos, altX, altZ, dy);
            if (candidate != null) return candidate;
        }

        // Try opposite of alternative
        if (hasAirPocketBehindWall(wormPos, -altX, -altZ, dy, target)) {
            BlockPos candidate = findStaircaseBlockInDirection(wormPos, -altX, -altZ, dy);
            if (candidate != null) return candidate;
        }

        return null;
    }

    /**
     * Checks if there is an open cavity / cave (air pocket with space) within 2-4 blocks
     * behind the wall in direction (sx, sz, dy).
     * This ensures worms only break through thin barriers (2-3 blocks) separating caves,
     * and do not dig into endless solid mountains.
     */
    protected boolean hasAirPocketBehindWall(BlockPos start, int sx, int sz, int dy, LivingEntity target) {
        if (sx == 0 && sz == 0) return false;

        BlockPos targetPos = target.blockPosition();

        // Check distances 2, 3, 4 (thickness of solid wall is dist - 1, so 1 to 3 blocks)
        for (int dist = 2; dist <= 4; dist++) {
            int vertOffset = 0;
            if (dy > 1) {
                vertOffset = Math.min(dist, dy);
            } else if (dy < -1) {
                vertOffset = Math.max(-dist, dy);
            }

            BlockPos checkPos = start.offset(sx * dist, vertOffset, sz * dist);

            // Is checkPos directly at or near target?
            if (checkPos.distSqr(targetPos) <= 9.0) {
                return true;
            }

            // Check if checkPos is an open air cavity
            if (isOpenCavity(checkPos) || isOpenCavity(checkPos.above()) || isOpenCavity(checkPos.below())) {
                return true;
            }
        }

        return false;
    }

    private boolean isOpenCavity(BlockPos pos) {
        Level level = this.level();
        BlockState state = level.getBlockState(pos);
        if (!state.isAir()) return false;
        if (!level.getFluidState(pos).isEmpty()) return false;

        // Needs at least 2 blocks of air (mob height / clearance)
        BlockState aboveState = level.getBlockState(pos.above());
        if (!aboveState.isAir() && !aboveState.getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }

        // Must not be in lava or water
        if (!level.getFluidState(pos.above()).isEmpty()) return false;

        return true;
    }

    @Nullable
    protected BlockPos findStaircaseBlockInDirection(BlockPos wormPos, int sx, int sz, int dy) {
        if (sx == 0 && sz == 0) return null;

        for (int dist = 1; dist <= 2; dist++) {
            BlockPos col = wormPos.offset(sx * dist, 0, sz * dist);

            if (dy <= -1) {
                // Staircase DOWN:
                // Step drops by 1 block per horizontal block step.
                // Floor at col.below(dist + 1) MUST REMAIN SOLID (acts as the stair step!).
                // Clear upper clearance (headroom) and lower clearance (walking space).
                BlockPos upper = col.below(dist - 1);
                BlockPos lower = col.below(dist);

                if (canWormBreak(upper)) {
                    return upper;
                }
                if (canWormBreak(lower)) {
                    return lower;
                }
            } else if (dy >= 1) {
                // Staircase UP:
                // Step rises by 1 block per horizontal block step.
                // Floor at col.above(dist - 1) MUST REMAIN SOLID (acts as the stair step!).
                // Clear lower clearance (walking space) and upper clearance (headroom).
                BlockPos lower = col.above(dist);
                BlockPos upper = col.above(dist + 1);

                if (canWormBreak(lower)) {
                    return lower;
                }
                if (canWormBreak(upper)) {
                    return upper;
                }
            } else {
                // Horizontal Tunnel:
                // Floor at col.below() MUST REMAIN SOLID.
                // Clear lower clearance (walking space) and upper clearance (headroom).
                BlockPos lower = col;
                BlockPos upper = col.above();

                if (canWormBreak(lower)) {
                    return lower;
                }
                if (canWormBreak(upper)) {
                    return upper;
                }
            }
        }

        return null;
    }

    protected void tickSwarmDigging() {
        if (this.level().isClientSide) return;
        if (this.digCooldown > 0) return;
        if (this.isColonist() || this.isRetreating() || this.isFlying() || !this.onGround()) return;
        if (this.isPreparingJump()) return;
        if (this instanceof DepthWormBrutalEntity brutal && (brutal.isImpaling() || brutal.isPreparingJump())) return;

        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        if (this.distanceToSqr(target) > 48.0 * 48.0) return;

        // If the worm can see the target directly through open air, NEVER dig!
        if (this.hasLineOfSight(target)) {
            return;
        }

        // Must be physically stopped by a solid wall!
        if (!this.horizontalCollision) {
            return;
        }

        // If navigation has a valid, non-done path that can reach the target, walk along it!
        net.minecraft.world.level.pathfinder.Path currentPath = this.getNavigation().getPath();
        if (currentPath != null && !currentPath.isDone() && currentPath.canReach()) {
            return;
        }

        // 1. Swarm check: at least 5 worms in a 4-block radius
        List<DepthWormEntity> nearbyWorms = this.level().getEntitiesOfClass(
                DepthWormEntity.class,
                this.getBoundingBox().inflate(4.0D),
                w -> w.isAlive() && w.getTarget() != null && !w.isColonist() && !w.isRetreating()
        );

        if (nearbyWorms.size() < 5) return;

        // 2. Leader / digger election: worm closest to the target
        DepthWormEntity leadDigger = this;
        double myDistSq = this.distanceToSqr(target);
        for (DepthWormEntity other : nearbyWorms) {
            if (other != this && other.getTarget() != null) {
                double otherDistSq = other.distanceToSqr(other.getTarget());
                if (otherDistSq < myDistSq) {
                    leadDigger = other;
                    myDistSq = otherDistSq;
                }
            }
        }

        if (leadDigger != this) return;

        // 3. Find block to dig towards target
        BlockPos blockToBreak = findBlockToDig(target);
        if (blockToBreak == null) return;
        if (blockToBreak.equals(this.blockPosition().below())) return;

        // 4. Destroy the block
        BlockState brokenState = this.level().getBlockState(blockToBreak);
        boolean destroyed = this.level().destroyBlock(blockToBreak, true, this);
        if (destroyed) {
            this.swing(InteractionHand.MAIN_HAND);
            this.setAttacking(true);

            // Sync 20-tick (1 second) cooldown to all nearby worms in 6-block radius
            List<DepthWormEntity> localCluster = this.level().getEntitiesOfClass(
                    DepthWormEntity.class,
                    this.getBoundingBox().inflate(6.0D),
                    w -> w.isAlive()
            );
            for (DepthWormEntity worm : localCluster) {
                worm.digCooldown = Math.max(worm.digCooldown, 20);
                if (worm.getNavigation().getPath() != null) {
                    worm.getNavigation().recomputePath();
                }
            }

            // Visual break particles
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, brokenState),
                        blockToBreak.getX() + 0.5, blockToBreak.getY() + 0.5, blockToBreak.getZ() + 0.5,
                        16, 0.3, 0.3, 0.3, 0.15);
            }
        }
    }

    public boolean tryDigTowardsHive(BlockPos nestPos) {
        if (this.level().isClientSide) return false;
        if (this.digCooldown > 0) return false;
        if (this.isColonist() || this.isFlying() || !this.onGround()) return false;
        if (this.isPreparingJump()) return false;
        if (this instanceof DepthWormBrutalEntity brutal && (brutal.isImpaling() || brutal.isPreparingJump())) return false;
        // Never dig towards hive if actively engaged with a living target!
        if (this.getTarget() != null && this.getTarget().isAlive()) return false;
        if (nestPos == null) return false;

        // Must be blocked by horizontal collision!
        if (!this.horizontalCollision) return false;

        if (this.distanceToSqr(nestPos.getX() + 0.5, nestPos.getY() + 0.5, nestPos.getZ() + 0.5) > 96.0 * 96.0) {
            return false;
        }

        // Leader election among nearby worms returning to hive:
        List<DepthWormEntity> nearbyCluster = this.level().getEntitiesOfClass(
                DepthWormEntity.class,
                this.getBoundingBox().inflate(6.0D),
                w -> w.isAlive() && !w.isColonist() && (w.isRetreating() || w.getTarget() == null)
        );

        DepthWormEntity leader = this;
        double myDistSq = this.distanceToSqr(nestPos.getX() + 0.5, nestPos.getY() + 0.5, nestPos.getZ() + 0.5);
        for (DepthWormEntity other : nearbyCluster) {
            double otherDistSq = other.distanceToSqr(nestPos.getX() + 0.5, nestPos.getY() + 0.5, nestPos.getZ() + 0.5);
            if (otherDistSq < myDistSq) {
                leader = other;
                myDistSq = otherDistSq;
            }
        }

        if (leader != this) return false;

        // Find block to dig towards hive
        BlockPos blockToBreak = findHiveBlockToDig(nestPos);
        if (blockToBreak == null) return false;
        if (blockToBreak.equals(this.blockPosition().below())) return false;

        BlockState brokenState = this.level().getBlockState(blockToBreak);
        boolean destroyed = this.level().destroyBlock(blockToBreak, true, this);
        if (destroyed) {
            this.swing(InteractionHand.MAIN_HAND);

            // Sync 20-tick cooldown to nearby worms
            for (DepthWormEntity worm : nearbyCluster) {
                worm.digCooldown = Math.max(worm.digCooldown, 20);
                if (worm.getNavigation().getPath() != null) {
                    worm.getNavigation().recomputePath();
                }
            }

            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, brokenState),
                        blockToBreak.getX() + 0.5, blockToBreak.getY() + 0.5, blockToBreak.getZ() + 0.5,
                        16, 0.3, 0.3, 0.3, 0.15);
            }
            return true;
        }

        return false;
    }

    @Nullable
    protected BlockPos findHiveBlockToDig(BlockPos nestPos) {
        BlockPos wormPos = this.blockPosition();

        int dx = nestPos.getX() - wormPos.getX();
        int dy = nestPos.getY() - wormPos.getY();
        int dz = nestPos.getZ() - wormPos.getZ();

        int dirX = Integer.compare(dx, 0);
        int dirZ = Integer.compare(dz, 0);

        // 1. Check for diagonal slit / corner blocks at the cave entrance:
        // When worms tunnel diagonally towards a cave, vanilla pathfinder allows diagonal traversal,
        // leaving the two orthogonal corner blocks and/or threshold sill block intact.
        // This forms a 1-block diagonal slit where worms get stuck.
        if (dirX != 0 && dirZ != 0) {
            BlockPos cornerX = wormPos.offset(dirX, 0, 0);
            BlockPos cornerZ = wormPos.offset(0, 0, dirZ);
            BlockPos diagPos = wormPos.offset(dirX, 0, dirZ);

            // If diagPos has air (entering cave), but cornerX or cornerZ is solid:
            if (!isSolidBlock(diagPos.above(1)) || !isSolidBlock(diagPos)) {
                if (canWormBreak(cornerX.above(1))) return cornerX.above(1);
                if (canWormBreak(cornerX)) return cornerX;
                if (canWormBreak(cornerZ.above(1))) return cornerZ.above(1);
                if (canWormBreak(cornerZ)) return cornerZ;
            }

            // If diagPos itself is solid and blocking diagonal passage
            if (canWormBreak(diagPos.above(1))) return diagPos.above(1);
            if (canWormBreak(diagPos)) return diagPos;
        }

        // 2. Check facing direction if colliding horizontally
        Direction facing = this.getDirection();
        BlockPos facingPos = wormPos.relative(facing);
        if (canWormBreak(facingPos.above(1))) return facingPos.above(1);
        if (canWormBreak(facingPos)) {
            // Only break facingPos at feet level if its above is air (sill block at entrance) or we need staircase down
            if (!isSolidBlock(facingPos.above(1)) || dy < 0) {
                return facingPos;
            }
        }

        // 3. Primary horizontal step direction
        int stepX = 0;
        int stepZ = 0;
        if (Math.abs(dx) >= Math.abs(dz) && Math.abs(dx) > 0) {
            stepX = Integer.compare(dx, 0);
        } else if (Math.abs(dz) > 0) {
            stepZ = Integer.compare(dz, 0);
        } else {
            stepX = facing.getStepX();
            stepZ = facing.getStepZ();
        }

        // Try primary direction
        BlockPos candidate = findHiveBlockInDirection(wormPos, stepX, stepZ, dy);
        if (candidate != null) return candidate;

        // Try alternative horizontal direction
        int altX = 0;
        int altZ = 0;
        if (stepX != 0) {
            altZ = (dz != 0) ? Integer.compare(dz, 0) : 1;
        } else {
            altX = (dx != 0) ? Integer.compare(dx, 0) : 1;
        }
        candidate = findHiveBlockInDirection(wormPos, altX, altZ, dy);
        if (candidate != null) return candidate;

        // Try opposite of alternative
        candidate = findHiveBlockInDirection(wormPos, -altX, -altZ, dy);
        if (candidate != null) return candidate;

        return null;
    }

    @Nullable
    protected BlockPos findHiveBlockInDirection(BlockPos wormPos, int sx, int sz, int dy) {
        if (sx == 0 && sz == 0) return null;

        BlockPos frontCol = wormPos.offset(sx, 0, sz);

        // If frontCol is solid, but frontCol.above(1) and frontCol.above(2) are AIR:
        // This is a 1-block sill at the threshold of an open cave!
        // Break frontCol so worms do not leave 1 block at the end!
        if (isSolidBlock(frontCol) && !isSolidBlock(frontCol.above(1)) && !isSolidBlock(frontCol.above(2))) {
            if (canWormBreak(frontCol)) return frontCol;
        }

        if (dy > 0) {
            // Staircase UP towards hive:
            // 1. Head clearance above worm: if stepping up requires head clearance, clear it!
            BlockPos headPos2 = wormPos.above(2);
            if (canWormBreak(headPos2) && (isSolidBlock(frontCol) || isSolidBlock(frontCol.above(1)))) {
                return headPos2;
            }

            // 2. Clear dist = 1 step
            BlockPos frontUpper = frontCol.above(2);
            BlockPos frontLower = frontCol.above(1);
            if (canWormBreak(frontUpper)) return frontUpper;
            if (canWormBreak(frontLower)) return frontLower;

            // If frontCol at feet level is blocking and frontCol.above(1) is air
            if (canWormBreak(frontCol) && !isSolidBlock(frontCol.above(1))) return frontCol;

            // 3. Clear dist = 2 step
            BlockPos front2 = wormPos.offset(sx * 2, 0, sz * 2);
            BlockPos front2High = front2.above(3);
            BlockPos front2Upper = front2.above(2);
            BlockPos front2Lower = front2.above(1);
            if (canWormBreak(front2High)) return front2High;
            if (canWormBreak(front2Upper)) return front2Upper;
            if (canWormBreak(front2Lower)) return front2Lower;
            if (canWormBreak(front2) && !isSolidBlock(front2.above(1))) return front2;
        } else if (dy < 0) {
            // Staircase DOWN towards hive:
            for (int dist = 1; dist <= 2; dist++) {
                BlockPos col = wormPos.offset(sx * dist, 0, sz * dist);
                BlockPos upper = col.below(dist - 1);
                BlockPos lower = col.below(dist);
                if (canWormBreak(upper)) return upper;
                if (canWormBreak(lower)) return lower;
            }
        } else {
            // Horizontal tunnel towards hive:
            for (int dist = 1; dist <= 2; dist++) {
                BlockPos col = wormPos.offset(sx * dist, 0, sz * dist);
                if (canWormBreak(col.above())) return col.above();
                if (canWormBreak(col)) return col;
            }
        }

        return null;
    }

    protected void tickHiveReturnDigging() {
        if (this.level().isClientSide) return;
        if (this.digCooldown > 0) return;
        if (this.isColonist() || this.isFlying() || !this.onGround()) return;
        if (this.isPreparingJump()) return;
        if (this instanceof DepthWormBrutalEntity brutal && (brutal.isImpaling() || brutal.isPreparingJump())) return;

        // Only dig to hive if retreating or idle without target, and has a bound hive
        if (this.getTarget() != null && this.getTarget().isAlive()) return;

        BlockPos bound = this.getBoundNestPos();
        if (bound == null) return;

        // If the worm has a valid reaching path and is not colliding horizontally, it is walking through open air!
        net.minecraft.world.level.pathfinder.Path path = this.getNavigation().getPath();
        if (path != null && !path.isDone() && path.canReach() && !this.horizontalCollision) {
            return;
        }

        // Must be blocked by horizontal collision
        if (!this.horizontalCollision) return;

        // Try to dig towards hive!
        tryDigTowardsHive(bound);
    }

    public boolean isColonist() {
        return this.entityData.get(IS_COLONIST);
    }

    public void setColonist(boolean colonist, @Nullable BlockPos target) {
        this.entityData.set(IS_COLONIST, colonist);
        this.entityData.set(COLONIST_TARGET, target != null ? target.asLong() + "" : "");
        if (colonist) {
            this.setTarget(null);
            this.setAttacking(false);
            this.setRetreating(false);
        }
    }

    @Nullable
    public BlockPos getColonistTarget() {
        String s = this.entityData.get(COLONIST_TARGET);
        if (s == null || s.isEmpty()) return null;
        try {
            return BlockPos.of(Long.parseLong(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected void transformToBrutal() {
        if (this.level().isClientSide) return;

        Level level = this.level();
        Vec3 pos = this.position();

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.EXPLOSION,
                    pos.x, pos.y + 0.3, pos.z,
                    2,
                    0.15, 0.15, 0.15,
                    0.02
            );
            serverLevel.sendParticles(
                    ParticleTypes.LARGE_SMOKE,
                    pos.x, pos.y + 0.2, pos.z,
                    8,
                    0.2, 0.1, 0.2,
                    0.01
            );
            serverLevel.sendParticles(
                    ParticleTypes.SQUID_INK,
                    pos.x, pos.y + 0.4, pos.z,
                    4,
                    0.1, 0.1, 0.1,
                    0.05
            );
            serverLevel.sendParticles(
                    ParticleTypes.ASH,
                    pos.x, pos.y + 0.5, pos.z,
                    12,
                    0.25, 0.2, 0.25,
                    0.03
            );

            float knockbackRadius = 2.5F;
            float knockbackStrength = 0.4F;
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                    this.getBoundingBox().inflate(knockbackRadius))) {
                if (entity == this) continue;
                Vec3 diff = entity.position().subtract(pos);
                double dist = diff.length();
                if (dist < 0.01) continue;
                Vec3 knock = diff.normalize().scale(knockbackStrength * (1.0 - dist / knockbackRadius));
                entity.setDeltaMovement(entity.getDeltaMovement().add(knock.x, 0.15, knock.z));
                entity.hurtMarked = true;
            }
        }

        DepthWormBrutalEntity brutal = new DepthWormBrutalEntity(ModEntities.DEPTH_WORM_BRUTAL.get(), level);
        brutal.copyPosition(this);
        brutal.setYRot(this.getYRot());
        brutal.yHeadRot = this.yHeadRot;
        brutal.yBodyRot = this.yBodyRot;
        brutal.setHealth(this.getHealth());
        brutal.setTarget(this.getTarget());

        BlockPos boundNest = this.getBoundNestPos();
        if (boundNest != null) brutal.bindToNest(boundNest);
        if (this.homePos != null) brutal.setHomePos(this.homePos);
        BlockPos lastExit = this.getLastExitPos();
        if (lastExit != null) brutal.setLastExitPos(lastExit);
        brutal.setFormerHivePos(this.getFormerHivePos());
        brutal.setDigCooldown(this.getDigCooldown());

        brutal.ignoreFallDamageTicks = this.ignoreFallDamageTicks;

        if (this.onDeathCallback != null) {
            brutal.setOnDeathCallback(this.onDeathCallback);
        }

        level.addFreshEntity(brutal);
        this.discard();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (this.meleeCooldown > 0) return false;
        this.meleeCooldown = 20;
        return super.doHurtTarget(target);
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide) {
            if (this.isInWater()) {
                if (!this.isColonist()) this.setTarget(null);
                double fluidHeight = this.getFluidHeight(net.minecraft.tags.FluidTags.WATER);
                if (fluidHeight > 0.4D) {
                    this.setDeltaMovement(this.getDeltaMovement().x, Math.min(0.2D, this.getDeltaMovement().y + 0.06D), this.getDeltaMovement().z);
                }

                if (this.horizontalCollision) {
                    BlockPos land = findNearestLand(8);
                    Vec3 toLand = (land != null)
                            ? new Vec3(land.getX() + 0.5 - this.getX(), 0, land.getZ() + 0.5 - this.getZ())
                            : this.getLookAngle();
                    if (toLand.horizontalDistanceSqr() > 0.001) {
                        Vec3 horiz = toLand.normalize().scale(0.45D);
                        this.setDeltaMovement(horiz.x, 0.65D, horiz.z);
                        this.hasImpulse = true;
                    }
                } else if (this.tickCount % 10 == 0) {
                    BlockPos land = findNearestLand(16);
                    if (land != null) {
                        this.getNavigation().moveTo(land.getX() + 0.5, land.getY() + 0.5, land.getZ() + 0.5, 1.2D);
                        double distToLandSq = this.distanceToSqr(land.getX() + 0.5, land.getY() + 0.5, land.getZ() + 0.5);
                        if (distToLandSq < 4.0) {
                            Vec3 toLand = new Vec3(land.getX() + 0.5 - this.getX(), 0, land.getZ() + 0.5 - this.getZ());
                            if (toLand.lengthSqr() > 0.001) {
                                Vec3 horiz = toLand.normalize().scale(0.45D);
                                this.setDeltaMovement(horiz.x, 0.65D, horiz.z);
                                this.hasImpulse = true;
                            }
                        }
                    }
                }

                if (this.isColonist()) {
                    super.aiStep();
                    if (this.meleeCooldown > 0) this.meleeCooldown--;
                    if (this.ignoreFallDamageTicks > 0) this.ignoreFallDamageTicks--;
                    return;
                }
            } else if (this.isColonist()) {
                super.aiStep();
                if (this.meleeCooldown > 0) this.meleeCooldown--;
                if (this.ignoreFallDamageTicks > 0) this.ignoreFallDamageTicks--;
                return;
            } else if (!this.isRetreating()) {
                LivingEntity currentTarget = this.getTarget();
                if (currentTarget != null && currentTarget.isAlive()) {
                    float maxHealth = this.getMaxHealth();
                    boolean lowHp = this.getHealth() < maxHealth / 3.0f;
                    boolean tooManyKills = this.getKills() >= this.getRetreatKillThreshold();

                    if (lowHp || tooManyKills) {
                        this.setRetreating(true);
                        this.setTarget(null);
                    }
                }

                if (!this.isRetreating() && !this.isColonist()) {
                    BlockPos bound = this.getBoundNestPos();
                    if (bound != null) {
                        double distSq = this.distanceToSqr(
                                bound.getX() + 0.5, bound.getY() + 0.5, bound.getZ() + 0.5
                        );
                        if (distSq > 5000.0) {
                            this.setRetreating(true);
                            this.setTarget(null);
                        }
                    }
                }
            }

            LivingEntity target = this.getTarget();
            if (target != null) {
                if (target.isInWater() || target.isInLava() || target.getType() == EntityType.BAT ||
                    target.level().getBlockState(target.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.LAVA)) {
                    this.setTarget(null);
                }
            }

            if (!this.isRetreating() && !this.isColonist()) {
                for (MobEffectInstance effect : this.getActiveEffects()) {
                    if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                        this.setRetreating(true);
                        this.setTarget(null);
                        break;
                    }
                }
            }
        }

        super.aiStep();

        if (this.onGround()) {
            this.fallDistance = 0.0F;
            boolean canEndFlight = (this instanceof DepthWormBrutalEntity) ? (this.ignoreFallDamageTicks <= 35) : (this.ignoreFallDamageTicks <= 28);
            if (this.isFlying() && canEndFlight) {
                this.setFlying(false);
                this.setAttacking(false);
            }
            List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class,
                    this.getBoundingBox().inflate(0.5D),
                    e -> e == this.getTarget() && e.isAlive());

            for (LivingEntity target : targets) {
                if (target.hurt(this.damageSources().mobAttack(this), 10.0F)) {
                    this.setFlying(false);
                    this.setAttacking(false);
                    this.setDeltaMovement(this.getDeltaMovement().multiply(-0.3, 0.2, -0.3));
                    break;
                }
            }
        }

        if (this.ignoreFallDamageTicks > 0) this.ignoreFallDamageTicks--;
        if (this.digCooldown > 0) this.digCooldown--;
        if (!level().isClientSide) {
            this.entityData.set(IS_ANGRY, this.hurtTime > 0);
            tickMovementJump();
            tickSwarmDigging();
            tickHiveReturnDigging();
        }

        if (!level().isClientSide && nestPos == null) {
            nestPos = getBoundNestPos();
        }
        if (this.meleeCooldown > 0) this.meleeCooldown--;

        if (!level().isClientSide) {
            checkBrutalTransformation();
            checkHiveReconstruction();
        }
    }

    protected int movementJumpCooldown = 0;

    protected void tickMovementJump() {
        if (this.movementJumpCooldown > 0) {
            this.movementJumpCooldown--;
        }

        if (this.level().isClientSide || !this.onGround() || this.movementJumpCooldown > 0) {
            return;
        }

        if (this.isFlying()) {
            return;
        }

        boolean hasPath = this.getNavigation().getPath() != null && !this.getNavigation().getPath().isDone();
        LivingEntity target = this.getTarget();
        boolean hasTarget = target != null && target.isAlive();
        boolean hasWanted = this.getMoveControl().hasWanted();

        if (!hasPath && !hasTarget && !hasWanted) {
            return;
        }

        Vec3 moveDir = null;
        double targetHeightDiff = 0.0;

        // 1. Pathfinding node with vertical climb
        if (hasPath) {
            net.minecraft.world.level.pathfinder.Node nextNode = this.getNavigation().getPath().getNextNode();
            double dy = nextNode.y - this.getY();
            Vec3 nodePos = new Vec3(nextNode.x + 0.5, nextNode.y, nextNode.z + 0.5);
            Vec3 toNode = nodePos.subtract(this.position());
            double hDistSq = toNode.x * toNode.x + toNode.z * toNode.z;

            if (dy >= 0.7 && dy <= getMaxJumpHeight() + 0.5 && hDistSq < 16.0) {
                targetHeightDiff = dy;
                moveDir = new Vec3(toNode.x, 0, toNode.z);
            }
        }

        // 2. Direct target elevation check (e.g. target on a 3-5 block platform without path)
        if (moveDir == null && hasTarget) {
            double dy = target.getY() - this.getY();
            Vec3 toTarget = target.position().subtract(this.position());
            double hDistSq = toTarget.x * toTarget.x + toTarget.z * toTarget.z;
            double maxRange = getMaxJumpHorizontalRange();

            if (dy >= 0.8 && dy <= getMaxJumpHeight() + 0.5 && hDistSq <= (maxRange * maxRange + 4.0)) {
                // Check direct line of sight so worm never jumps at a target through walls/ceilings
                Vec3 wormEye = this.getEyePosition();
                Vec3 targetEye = target.getEyePosition();
                ClipContext clip = new ClipContext(wormEye, targetEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this);
                if (this.level().clip(clip).getType() == HitResult.Type.MISS) {
                    targetHeightDiff = dy;
                    moveDir = new Vec3(toTarget.x, 0, toTarget.z);
                }
            }
        }

        // 3. Move control wanted position check (disabled when retreating so worm doesn't jump at hive on ceiling)
        if (moveDir == null && hasWanted && !this.isRetreating()) {
            double wantedY = this.getMoveControl().getWantedY();
            double dy = wantedY - this.getY();
            Vec3 toWanted = new Vec3(this.getMoveControl().getWantedX() - this.getX(), 0, this.getMoveControl().getWantedZ() - this.getZ());
            double hDistSq = toWanted.lengthSqr();
            double maxRange = getMaxJumpHorizontalRange();

            if (dy >= 0.8 && dy <= getMaxJumpHeight() + 0.5 && hDistSq <= (maxRange * maxRange + 4.0)) {
                targetHeightDiff = dy;
                moveDir = toWanted;
            }
        }

        // 4. Obstacle scan ahead
        if (moveDir == null) {
            Vec3 look = this.getLookAngle();
            Vec3 vel = this.getDeltaMovement();
            Vec3 forward = (vel.horizontalDistanceSqr() > 0.001) ? vel : look;
            forward = new Vec3(forward.x, 0, forward.z);
            if (forward.lengthSqr() < 0.001) {
                forward = new Vec3(look.x, 0, look.z);
            }

            if (this.horizontalCollision) {
                double maxH = scanObstacleHeightAhead(forward.normalize());
                if (maxH >= 0.8 && maxH <= getMaxJumpHeight() + 0.5) {
                    targetHeightDiff = maxH;
                    moveDir = forward;
                }
            } else if (hasPath || hasTarget || hasWanted) {
                double maxH = scanObstacleHeightAhead(forward.normalize());
                if (maxH >= 1.2 && maxH <= getMaxJumpHeight() + 0.5) {
                    targetHeightDiff = maxH;
                    moveDir = forward;
                }
            }
        }

        if (moveDir != null && targetHeightDiff > 0.0) {
            // Check ceiling clearance for the entire jump height!
            int checkLimit = (int) Math.ceil(targetHeightDiff) + 1;
            for (int ch = 1; ch <= checkLimit; ch++) {
                if (isSolidBlock(this.blockPosition().above(ch))) {
                    return; // Ceiling directly above! Never jump!
                }
            }

            performMovementJump(targetHeightDiff, moveDir);
        }
    }

    private boolean isSolidBlock(BlockPos pos) {
        BlockState state = this.level().getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(this.level(), pos).isEmpty();
    }

    private double scanObstacleHeightAhead(Vec3 dir) {
        BlockPos currentPos = this.blockPosition();
        int maxCheck = (int) Math.ceil(getMaxJumpHeight());
        Vec3 normDir = dir.normalize();
        int maxRange = getMaxJumpHorizontalRange();

        for (int step = 1; step <= Math.min(maxRange, 3); step++) {
            BlockPos checkBase = currentPos.offset(
                    (int) Math.round(normDir.x * step),
                    0,
                    (int) Math.round(normDir.z * step)
            );
            for (int h = maxCheck; h >= 1; h--) {
                BlockPos ledgeBlock = checkBase.above(h - 1);
                BlockPos air1 = checkBase.above(h);
                BlockPos air2 = checkBase.above(h + 1);

                if (isSolidBlock(ledgeBlock) && !isSolidBlock(air1) && !isSolidBlock(air2)) {
                    return (double) h;
                }
            }
        }
        return 0.0;
    }

    protected void performMovementJump(double heightDiff, Vec3 dir) {
        double vy;
        double forwardSpeed;

        if (heightDiff <= 1.2) {
            vy = 0.50;
            forwardSpeed = 0.38;
        } else if (heightDiff <= 2.2) {
            vy = 0.72;
            forwardSpeed = 0.42;
        } else if (heightDiff <= 3.2) {
            vy = 0.92;
            forwardSpeed = 0.46;
        } else if (heightDiff <= 4.2) {
            vy = 1.10;
            forwardSpeed = 0.52;
        } else {
            vy = 1.28;
            forwardSpeed = 0.58;
        }

        Vec3 horiz = dir.normalize().scale(forwardSpeed);
        this.setDeltaMovement(horiz.x, vy, horiz.z);
        this.hasImpulse = true;
        this.setFlying(true);
        this.movementJumpCooldown = 20;
    }

    @Nullable
    private BlockPos findNearestLand(int radius) {
        BlockPos pos = this.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int y = -2; y <= 3; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos check = pos.offset(x, y, z);
                    BlockState state = this.level().getBlockState(check);
                    if (!state.getFluidState().isEmpty()) continue;
                    if (state.isAir()) continue;

                    BlockPos above = check.above();
                    if (this.level().getBlockState(above).isAir()) {
                        double d = pos.distSqr(above);
                        if (d < bestDist) {
                            bestDist = d;
                            best = above;
                        }
                    }
                }
            }
        }
        return best;
    }

    @Override
    public void push(Entity entity) {
        super.push(entity);
        if (this.isFlying() && entity instanceof LivingEntity target && target == this.getTarget()) {
            target.hurt(this.damageSources().mobAttack(this), 8.0F);
            this.setFlying(false);
            this.setAttacking(false);
        }
    }

    @Override
    public boolean isPushable() {
        boolean isReturning = this.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof ReturnToHiveGoal && wrappedGoal.isRunning());
        return super.isPushable() && !isReturning;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(-1, new ColonistReturnGoal(this));
        this.goalSelector.addGoal(0, new ColonistMoveGoal(this));
        this.goalSelector.addGoal(0, new DepthWormJumpGoal(this, 1.5D, 5.0F, 10.0F));
        this.goalSelector.addGoal(1, new ReturnToHiveGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, false,
                (target) -> !target.isInLava() && !target.isInWater() && !target.level().getBlockState(target.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.LAVA)));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, false,
                (target) -> target.isAlive() && target.deathTime <= 0
                        && !(target instanceof DepthWormEntity)
                        && target.getType() != EntityType.BAT
                        && !target.isInLava()
                        && !target.isInWater()
                        && !target.level().getBlockState(target.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.LAVA)));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this).setAlertOthers());
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public int calculateFallDamage(float distance, float damageMultiplier) {
        return 0;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FALL)) return false;
        return super.hurt(source, amount);
    }

    public void addKillPoints(Entity victim) {
        int points = 1;
        if (victim instanceof LivingEntity le) {
            points = Math.max(1, (int) (le.getMaxHealth()));
        }
        this.entityData.set(KILLS, this.getKills() + points);
    }

    public int getKills() {
        return this.entityData.get(KILLS);
    }

    @Override
    public void awardKillScore(Entity killed, int score, DamageSource damageSource) {
        super.awardKillScore(killed, score, damageSource);
        this.addKillPoints(killed);
        this.addRawKill();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(AnimationState<DepthWormEntity> state) {
        if (this.isDeadOrDying()) {
            return state.setAndContinue(RawAnimation.begin().thenPlayAndHold("death"));
        }
        if (this.isAttacking()) {
            return state.setAndContinue(RawAnimation.begin().thenPlayAndHold("prepare"));
        }
        if (state.isMoving()) {
            return state.setAndContinue(RawAnimation.begin().thenLoop("slide"));
        }
        return state.setAndContinue(RawAnimation.begin().thenLoop("slide"));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
