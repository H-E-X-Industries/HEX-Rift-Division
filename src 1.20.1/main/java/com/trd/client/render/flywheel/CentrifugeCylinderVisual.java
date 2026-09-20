package com.trd.client.render.flywheel;

import com.trd.multiblock.industrial.centrifuge.cylinder.CentrifugeCylinderBlockEntity;
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

public class CentrifugeCylinderVisual extends AbstractBlockEntityVisual<CentrifugeCylinderBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance blades;
    private final float localX;
    private final float localY;
    private final float localZ;

    public CentrifugeCylinderVisual(VisualizationContext ctx, CentrifugeCylinderBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        Vec3i origin = ctx.renderOrigin();
        this.localX = pos.getX() - origin.getX();
        this.localY = pos.getY() - origin.getY();
        this.localZ = pos.getZ() - origin.getZ();

        this.blades = instancerProvider().instancer(InstanceTypes.TRANSFORMED,
                Models.partial(ModModels.CENTRIFUGE_CYLINDER_LOPASTI)).createInstance();

        updateLight(partialTick);
    }

    private float smoothedSpeed = 0f;
    private float currentAngle = 0f;
    private float lastFrameTime = -1.0f;

    @Override
    public void beginFrame(Context ctx) {
        float partialTick = net.minecraft.client.Minecraft.getInstance().getFrameTime();
        float timeInSeconds = (level.getGameTime() + partialTick) / 20.0f;
        if (this.lastFrameTime < 0) this.lastFrameTime = timeInSeconds;
        float deltaSeconds = timeInSeconds - this.lastFrameTime;
        this.lastFrameTime = timeInSeconds;

        // Если есть прогресс (идёт крафт), скорость 120 RPM, иначе 0
        float targetSpeed = (blockEntity.getProgress() > 0) ? 120f : 0f;

        if (this.smoothedSpeed == 0 && targetSpeed != 0) { // мгновенный старт
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

        this.currentAngle += this.smoothedSpeed * ((float) Math.PI / 30.0f) * deltaSeconds; // RPM -> рад/с
        float twoPi = (float) (2 * Math.PI);
        this.currentAngle = this.currentAngle % twoPi;
        if (this.currentAngle < 0) this.currentAngle += twoPi;

        blades.setIdentityTransform()
                .translate(localX + 0.5f, localY, localZ + 0.5f)
                .rotateY(currentAngle); // вращение вокруг оси Y (центр модели уже в 0,0,0)
        blades.setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(pos, blades);
    }

    @Override
    protected void _delete() {
        blades.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(blades);
    }
}
