package com.trd.client.render.flywheel;

import com.trd.multiblock.industrial.vishelashivatel.VishelashivatelBlockEntity;
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
 * Рендер половинки вала на ВЕРХНЕЙ грани контроллера выщелащивателя.
 * Корпус блока рендерится ванильно (skipVanillaRender = false),
 * Flywheel добавляет только вращающийся вал — как у мотора.
 */
public class VishelashivatelVisual extends AbstractBlockEntityVisual<VishelashivatelBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance shaft;
    private final TransformedInstance blades;

    // Локальные координаты относительно renderOrigin (ВАЖНО для Flywheel)
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
    private float lastFrameTime = -1.0f;

    @Override
    public void beginFrame(Context ctx) {
        float partialTick   = net.minecraft.client.Minecraft.getInstance().getFrameTime();
        float timeInSeconds = (level.getGameTime() + partialTick) / 20.0f;
        if (this.lastFrameTime < 0) this.lastFrameTime = timeInSeconds;
        float deltaSeconds = timeInSeconds - this.lastFrameTime;
        this.lastFrameTime = timeInSeconds;

        float physicalTargetSpeed = blockEntity.getVisualSpeed();
        float maxRenderSpeed = 300f; // анти-стробоскоп
        float targetSpeed = Math.abs(physicalTargetSpeed) > maxRenderSpeed
                ? Math.signum(physicalTargetSpeed) * maxRenderSpeed : physicalTargetSpeed;

        if (this.smoothedSpeed == 0 && targetSpeed != 0) { // мгновенный старт с правильной фазой
            this.smoothedSpeed = targetSpeed;
            this.currentAngle = (timeInSeconds * targetSpeed * ((float) Math.PI / 30.0f)) % ((float) Math.PI * 2);
            if (this.currentAngle < 0) this.currentAngle += (float) Math.PI * 2;
        }
        float speedDiff = targetSpeed - this.smoothedSpeed;
        if (Math.abs(speedDiff) > 0.1f) this.smoothedSpeed += speedDiff * 4.0f * deltaSeconds;
        else this.smoothedSpeed = targetSpeed;

        this.currentAngle += this.smoothedSpeed * ((float) Math.PI / 30.0f) * deltaSeconds; // RPM -> рад/с
        float twoPi = (float) (2 * Math.PI);
        this.currentAngle = this.currentAngle % twoPi;
        if (this.currentAngle < 0) this.currentAngle += twoPi;

        if (this.smoothedSpeed == targetSpeed && targetSpeed != 0) { // глобальная синхронизация фазы
            float globalAngle = (timeInSeconds * targetSpeed * ((float) Math.PI / 30.0f)) % twoPi;
            if (globalAngle < 0) globalAngle += twoPi;
            float diff = (globalAngle - this.currentAngle) % twoPi;
            if (diff > Math.PI) diff -= twoPi;
            if (diff < -Math.PI) diff += twoPi;
            this.currentAngle += diff * 10.0f * deltaSeconds;
        }
        if (targetSpeed == 0 && Math.abs(this.smoothedSpeed) < 5.0f) { // остановка — щелчок к PI/4
            float PI_OVER_4 = (float) (Math.PI / 4.0);
            float targetSnap = Math.round(this.currentAngle / PI_OVER_4) * PI_OVER_4;
            float snapDiff = targetSnap - this.currentAngle;
            if (Math.abs(snapDiff) > 0.001f) {
                float pull = 8.0f * (1.0f - (Math.abs(this.smoothedSpeed) / 5.0f));
                this.currentAngle += snapDiff * pull * deltaSeconds;
            } else this.currentAngle = targetSnap;
        }

        // === ПОЛОВИНКА ВАЛА НА ВЕРХНЕЙ ГРАНИ ===
        shaft.setIdentityTransform()
                .translate(localX, localY, localZ)
                .translate(0.5f, 0.5f, 0.5f)
                .rotateX((float) Math.toRadians(90))   // ориентация вверх (ветка UP из MotorVisual)
                .rotateZ(currentAngle)                 // спин вокруг собственной оси модели
                .translate(-0.5f, -0.5f, -0.5f);
        shaft.setChanged();

        // === ЛОПАСТИ ВЫЩЕЛАЧИВАТЕЛЯ ===
        blades.setIdentityTransform()
                .translate(localX + 0.5f, localY, localZ + 0.5f) // смещение позиции рендера на -0.5 по X и Y
                .rotateY(-currentAngle / 2.0f);                  // вращение в обратную сторону в 2 раза медленнее
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
