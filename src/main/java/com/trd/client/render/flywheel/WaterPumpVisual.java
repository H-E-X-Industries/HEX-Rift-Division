package com.trd.client.render.flywheel;

import com.trd.block.basic.industrial.fluids.WaterPumpBlock;
import com.trd.block.entity.industrial.fluids.WaterPumpBlockEntity;
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

public class WaterPumpVisual extends AbstractBlockEntityVisual<WaterPumpBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance base;
    private final TransformedInstance shaft;

    private final Direction facing;
    private final float localX;
    private final float localY;
    private final float localZ;

    private float smoothedSpeed = 0f;
    private float currentAngle = 0f;
    private float lastFrameTime = -1.0f;

    public WaterPumpVisual(VisualizationContext ctx, WaterPumpBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        if (blockState.hasProperty(WaterPumpBlock.FACING)) {
            this.facing = blockState.getValue(WaterPumpBlock.FACING);
        } else {
            this.facing = Direction.NORTH;
        }

        Vec3i origin = ctx.renderOrigin();
        this.localX = pos.getX() - origin.getX();
        this.localY = pos.getY() - origin.getY();
        this.localZ = pos.getZ() - origin.getZ();

        this.base = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.WATER_PUMP)).createInstance();
        this.shaft = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.SHAFT_MODELS.get("shaft_light_iron"))).createInstance();

        setupStaticBase();
        updateLight(partialTick);
    }

    private void setupStaticBase() {
        base.setIdentityTransform()
                .translate(localX, localY, localZ)
                .translate(0.5f, 0.5f, 0.5f);

        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            base.rotateY((float) Math.toRadians(facing == Direction.EAST ? 270 : 90));
        } else if (facing == Direction.SOUTH) {
            base.rotateY((float) Math.toRadians(180));
        }

        base.translate(-0.5f + (0.6f / 16.0f), -1.5f, -0.5f);
        base.setChanged();
    }

    @Override
    public void beginFrame(Context context) {
        float partialTick = context.partialTick();
        float currentAngle = com.trd.client.rotation.ClientKineticAngleTracker.getAngle(blockEntity, facing, partialTick);

        shaft.setIdentityTransform()
                .translate(localX, localY, localZ)
                .translate(0.5f, 0.5f, 0.5f);

        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            shaft.rotateY((float) Math.toRadians(facing == Direction.EAST ? 270 : 90));
        } else if (facing == Direction.SOUTH) {
            shaft.rotateY((float) Math.toRadians(180));
        }

        shaft.rotateZ(currentAngle);
        shaft.translate(-0.5f, -0.5f, -0.5f);
        shaft.setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(pos, base, shaft);
    }

    @Override
    protected void _delete() {
        base.delete();
        shaft.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(base);
        consumer.accept(shaft);
    }
}
