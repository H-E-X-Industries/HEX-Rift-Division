package com.trd.client.render.flywheel;

import com.trd.api.rotation.Rotational;
import com.trd.api.rotation.ShaftDiameter;
import com.trd.api.rotation.ShaftMaterial;
import com.trd.block.basic.industrial.rotation.BearingBlock;
import com.trd.block.basic.industrial.rotation.ClutchBlock;
import com.trd.block.basic.industrial.rotation.MotorElectroBlock;
import com.trd.block.basic.industrial.rotation.ShaftBlock;
import com.trd.block.basic.industrial.rotation.TachometerBlock;
import com.trd.block.entity.industrial.rotation.BearingBlockEntity;
import com.trd.block.entity.industrial.rotation.ClutchBlockEntity;
import com.trd.block.entity.industrial.rotation.KineticNodeBlockEntity;
import com.trd.block.entity.industrial.rotation.MotorElectroBlockEntity;
import com.trd.block.entity.industrial.rotation.ShaftBlockEntity;
import com.trd.block.entity.industrial.rotation.TachometerBlockEntity;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
            if (this.currentMaterial == null) this.currentMaterial = ShaftMaterial.IRON;
            if (this.currentDiameter == null) this.currentDiameter = ShaftDiameter.LIGHT;

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

        float partialTick = ctx.partialTick();
        float timeInSeconds = (level.getGameTime() + partialTick) / 20.0f;

        if (this.lastFrameTime < 0) this.lastFrameTime = timeInSeconds;
        float deltaSeconds = timeInSeconds - this.lastFrameTime;
        if (deltaSeconds > 0.25f || deltaSeconds <= 0f) deltaSeconds = 0.016f;
        this.lastFrameTime = timeInSeconds;

        boolean powered = blockEntity.getBlockState().getValue(ClutchBlock.POWERED);

        if (powered) {
            float angle = com.trd.client.rotation.ClientKineticAngleTracker.getAngle(blockEntity, facing, partialTick);
            currentAngleFront = angle;
            currentAngleBack = angle;
            float currentSpeed = blockEntity.getSpeed();
            this.smoothedSpeedFront = currentSpeed;
            this.smoothedSpeedBack = currentSpeed;
        } else {
            BlockPos posFront = pos.relative(facing.getOpposite());
            BlockPos posBack = pos.relative(facing);

            BlockEntity beFront = level.getBlockEntity(posFront);
            BlockEntity beBack = level.getBlockEntity(posBack);

            if (isHalfShaftConnected(posFront, beFront) && beFront instanceof KineticNodeBlockEntity rotFront) {
                currentAngleFront = com.trd.client.rotation.ClientKineticAngleTracker.getAngle(rotFront, facing, partialTick);
                this.smoothedSpeedFront = rotFront.getSpeed();
            } else {
                currentAngleFront = updateAngle(0, deltaSeconds, timeInSeconds, currentAngleFront, true);
            }

            if (isHalfShaftConnected(posBack, beBack) && beBack instanceof KineticNodeBlockEntity rotBack) {
                currentAngleBack = com.trd.client.rotation.ClientKineticAngleTracker.getAngle(rotBack, facing, partialTick);
                this.smoothedSpeedBack = rotBack.getSpeed();
            } else {
                currentAngleBack = updateAngle(0, deltaSeconds, timeInSeconds, currentAngleBack, false);
            }
        }

        applyShaftTransform(this.shaftFront, currentAngleFront, true);
        applyShaftTransform(this.shaftBack, currentAngleBack, false);
    }

    private boolean isHalfShaftConnected(BlockPos neighborPos, BlockEntity be) {
        if (be == null || be.isRemoved()) return false;
        if (!blockEntity.hasShaft()) return false;
        if (level == null || !level.isLoaded(neighborPos)) return false;

        BlockState neighborState = level.getBlockState(neighborPos);
        if (neighborState.isAir()) return false;

        if (!(be instanceof Rotational neighborNode)) return false;

        Direction.Axis myAxis = facing.getAxis();
        ShaftDiameter myDia = blockEntity.getShaftDiameter();

        if (neighborNode instanceof ShaftBlockEntity shaftBE) {
            if (shaftBE.getBlockState().getBlock() instanceof ShaftBlock shaftBlock) {
                return shaftBlock.getDiameter() == myDia &&
                        shaftBE.getBlockState().getValue(ShaftBlock.FACING).getAxis() == myAxis;
            }
        } else if (neighborNode instanceof BearingBlockEntity bearing) {
            return bearing.hasShaft() && bearing.getShaftDiameter() == myDia &&
                    bearing.getBlockState().getValue(BearingBlock.FACING).getAxis() == myAxis;
        } else if (neighborNode instanceof ClutchBlockEntity otherClutch) {
            return otherClutch.hasShaft() && otherClutch.getShaftDiameter() == myDia &&
                    otherClutch.getBlockState().getValue(ClutchBlock.FACING).getAxis() == myAxis;
        } else if (neighborNode instanceof TachometerBlockEntity tach) {
            return tach.hasShaft() && tach.getShaftDiameter() == myDia &&
                    tach.getBlockState().getValue(TachometerBlock.FACING).getAxis() == myAxis;
        } else if (neighborNode instanceof MotorElectroBlockEntity motor) {
            return myDia == ShaftDiameter.LIGHT &&
                    motor.getBlockState().getValue(MotorElectroBlock.FACING).getAxis() == myAxis;
        }
        return false;
    }

    private float updateAngle(float targetSpeed, float deltaSeconds, float timeInSeconds, float currentAngle, boolean isFront) {
        float smoothedSpeed = isFront ? this.smoothedSpeedFront : this.smoothedSpeedBack;

        float speedDiff = targetSpeed - smoothedSpeed;
        if (Math.abs(speedDiff) > 0.01f) {
            smoothedSpeed += speedDiff * 5.0f * deltaSeconds;
        } else {
            smoothedSpeed = targetSpeed;
        }

        if (isFront) this.smoothedSpeedFront = smoothedSpeed;
        else this.smoothedSpeedBack = smoothedSpeed;

        currentAngle += smoothedSpeed * ((float) Math.PI / 30.0f) * deltaSeconds;
        float twoPi = (float) (2 * Math.PI);
        currentAngle = currentAngle % twoPi;
        if (currentAngle < 0) currentAngle += twoPi;

        if (targetSpeed == 0 && Math.abs(smoothedSpeed) < 5.0f) {
            float PI_OVER_4 = (float) (Math.PI / 4.0);
            float targetSnap = Math.round(currentAngle / PI_OVER_4) * PI_OVER_4;
            float snapDiff = targetSnap - currentAngle;

            if (Math.abs(snapDiff) > 0.001f) {
                float pull = 6.0f * (1.0f - (Math.abs(smoothedSpeed) / 5.0f));
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
        if (this.shaftFront != null && this.shaftBack != null) {
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
