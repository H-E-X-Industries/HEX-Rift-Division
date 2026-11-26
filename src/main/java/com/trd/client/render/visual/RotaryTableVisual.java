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
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Flywheel 1.0 визуал роторного стола:
 * статичная база (rotary_table_base.obj), вращающийся диск (rotary_disk.obj),
 * входной железный вал в кинетическом порту '%' и синхронизированная колонна труб (drill_pipe.obj).
 */
public class RotaryTableVisual extends AbstractBlockEntityVisual<RotaryTableBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance base;
    private final TransformedInstance disk;
    private final TransformedInstance portShaft;
    private final List<TransformedInstance> descendingPipes = new ArrayList<>(8);

    private final BlockPos wellCenter;
    private final float wellLocalX;
    private final float wellLocalY;
    private final float wellLocalZ;
    private final float baseYawRad;

    private final BlockPos portPos;
    private final Direction portSide;
    private final float portLocalX;
    private final float portLocalY;
    private final float portLocalZ;

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

        this.portPos = blockEntity.getKineticPortPos();
        this.portSide = blockEntity.getKineticPortSide();
        this.portLocalX = portPos.getX() - origin.getX();
        this.portLocalY = portPos.getY() - origin.getY();
        this.portLocalZ = portPos.getZ() - origin.getZ();

        this.base = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.ROTARY_TABLE_BASE)).createInstance();
        this.disk = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.ROTARY_DISK)).createInstance();
        this.portShaft = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.SHAFT_MODELS.get("shaft_light_iron"))).createInstance();

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
        float diskSpinRad = blockEntity.getDiskSpinAngleRad(partialTick);

        disk.setIdentityTransform()
                .translate(wellLocalX + 0.5f, wellLocalY, wellLocalZ + 0.5f)
                .rotateY(baseYawRad + diskSpinRad);
        disk.setChanged();

        float shaftAngleRad = ClientKineticAngleTracker.getAngle(blockEntity, portSide, partialTick);
        portShaft.setIdentityTransform()
                .translate(portLocalX, portLocalY, portLocalZ)
                .translate(0.5f, 0.5f, 0.5f);
        if (portSide.getAxis() == Direction.Axis.X) {
            portShaft.rotateY((float) Math.toRadians(portSide == Direction.EAST ? 270 : 90));
        } else if (portSide == Direction.SOUTH) {
            portShaft.rotateY((float) Math.toRadians(180));
        }
        portShaft.rotateZ(shaftAngleRad)
                .translate(-0.5f, -0.5f, -0.5f);
        portShaft.setChanged();

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
        relight(portPos, portShaft);

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
        portShaft.delete();
        for (TransformedInstance pipe : descendingPipes) {
            pipe.delete();
        }
        descendingPipes.clear();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(base);
        consumer.accept(disk);
        consumer.accept(portShaft);
    }
}
