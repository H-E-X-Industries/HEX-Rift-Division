package com.trd.client.render.visual;

import com.trd.api.industiral.rotation.ClientKineticAngleTracker;
import com.trd.client.render.FlywheelFallbackHelper;
import com.trd.client.render.flywheel.ModModels;
import com.trd.multiblock.industrial.drilling.RotaryTableBlockEntity;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Flywheel 1.0 визуал роторного стола:
 * статичная база (rotary_table_base.obj), вращающийся диск (rotary_disk.obj)
 * и синхронизированная колонна труб (drill_pipe.obj) во время плавного спуска.
 */
public class RotaryTableVisual extends AbstractBlockEntityVisual<RotaryTableBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance base;
    private final TransformedInstance disk;
    private final List<TransformedInstance> descendingPipes = new ArrayList<>(8);

    private final BlockPos wellCenter;
    private final float wellLocalX;
    private final float wellLocalY;
    private final float wellLocalZ;
    private final float baseYawRad;

    private final BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();
    private boolean prevAssembled;
    private boolean prevHologram;

    public RotaryTableVisual(VisualizationContext ctx, RotaryTableBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        Vec3i origin = ctx.renderOrigin();
        this.wellCenter = blockEntity.getWellCenterPos();
        this.wellLocalX = wellCenter.getX() - origin.getX();
        this.wellLocalY = wellCenter.getY() - origin.getY();
        this.wellLocalZ = wellCenter.getZ() - origin.getZ();
        this.baseYawRad = (float) Math.toRadians(180.0f - blockEntity.getDriveFacing().toYRot());

        this.base = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.ROTARY_TABLE_BASE)).createInstance();
        this.disk = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.ROTARY_DISK)).createInstance();

        this.prevAssembled = blockEntity.isDerrickAssembled();
        this.prevHologram = blockEntity.shouldRenderStage2Hologram();

        this.base.setIdentityTransform()
                .translate(wellLocalX + 0.5f, wellLocalY, wellLocalZ + 0.5f)
                .rotateY(baseYawRad);
        this.base.setChanged();

        updateDynamicTransforms(partialTick);
        updateLight(partialTick);
    }

    @Override
    public void beginFrame(Context ctx) {
        boolean assembled = blockEntity.isDerrickAssembled();
        boolean hologram = blockEntity.shouldRenderStage2Hologram();
        if (assembled != prevAssembled || hologram != prevHologram) {
            prevAssembled = assembled;
            prevHologram = hologram;
            FlywheelFallbackHelper.queueBlockEntityUpdate(blockEntity);
        }

        updateDynamicTransforms(ctx.partialTick());
        updateLight(ctx.partialTick());
    }

    private void updateDynamicTransforms(float partialTick) {
        float diskSpinRad;
        if (!blockEntity.isExtractingStopped()) {
            diskSpinRad = ClientKineticAngleTracker.getAngle(blockEntity, null, partialTick);
            blockEntity.setFrozenDiskAngle(diskSpinRad);
        } else {
            diskSpinRad = blockEntity.getFrozenDiskAngle();
        }

        disk.setIdentityTransform()
                .translate(wellLocalX + 0.5f, wellLocalY, wellLocalZ + 0.5f)
                .rotateY(baseYawRad + diskSpinRad);
        disk.setChanged();

        int activeCount = blockEntity.isDescendingPipe() ? (blockEntity.getPipeCountInWell() + 1) : 0;
        if (activeCount > descendingPipes.size()) {
            var pipeModel = Models.partial(ModModels.DRILL_PIPE);
            while (descendingPipes.size() < activeCount) {
                descendingPipes.add(instancerProvider().instancer(InstanceTypes.TRANSFORMED, pipeModel).createInstance());
            }
        }

        float descent = blockEntity.getVisualDescentBlocks(partialTick);
        for (int i = 0; i < descendingPipes.size(); i++) {
            TransformedInstance pipe = descendingPipes.get(i);
            if (i < activeCount) {
                float bottomLocalY = wellLocalY - 4.0f * i - descent;
                pipe.setIdentityTransform()
                        .translate(wellLocalX + 0.5f, bottomLocalY, wellLocalZ + 0.5f)
                        .rotateY(baseYawRad + diskSpinRad);
            } else {
                pipe.setIdentityTransform().scale(0.0f);
            }
            pipe.setChanged();
        }
    }

    @Override
    public void updateLight(float partialTick) {
        relight(wellCenter, base, disk);

        int activeCount = blockEntity.isDescendingPipe() ? (blockEntity.getPipeCountInWell() + 1) : 0;
        float descent = blockEntity.getVisualDescentBlocks(partialTick);
        for (int i = 0; i < activeCount && i < descendingPipes.size(); i++) {
            int topBlockY = Mth.floor(wellCenter.getY() - 4.0f * i - descent + 3.0f);
            lightPos.set(wellCenter.getX(), topBlockY, wellCenter.getZ());
            relight(lightPos, descendingPipes.get(i));
        }
    }

    @Override
    protected void _delete() {
        base.delete();
        disk.delete();
        for (TransformedInstance pipe : descendingPipes) {
            pipe.delete();
        }
        descendingPipes.clear();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(base);
        consumer.accept(disk);
    }
}
