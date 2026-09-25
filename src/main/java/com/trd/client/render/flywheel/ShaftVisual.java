package com.trd.client.render.flywheel;

import com.trd.block.basic.industrial.rotation.ShaftBlock;
import com.trd.block.entity.industrial.rotation.ShaftBlockEntity;
import com.trd.main.MainRegistry;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class ShaftVisual extends AbstractBlockEntityVisual<ShaftBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance shaftInstance;
    private final Direction facing;

    private final float localX;
    private final float localY;
    private final float localZ;

    private float smoothedSpeed = 0f;
    private float currentAngle = 0f;
    private float lastFrameTime = -1.0f;

    /*
    // =========================================================
    // НА РЕФАКТОРИНГЕ: Рендер насадок на вал временно отключен
    // =========================================================
    @Nullable private TransformedInstance gearInstance;
    @Nullable private TransformedInstance pulleyInstance;
    @Nullable private TransformedInstance bevelStartInstance;
    @Nullable private TransformedInstance bevelEndInstance;
    @Nullable private TransformedInstance rotorInstance;
    @Nullable private TransformedInstance flywheelInstance;
    private final java.util.List<TransformedInstance> beltTracks = new java.util.ArrayList<>();
    private net.minecraft.core.BlockPos lastConnectedPos = null;
    private float phaseOffset = 0f;
    private net.minecraft.world.item.Item currentGearItem;
    private net.minecraft.world.item.Item currentPulleyItem;
    private net.minecraft.world.item.Item currentBevelStartItem;
    private net.minecraft.world.item.Item currentBevelEndItem;
    private net.minecraft.world.item.Item currentRotorItem;
    private net.minecraft.world.item.Item currentFlywheelItem;
    */

    public ShaftVisual(VisualizationContext ctx, ShaftBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        this.facing = blockState.getValue(ShaftBlock.FACING);

        Vec3i origin = ctx.renderOrigin();
        this.localX = pos.getX() - origin.getX();
        this.localY = pos.getY() - origin.getY();
        this.localZ = pos.getZ() - origin.getZ();

        ResourceLocation shaftId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
        String shaftName = shaftId != null ? shaftId.getPath() : "";
        PartialModel shaftModel = ModModels.SHAFT_MODELS.getOrDefault(shaftName, ModModels.HALF_SHAFT);
        this.shaftInstance = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(shaftModel)).createInstance();

        /*
        // Rebuild attachments commented out
        rebuildGear();
        rebuildPulley();
        rebuildBevelGears();
        rebuildRotor();
        rebuildFlywheel();
        */

        setupStatic(shaftInstance, 0);
        updateLight(partialTick);

        if (MainRegistry.LOGGER.isInfoEnabled()) {
            MainRegistry.LOGGER.info("[trd-Visual] ShaftVisual CREATED at {} | model={} | origin=({},{},{})",
                    pos, shaftModel != null ? "OK" : "NULL",
                    ctx.renderOrigin().getX(), ctx.renderOrigin().getY(), ctx.renderOrigin().getZ());
        }
    }

    private void setupStatic(TransformedInstance instance, float initialRotationZ) {
        instance.setIdentityTransform()
                .translate(localX, localY, localZ)
                .translate(0.5f, 0.5f, 0.5f);

        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            instance.rotateY((float) Math.toRadians(facing == Direction.EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Y) {
            instance.rotateX((float) Math.toRadians(facing == Direction.UP ? 90 : -90));
        } else if (facing == Direction.SOUTH) {
            instance.rotateY((float) Math.toRadians(180));
        }

        if (initialRotationZ != 0) {
            instance.rotateZ(initialRotationZ);
        }

        instance.translate(-0.5f, -0.5f, -0.5f);
        instance.setChanged();
    }

    @Override
    public void beginFrame(Context ctx) {
        // --- МАТЕМАТИКА ВРАЩЕНИЯ И ФАЗИРОВКИ ВАЛА ---
        float partialTick = ctx.partialTick();
        float timeInSeconds = (level.getGameTime() + partialTick) / 20.0f;

        if (this.lastFrameTime < 0) this.lastFrameTime = timeInSeconds;
        float deltaSeconds = timeInSeconds - this.lastFrameTime;
        if (deltaSeconds > 0.25f || deltaSeconds <= 0f) deltaSeconds = 0.016f;
        this.lastFrameTime = timeInSeconds;

        float physicalTargetSpeed = blockEntity.getVisualSpeed();

        // Ограничитель скорости для рендера (защита от стробоскопического эффекта на сверхвысоких RPM)
        float maxRenderSpeed = 300f; 
        float targetSpeed = physicalTargetSpeed;
        if (Math.abs(targetSpeed) > maxRenderSpeed) {
            targetSpeed = Math.signum(targetSpeed) * maxRenderSpeed;
        }

        float speedDiff = targetSpeed - this.smoothedSpeed;
        if (Math.abs(speedDiff) > 0.01f) {
            this.smoothedSpeed += speedDiff * 5.0f * deltaSeconds;
        } else {
            this.smoothedSpeed = targetSpeed;
        }

        // Непрерывная физическая интеграция угла вращения
        this.currentAngle += this.smoothedSpeed * ((float) Math.PI / 30.0f) * deltaSeconds;
        float twoPi = (float) (2 * Math.PI);
        this.currentAngle = this.currentAngle % twoPi;
        if (this.currentAngle < 0) this.currentAngle += twoPi;

        if (targetSpeed == 0 && Math.abs(this.smoothedSpeed) < 5.0f) {
            float PI_OVER_4 = (float) (Math.PI / 4.0);
            float targetSnap = Math.round(this.currentAngle / PI_OVER_4) * PI_OVER_4;
            float snapDiff = targetSnap - this.currentAngle;
            
            if (Math.abs(snapDiff) > 0.001f) {
                float pull = 6.0f * (1.0f - (Math.abs(this.smoothedSpeed) / 5.0f));
                this.currentAngle += snapDiff * pull * deltaSeconds;
            } else {
                this.currentAngle = targetSnap;
            }
        }
        // --- КОНЕЦ МАТЕМАТИКИ ---

        setupStatic(shaftInstance, currentAngle);

        /*
        // Вращение насадок временно отключено
        if (gearInstance != null) setupStatic(gearInstance, currentAngle + this.phaseOffset);
        if (pulleyInstance != null) setupStatic(pulleyInstance, currentAngle);
        if (rotorInstance != null) setupStatic(rotorInstance, currentAngle);
        if (flywheelInstance != null) setupStatic(flywheelInstance, currentAngle);
        if (bevelStartInstance != null) setupStaticForBevel(bevelStartInstance, currentAngle, true);
        if (bevelEndInstance != null) setupStaticForBevel(bevelEndInstance, currentAngle, false);
        */
    }

    @Override
    public void updateLight(float partialTick) {
        relight(pos, shaftInstance);
    }

    @Override
    protected void _delete() {
        shaftInstance.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(shaftInstance);
    }
}
