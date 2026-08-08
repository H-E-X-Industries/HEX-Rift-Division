package com.trd.client.render.flywheel;

import com.trd.api.rotation.ShaftMaterial;
import com.trd.api.rotation.Rotational;
import com.trd.api.rotation.ShaftDiameter;
import com.trd.block.basic.industrial.rotation.ClutchBlock;
import com.trd.block.entity.industrial.rotation.ClutchBlockEntity;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class ClutchVisual extends AbstractBlockEntityVisual<ClutchBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance body;
    private TransformedInstance shaftFront;
    private TransformedInstance shaftBack;

    private final Direction facing;
    private ShaftMaterial currentMaterial = null;
    private ShaftDiameter currentDiameter = null;

    private float smoothedSpeedFront = 0f;
    private float currentAngleFront = 0f;
    
    private float smoothedSpeedBack = 0f;
    private float currentAngleBack = 0f;
    
    private float lastFrameTime = -1.0f;
    
    private final float localX;
    private final float localY;
    private final float localZ;

    public ClutchVisual(VisualizationContext ctx, ClutchBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        this.facing = blockState.getValue(ClutchBlock.FACING);
        
        Vec3i origin = ctx.renderOrigin();
        this.localX = pos.getX() - origin.getX();
        this.localY = pos.getY() - origin.getY();
        this.localZ = pos.getZ() - origin.getZ();

        this.body = instancerProvider().instancer(
                InstanceTypes.TRANSFORMED,
                Models.partial(ModModels.CLUTCH_BLOCK)
        ).createInstance();

        setupStaticBody();
        createShaftInstances();

        updateLight(partialTick);
    }

    private void createShaftInstances() {
        if (blockEntity.hasShaft()) {
            this.currentMaterial = blockEntity.getShaftMaterial();
            this.currentDiameter = blockEntity.getShaftDiameter();
            String matName = currentMaterial.name().toLowerCase();
            String diaName = currentDiameter.name().toLowerCase();
            String shaftName = "shaft_" + diaName + "_" + matName;

            PartialModel shaftModel = ModModels.SHAFT_MODELS.get(shaftName);
            if (shaftModel == null) {
                shaftModel = ModModels.HALF_SHAFT;
            }

            this.shaftFront = instancerProvider().instancer(
                    InstanceTypes.TRANSFORMED,
                    Models.partial(shaftModel)
            ).createInstance();

            this.shaftBack = instancerProvider().instancer(
                    InstanceTypes.TRANSFORMED,
                    Models.partial(shaftModel)
            ).createInstance();
        }
    }

    private void setupStaticBody() {
        applyStaticTransform(this.body);
    }

    private void applyStaticTransform(TransformedInstance instance) {
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

        instance.translate(-0.5f, -0.5f, -0.5f);
        instance.setChanged();
    }

    @Override
    public void beginFrame(Context ctx) {
        boolean shaftStateChanged = blockEntity.hasShaft() != (this.shaftFront != null);
        boolean materialChanged = blockEntity.getShaftMaterial() != currentMaterial;
        boolean diameterChanged = blockEntity.getShaftDiameter() != currentDiameter;

        if (shaftStateChanged || materialChanged || diameterChanged) {
            if (this.shaftFront != null) this.shaftFront.delete();
            if (this.shaftBack != null) this.shaftBack.delete();
            this.shaftFront = null;
            this.shaftBack = null;

            createShaftInstances();
            if (this.shaftFront != null) relight(pos, this.shaftFront, this.shaftBack);
        }

        if (this.shaftFront == null || this.shaftBack == null) return;

        float partialTick = net.minecraft.client.Minecraft.getInstance().getFrameTime();
        float timeInSeconds = (level.getGameTime() + partialTick) / 20.0f;

        if (this.lastFrameTime < 0) this.lastFrameTime = timeInSeconds;
        float deltaSeconds = timeInSeconds - this.lastFrameTime;
        this.lastFrameTime = timeInSeconds;

        boolean powered = blockEntity.getBlockState().getValue(ClutchBlock.POWERED);
        
        float targetSpeedFront = 0;
        float targetSpeedBack = 0;

        if (powered) {
            targetSpeedFront = blockEntity.getVisualSpeed();
            targetSpeedBack = targetSpeedFront;
        } else {
            BlockEntity beFront = level.getBlockEntity(pos.relative(facing.getOpposite()));
            BlockEntity beBack = level.getBlockEntity(pos.relative(facing));
            
            if (beFront instanceof Rotational rotFront) {
                targetSpeedFront = rotFront.getVisualSpeed();
            }
            if (beBack instanceof Rotational rotBack) {
                targetSpeedBack = rotBack.getVisualSpeed();
            }
        }

        float maxRenderSpeed = 300f; 
        if (Math.abs(targetSpeedFront) > maxRenderSpeed) targetSpeedFront = Math.signum(targetSpeedFront) * maxRenderSpeed;
        if (Math.abs(targetSpeedBack) > maxRenderSpeed) targetSpeedBack = Math.signum(targetSpeedBack) * maxRenderSpeed;

        currentAngleFront = updateAngle(targetSpeedFront, deltaSeconds, timeInSeconds, currentAngleFront, true);
        currentAngleBack = updateAngle(targetSpeedBack, deltaSeconds, timeInSeconds, currentAngleBack, false);

        applyShaftTransform(this.shaftFront, currentAngleFront, true);
        applyShaftTransform(this.shaftBack, currentAngleBack, false);
    }
    
    private float updateAngle(float targetSpeed, float deltaSeconds, float timeInSeconds, float currentAngle, boolean isFront) {
        float smoothedSpeed = isFront ? this.smoothedSpeedFront : this.smoothedSpeedBack;
        
        if (smoothedSpeed == 0 && targetSpeed != 0) {
            smoothedSpeed = targetSpeed;
            currentAngle = (timeInSeconds * targetSpeed * ((float) Math.PI / 30.0f)) % ((float) Math.PI * 2);
            if (currentAngle < 0) currentAngle += (float) Math.PI * 2;
        }

        float speedDiff = targetSpeed - smoothedSpeed;
        if (Math.abs(speedDiff) > 0.1f) {
            smoothedSpeed += speedDiff * 4.0f * deltaSeconds;
        } else {
            smoothedSpeed = targetSpeed;
        }

        if (isFront) this.smoothedSpeedFront = smoothedSpeed;
        else this.smoothedSpeedBack = smoothedSpeed;

        currentAngle += smoothedSpeed * ((float) Math.PI / 30.0f) * deltaSeconds;
        float twoPi = (float) (2 * Math.PI);
        currentAngle = currentAngle % twoPi;
        if (currentAngle < 0) currentAngle += twoPi;
        
        if (smoothedSpeed == targetSpeed && targetSpeed != 0) {
            float globalAngle = (timeInSeconds * targetSpeed * ((float) Math.PI / 30.0f)) % twoPi;
            if (globalAngle < 0) globalAngle += twoPi;

            float diff = (globalAngle - currentAngle) % twoPi;
            if (diff > Math.PI) diff -= twoPi;
            if (diff < -Math.PI) diff += twoPi;

            currentAngle += diff * 10.0f * deltaSeconds;
        }

        if (targetSpeed == 0 && Math.abs(smoothedSpeed) < 5.0f) {
            float PI_OVER_4 = (float) (Math.PI / 4.0);
            float targetSnap = Math.round(currentAngle / PI_OVER_4) * PI_OVER_4;
            float snapDiff = targetSnap - currentAngle;
            
            if (Math.abs(snapDiff) > 0.001f) {
                float pull = 8.0f * (1.0f - (Math.abs(smoothedSpeed) / 5.0f));
                currentAngle += snapDiff * pull * deltaSeconds;
            } else {
                currentAngle = targetSnap;
            }
        }
        
        return currentAngle;
    }

    private void applyShaftTransform(TransformedInstance instance, float angle, boolean isFront) {
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

        instance.rotateZ(angle);
        
        instance.scale(1f, 1f, 0.5f);
        
        if (isFront) {
            instance.translate(0f, 0f, 0.5f);
        } else {
            instance.translate(0f, 0f, -0.5f);
        }

        instance.translate(-0.5f, -0.5f, -0.5f);
        instance.setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        if (this.shaftFront != null) {
            relight(pos, this.body, this.shaftFront, this.shaftBack);
        } else {
            relight(pos, this.body);
        }
    }

    @Override
    protected void _delete() {
        this.body.delete();
        if (this.shaftFront != null) this.shaftFront.delete();
        if (this.shaftBack != null) this.shaftBack.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(this.body);
        if (this.shaftFront != null) consumer.accept(this.shaftFront);
        if (this.shaftBack != null) consumer.accept(this.shaftBack);
    }
}
