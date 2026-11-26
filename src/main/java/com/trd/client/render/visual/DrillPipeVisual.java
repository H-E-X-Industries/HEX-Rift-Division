package com.trd.client.render.visual;

import com.trd.client.render.flywheel.ModModels;
import com.trd.multiblock.industrial.drilling.DrillPipeBlockEntity;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Flywheel 1.0 визуал буровой трубы (1×1×4).
 * Вычисляет освещение по координатам самого верхнего блока мультиблока (pos.above(3)).
 */
public class DrillPipeVisual extends AbstractBlockEntityVisual<DrillPipeBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance pipe;
    private final float localX;
    private final float localY;
    private final float localZ;
    private final float baseYawRad;

    public DrillPipeVisual(VisualizationContext ctx, DrillPipeBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        Vec3i origin = ctx.renderOrigin();
        this.localX = pos.getX() - origin.getX();
        this.localY = pos.getY() - origin.getY();
        this.localZ = pos.getZ() - origin.getZ();
        this.baseYawRad = (float) Math.toRadians(180.0f - blockEntity.getFacing().toYRot());

        this.pipe = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.DRILL_PIPE)).createInstance();

        updateTransform(partialTick);
        updateLight(partialTick);
    }

    @Override
    public void beginFrame(Context ctx) {
        updateTransform(ctx.partialTick());
        updateLight(ctx.partialTick());
    }

    private void updateTransform(float partialTick) {
        if (blockEntity.isHiddenForColumnDescent()) {
            pipe.setIdentityTransform().scale(0.0f);
        } else {
            float spin = blockEntity.getRenderSpinAngleRad(partialTick);
            pipe.setIdentityTransform()
                    .translate(localX + 0.5f, localY, localZ + 0.5f)
                    .rotateY(baseYawRad + spin);
        }
        pipe.setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        // Освещение трубы в скважине берётся по самому верхнему блоку (#, y = +3)
        relight(pos.above(3), pipe);
    }

    @Override
    protected void _delete() {
        pipe.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(pipe);
    }
}
