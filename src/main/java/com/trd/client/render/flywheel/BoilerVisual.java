package com.trd.client.render.flywheel;

import com.trd.multiblock.industrial.boiler.BoilerBlock;
import com.trd.multiblock.industrial.boiler.BoilerBlockEntity;
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

public class BoilerVisual extends AbstractBlockEntityVisual<BoilerBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance model;
    private final Direction facing;

    private final float localX;
    private final float localY;
    private final float localZ;

    public BoilerVisual(VisualizationContext ctx, BoilerBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        if (blockState.hasProperty(BoilerBlock.FACING)) {
            this.facing = blockState.getValue(BoilerBlock.FACING);
        } else {
            this.facing = Direction.NORTH;
        }

        Vec3i origin = ctx.renderOrigin();
        this.localX = pos.getX() - origin.getX();
        this.localY = pos.getY() - origin.getY();
        this.localZ = pos.getZ() - origin.getZ();

        this.model = instancerProvider().instancer(
                InstanceTypes.TRANSFORMED,
                Models.partial(ModModels.BOILER)
        ).createInstance();

        setupTransform();
        updateLight(partialTick);
    }

    private void setupTransform() {
        model.setIdentityTransform()
                .translate(localX, localY, localZ)
                .translate(0.5f, 0.5f, 0.5f);

        if (facing == Direction.SOUTH) {
            model.rotateY((float) Math.toRadians(180));
        } else if (facing == Direction.WEST) {
            model.rotateY((float) Math.toRadians(90));
        } else if (facing == Direction.EAST) {
            model.rotateY((float) Math.toRadians(270));
        }

        model.translate(-0.5f, -0.5f, -0.5f);
        model.translate(-1.0f, 0.0f, -1.0f);

        model.setChanged();
    }

    @Override
    public void beginFrame(Context ctx) {
    }

    @Override
    public void updateLight(float partialTick) {
        relight(pos, model);
    }

    @Override
    protected void _delete() {
        com.trd.client.sound.BoilerSoundHandler.stop(pos);
        model.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(model);
    }
}
