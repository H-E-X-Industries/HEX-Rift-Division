package com.trd.client.render.visual;

import com.trd.multiblock.industrial.drobitel.DrobitelBlock;
import com.trd.multiblock.industrial.drobitel.DrobitelBlockEntity;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class DrobitelVisual extends AbstractBlockEntityVisual<DrobitelBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance shaftFront;
    private final TransformedInstance shaftBack;

    @Nullable private TransformedInstance leftBlade;
    @Nullable private TransformedInstance rightBlade;
    @Nullable private TransformedInstance leftShaft1;
    @Nullable private TransformedInstance leftShaft2;
    @Nullable private TransformedInstance rightShaft1;
    @Nullable private TransformedInstance rightShaft2;

    private boolean hasBlade1Prev = false;
    private boolean hasBlade2Prev = false;

    private final Direction facing;
    private final float localX;
    private final float localY;
    private final float localZ;

    private float smoothedSpeed = 0f;
    private float currentAngle = 0f;
    private float lastFrameTime = -1.0f;

    public DrobitelVisual(VisualizationContext ctx, DrobitelBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        if (blockState.hasProperty(DrobitelBlock.FACING)) {
            this.facing = blockState.getValue(DrobitelBlock.FACING);
        } else {
            this.facing = Direction.NORTH;
        }

        Vec3i origin = ctx.renderOrigin();
        this.localX = pos.getX() - origin.getX();
        this.localY = pos.getY() - origin.getY();
        this.localZ = pos.getZ() - origin.getZ();

        var shaftModel = Models.partial(com.trd.client.render.flywheel.ModModels.SHAFT_MODELS.get("shaft_light_iron"));
        this.shaftFront = instancerProvider().instancer(InstanceTypes.TRANSFORMED, shaftModel).createInstance();
        this.shaftBack = instancerProvider().instancer(InstanceTypes.TRANSFORMED, shaftModel).createInstance();

        updateBlades();
        updateLight(partialTick);
    }

    private void updateBlades() {
        boolean hasBlade1 = blockEntity.getHasBlade1() == 1; // Left blade (slot 1)
        boolean hasBlade2 = blockEntity.getHasBlade2() == 1; // Right blade (slot 2)

        var shaftModel = Models.partial(com.trd.client.render.flywheel.ModModels.SHAFT_MODELS.get("shaft_light_iron"));
        var bladeModel = Models.partial(com.trd.client.render.flywheel.ModModels.CRUSHER_BLADES);

        if (hasBlade1 != hasBlade1Prev) {
            if (hasBlade1) {
                this.leftBlade = instancerProvider().instancer(InstanceTypes.TRANSFORMED, bladeModel).createInstance();
                this.leftShaft1 = instancerProvider().instancer(InstanceTypes.TRANSFORMED, shaftModel).createInstance();
                this.leftShaft2 = instancerProvider().instancer(InstanceTypes.TRANSFORMED, shaftModel).createInstance();
                if (this.leftBlade != null) relight(pos, this.leftBlade);
                if (this.leftShaft1 != null) relight(pos, this.leftShaft1);
                if (this.leftShaft2 != null) relight(pos, this.leftShaft2);
            } else {
                if (this.leftBlade != null) this.leftBlade.delete();
                if (this.leftShaft1 != null) this.leftShaft1.delete();
                if (this.leftShaft2 != null) this.leftShaft2.delete();
                this.leftBlade = null;
                this.leftShaft1 = null;
                this.leftShaft2 = null;
            }
            hasBlade1Prev = hasBlade1;
        }

        if (hasBlade2 != hasBlade2Prev) {
            if (hasBlade2) {
                this.rightBlade = instancerProvider().instancer(InstanceTypes.TRANSFORMED, bladeModel).createInstance();
                this.rightShaft1 = instancerProvider().instancer(InstanceTypes.TRANSFORMED, shaftModel).createInstance();
                this.rightShaft2 = instancerProvider().instancer(InstanceTypes.TRANSFORMED, shaftModel).createInstance();
                if (this.rightBlade != null) relight(pos, this.rightBlade);
                if (this.rightShaft1 != null) relight(pos, this.rightShaft1);
                if (this.rightShaft2 != null) relight(pos, this.rightShaft2);
            } else {
                if (this.rightBlade != null) this.rightBlade.delete();
                if (this.rightShaft1 != null) this.rightShaft1.delete();
                if (this.rightShaft2 != null) this.rightShaft2.delete();
                this.rightBlade = null;
                this.rightShaft1 = null;
                this.rightShaft2 = null;
            }
            hasBlade2Prev = hasBlade2;
        }
    }

    @Override
    public void beginFrame(Context context) {
        updateBlades();

        float physicalTargetSpeed = blockEntity.getVisualSpeed();
        float partialTick = net.minecraft.client.Minecraft.getInstance().getFrameTime();
        float timeInSeconds = (level.getGameTime() + partialTick) / 20.0f;

        if (this.lastFrameTime < 0) this.lastFrameTime = timeInSeconds;
        float deltaSeconds = timeInSeconds - this.lastFrameTime;
        this.lastFrameTime = timeInSeconds;

        float maxRenderSpeed = 300f;
        float targetSpeed = physicalTargetSpeed;
        if (Math.abs(targetSpeed) > maxRenderSpeed) {
            targetSpeed = Math.signum(targetSpeed) * maxRenderSpeed;
        }

        if (this.smoothedSpeed == 0 && targetSpeed != 0) {
            this.smoothedSpeed = targetSpeed;
            this.currentAngle = (timeInSeconds * targetSpeed * ((float) Math.PI / 30.0f)) % ((float) Math.PI * 2);
            if (this.currentAngle < 0) this.currentAngle += (float) Math.PI * 2;
        }

        float speedDiff = targetSpeed - this.smoothedSpeed;
        if (Math.abs(speedDiff) > 0.1f) {
            this.smoothedSpeed += speedDiff * 4.0f * deltaSeconds;
        } else {
            this.smoothedSpeed = targetSpeed;
        }

        this.currentAngle += this.smoothedSpeed * ((float) Math.PI / 30.0f) * deltaSeconds;
        float twoPi = (float) (2 * Math.PI);
        this.currentAngle = this.currentAngle % twoPi;
        if (this.currentAngle < 0) this.currentAngle += twoPi;

        if (this.smoothedSpeed == targetSpeed && targetSpeed != 0) {
            float globalAngle = (timeInSeconds * targetSpeed * ((float) Math.PI / 30.0f)) % twoPi;
            if (globalAngle < 0) globalAngle += twoPi;

            float diff = (globalAngle - this.currentAngle) % twoPi;
            if (diff > Math.PI) diff -= twoPi;
            if (diff < -Math.PI) diff += twoPi;

            this.currentAngle += diff * 10.0f * deltaSeconds;
        }

        if (targetSpeed == 0 && Math.abs(this.smoothedSpeed) < 5.0f) {
            float PI_OVER_4 = (float) (Math.PI / 4.0);
            float targetSnap = Math.round(this.currentAngle / PI_OVER_4) * PI_OVER_4;
            float snapDiff = targetSnap - this.currentAngle;

            if (Math.abs(snapDiff) > 0.001f) {
                float pull = 8.0f * (1.0f - (Math.abs(this.smoothedSpeed) / 5.0f));
                this.currentAngle += snapDiff * pull * deltaSeconds;
            } else {
                this.currentAngle = targetSnap;
            }
        }

        // --- Outer network shafts ---
        updateShaft(shaftFront, facing.getStepX(), facing.getStepZ(), 0.0f, 0.0f, 0.0f, currentAngle);
        updateShaft(shaftBack, -facing.getStepX(), -facing.getStepZ(), 0.0f, 0.0f, 0.0f, currentAngle);

        // --- Blades and Inner Shafts ---
        // Right blade (rotates counter-clockwise: currentAngle)
        if (rightBlade != null) {
            updatePart(rightBlade, -0.1f, 1.3225f, 0.525f, currentAngle, false, 180f);
        }
        if (rightShaft1 != null) {
            // Front shaft (near North)
            updatePart(rightShaft1, -0.1f, 1.3225f, -0.275f, currentAngle, true, 0f);
        }
        if (rightShaft2 != null) {
            // Back shaft (near South)
            updatePart(rightShaft2, -0.1f, 1.3225f, 1.325f, currentAngle, true, 0f);
        }

        // Left blade (rotates clockwise: -currentAngle)
        if (leftBlade != null) {
            updatePart(leftBlade, 1.1f, 1.3225f, 0.525f, -currentAngle, false, 0f);
        }
        if (leftShaft1 != null) {
            updatePart(leftShaft1, 1.1f, 1.3225f, -0.275f, -currentAngle, true, 0f);
        }
        if (leftShaft2 != null) {
            updatePart(leftShaft2, 1.1f, 1.3225f, 1.325f, -currentAngle, true, 0f);
        }
    }

    private void updatePart(TransformedInstance part, float x, float y, float z, float angle, boolean isBlockModel, float extraRotY) {
        part.setIdentityTransform();

        // 1. Move to block position
        part.translate(localX, localY, localZ);

        // 2. Move to block center to apply facing rotation
        part.translate(0.5f, 0.5f, 0.5f);
        
        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            part.rotateY((float) Math.toRadians(facing == Direction.EAST ? 270 : 90));
        } else if (facing == Direction.SOUTH) {
            part.rotateY((float) Math.toRadians(180));
        }
        
        // 3. Move back from center
        part.translate(-0.5f, -0.5f, -0.5f);
        
        // 4. Translate to the specified center coordinates
        part.translate(x, y, z);
        
        // 5. Rotate the part itself along Z axis
        part.rotateZ(angle);
        
        // 5.5 Extra Y rotation (for mirroring)
        if (extraRotY != 0) {
            part.rotateY((float) Math.toRadians(extraRotY));
        }
        
        // 6. If it's a JSON block model, its geometric center is at 0.5, 0.5, 0.5.
        // We shift it by -0.5 so its center aligns with 0,0,0 before rotation.
        if (isBlockModel) {
            part.translate(-0.5f, -0.5f, -0.5f);
        }
        
        part.setChanged();
    }

    private void updateShaft(TransformedInstance shaft, int offsetX, int offsetZ, float dx, float dy, float dz, float angle) {
        shaft.setIdentityTransform()
                .translate(localX + offsetX, localY, localZ + offsetZ)
                .translate(0.5f, 0.5f, 0.5f);

        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            shaft.rotateY((float) Math.toRadians(facing == Direction.EAST ? 270 : 90));
        } else if (facing == Direction.SOUTH) {
            shaft.rotateY((float) Math.toRadians(180));
        }

        shaft.rotateZ(angle);
        shaft.translate(-0.5f, -0.5f, -0.5f);
        shaft.setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(pos.relative(facing), shaftFront);
        relight(pos.relative(facing.getOpposite()), shaftBack);
        if (leftBlade != null) relight(pos, leftBlade);
        if (rightBlade != null) relight(pos, rightBlade);
        if (leftShaft1 != null) relight(pos, leftShaft1);
        if (leftShaft2 != null) relight(pos, leftShaft2);
        if (rightShaft1 != null) relight(pos, rightShaft1);
        if (rightShaft2 != null) relight(pos, rightShaft2);
    }

    @Override
    protected void _delete() {
        shaftFront.delete();
        shaftBack.delete();
        if (leftBlade != null) leftBlade.delete();
        if (rightBlade != null) rightBlade.delete();
        if (leftShaft1 != null) leftShaft1.delete();
        if (leftShaft2 != null) leftShaft2.delete();
        if (rightShaft1 != null) rightShaft1.delete();
        if (rightShaft2 != null) rightShaft2.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(shaftFront);
        consumer.accept(shaftBack);
        if (leftBlade != null) consumer.accept(leftBlade);
        if (rightBlade != null) consumer.accept(rightBlade);
        if (leftShaft1 != null) consumer.accept(leftShaft1);
        if (leftShaft2 != null) consumer.accept(leftShaft2);
        if (rightShaft1 != null) consumer.accept(rightShaft1);
        if (rightShaft2 != null) consumer.accept(rightShaft2);
    }
}
