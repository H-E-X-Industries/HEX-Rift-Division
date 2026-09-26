package com.trd.client.render.flywheel;

import com.trd.client.rotation.ClientKineticAngleTracker;
import com.trd.multiblock.industrial.vishelashivatel.VishelashivatelBlockEntity;
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

/**
 * Рендер половинки вала на ВЕРХНЕЙ грани контроллера выщелащивателя.
 * Корпус блока рендерится ванильно,
 * Flywheel добавляет вращающийся вал и лопасти.
 */
public class VishelashivatelVisual extends AbstractBlockEntityVisual<VishelashivatelBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance shaft;
    private final TransformedInstance blades;

    private final float localX;
    private final float localY;
    private final float localZ;

    public VishelashivatelVisual(VisualizationContext ctx, VishelashivatelBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        Vec3i origin = ctx.renderOrigin();
        this.localX = pos.getX() - origin.getX();
        this.localY = pos.getY() - origin.getY();
        this.localZ = pos.getZ() - origin.getZ();

        this.shaft = instancerProvider().instancer(InstanceTypes.TRANSFORMED,
                Models.partial(ModModels.HALF_SHAFT)).createInstance();
                
        this.blades = instancerProvider().instancer(InstanceTypes.TRANSFORMED,
                Models.partial(ModModels.VISHELACHIVATEL_LOPASTI)).createInstance();

        updateLight(partialTick);
    }

    private float smoothedSpeed = 0f;
    private float currentAngle = 0f;
    private float bladeAngle = 0f;
    private float lastFrameTime = -1.0f;

    @Override
    public void beginFrame(Context ctx) {
        float partialTick = ctx.partialTick();
        float timeInSeconds = (level.getGameTime() + partialTick) / 20.0f;
        if (this.lastFrameTime < 0) this.lastFrameTime = timeInSeconds;
        float deltaSeconds = timeInSeconds - this.lastFrameTime;
        if (deltaSeconds > 0.25f || deltaSeconds <= 0f) deltaSeconds = 0.016f;
        this.lastFrameTime = timeInSeconds;

        float physicalTargetSpeed = blockEntity.getVisualSpeed();
        float maxRenderSpeed = 300f; // анти-стробоскоп
        float targetSpeed = Math.abs(physicalTargetSpeed) > maxRenderSpeed
                ? Math.signum(physicalTargetSpeed) * maxRenderSpeed : physicalTargetSpeed;

        if (Math.abs(blockEntity.getSpeed()) > 0) {
            this.currentAngle = ClientKineticAngleTracker.getAngle(blockEntity, Direction.UP, partialTick);
            this.smoothedSpeed = blockEntity.getSpeed();
        } else {
            float speedDiff = targetSpeed - this.smoothedSpeed;
            if (Math.abs(speedDiff) > 0.01f) this.smoothedSpeed += speedDiff * 5.0f * deltaSeconds;
            else this.smoothedSpeed = targetSpeed;

            this.currentAngle += this.smoothedSpeed * ((float) Math.PI / 30.0f) * deltaSeconds;
            float twoPi = (float) (2 * Math.PI);
            this.currentAngle = this.currentAngle % twoPi;
            if (this.currentAngle < 0) this.currentAngle += twoPi;

            if (targetSpeed == 0 && Math.abs(this.smoothedSpeed) < 5.0f) {
                float PI_OVER_4 = (float) (Math.PI / 4.0);
                float targetSnap = Math.round(this.currentAngle / PI_OVER_4) * PI_OVER_4;
                float snapDiff = targetSnap - this.currentAngle;
                if (Math.abs(snapDiff) > 0.001f) {
                    float pull = 6.0f * (1.0f - (Math.abs(this.smoothedSpeed) / 5.0f));
                    this.currentAngle += snapDiff * pull * deltaSeconds;
                } else this.currentAngle = targetSnap;
            }
        }

        // Лопасти вращаются непрерывно с редукцией 1:2
        float twoPi = (float) (2 * Math.PI);
        this.bladeAngle += (-this.smoothedSpeed / 2.0f) * ((float) Math.PI / 30.0f) * deltaSeconds;
        this.bladeAngle = this.bladeAngle % twoPi;
        if (this.bladeAngle < 0) this.bladeAngle += twoPi;

        // === ПОЛОВИНКА ВАЛА НА ВЕРХНЕЙ ГРАНИ ===
        shaft.setIdentityTransform()
                .translate(localX, localY, localZ)
                .translate(0.5f, 0.5f, 0.5f)
                .rotateX((float) Math.toRadians(90))
                .rotateZ(currentAngle)
                .translate(-0.5f, -0.5f, -0.5f);
        shaft.setChanged();

        // === ЛОПАСТИ ВЫЩЕЛАЧИВАТЕЛЯ ===
        blades.setIdentityTransform()
                .translate(localX + 0.5f, localY, localZ + 0.5f)
                .rotateY(bladeAngle);
        blades.setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(pos, shaft, blades);
    }

    @Override
    protected void _delete() {
        shaft.delete();
        blades.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(shaft);
        consumer.accept(blades);
    }
}
