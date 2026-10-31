package com.trd.client.render.visual;

import com.trd.client.render.flywheel.ModModels;
import com.trd.block.basic.industrial.rotation.HandCrankBlock;
import com.trd.block.entity.industrial.rotation.HandCrankBlockEntity;
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

public class HandCrankVisual extends AbstractBlockEntityVisual<HandCrankBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance handle;

    private final Direction facing;
    private final float localX;
    private final float localY;
    private final float localZ;

    public HandCrankVisual(VisualizationContext ctx, HandCrankBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        if (blockState.hasProperty(HandCrankBlock.FACING)) {
            this.facing = blockState.getValue(HandCrankBlock.FACING);
        } else {
            this.facing = Direction.NORTH;
        }

        Vec3i origin = ctx.renderOrigin();
        this.localX = pos.getX() - origin.getX();
        this.localY = pos.getY() - origin.getY();
        this.localZ = pos.getZ() - origin.getZ();

        this.handle = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.HAND_CRANK)).createInstance();

        updateLight(partialTick);
    }

    @Override
    public void beginFrame(Context context) {
        // Используем общий сетевой трекер углов, как ShaftVisual/BearingVisual:
        // все узлы одной кинетической сети получают ОДИН угол (синхронно по фазе),
        // а ручной привод больше не дрейфует относительно вала, на который он стоит.
        // getNetworkScale()/знак инверсии уже учтены внутри трекера.
        float currentAngle = com.trd.client.rotation.ClientKineticAngleTracker
                .getAngle(blockEntity, facing, context.partialTick());

        handle.setIdentityTransform()
                .translate(localX, localY, localZ)
                .translate(0.5f, 0.5f, 0.5f);

        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            handle.rotateY((float) Math.toRadians(facing == Direction.EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Z) {
            if (facing == Direction.SOUTH) {
                handle.rotateY((float) Math.toRadians(180));
            }
        } else if (axis == Direction.Axis.Y) {
            handle.rotateX((float) Math.toRadians(facing == Direction.UP ? 270 : 90));
        }

        handle.rotateZ(currentAngle);
        handle.translate(-0.5f, -0.5f, -0.5f);
        handle.setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(pos, handle);
    }

    @Override
    protected void _delete() {
        handle.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(handle);
    }
}
