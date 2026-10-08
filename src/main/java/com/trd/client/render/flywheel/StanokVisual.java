package com.trd.client.render.flywheel;

import com.trd.multiblock.industrial.stanok.CarriageType;
import com.trd.multiblock.industrial.stanok.StanokBlock;
import com.trd.multiblock.industrial.stanok.StanokBlockEntity;
import com.trd.multiblock.industrial.stanok.StanokRecipe;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Flywheel Visual для станка (stanok).
 *
 * Анимации:
 *   PRESS  — press_head движется вверх/вниз (0.22 блока)
 *   WIRE   — два барабана вращаются вокруг оси X со скоростью кинетической сети
 *   FREZA  — каретка по X, крепление по Z+Y, фреза вращается + следует иерархии (этап 1: прямоугольник, этап 2: TRD)
 */
public class StanokVisual extends AbstractBlockEntityVisual<StanokBlockEntity> implements SimpleDynamicVisual {

    // ─── Статичные части ───
    private final TransformedInstance base;        // stanok.obj — всегда видим
    private final TransformedInstance shaftWest;   // лёгкий титановый вал (западный порт)
    private final TransformedInstance shaftEast;   // лёгкий титановый вал (восточный порт)

    // ─── Насадка PRESS ───
    @Nullable private TransformedInstance pressCarriage;
    @Nullable private TransformedInstance pressHead;

    // ─── Насадка WIRE ───
    @Nullable private TransformedInstance wireCarriage;
    @Nullable private TransformedInstance wireDrumLeft;
    @Nullable private TransformedInstance wireDrumRight;

    // ─── Насадка FREZA ───
    @Nullable private TransformedInstance frezaCarriage;
    @Nullable private TransformedInstance frezaAttachment;
    @Nullable private TransformedInstance freza;

    // ─── Общее состояние ───
    private float shaftAngle    = 0f;
    private float lastFrameTime = -1f;
    private long  lastNanoTime  = 0L;
    private float smoothedSpeed = 0f;
    private float lastValidPartialTick = 0.5f;

    // ─── Интерполяция прогресса операции ───
    private float prevAnimProgress = 0f;
    private float currAnimProgress = 0f;
    private int lastSeenServerProg  = -1;

    // Константы координат барабанов
    private static final float DRUM_LEFT_X  = -1.66570625f;
    private static final float DRUM_RIGHT_X = -1.29851875f;
    private static final float DRUM_Y       = 1.33125f;
    private static final float DRUM_Z       = -1.034375f;

    // Фреза
    private static final float FREZA_X = -1.23125f;
    private static final float FREZA_Y = 1.225f;
    private static final float FREZA_Z = -0.9296875f;

    private static final float FREZA_TRAVEL_X = 0.5f;
    private static final float FREZA_TRAVEL_Z = 0.22f;
    private static final float FREZA_TRAVEL_Y_UP = 0.0449f;

    private final Vec3i renderOrigin;

    private TransformedInstance startTransform(TransformedInstance inst) {
        Direction facing = blockEntity.getBlockState().getValue(StanokBlock.FACING);
        float facingRot = 0f;
        switch (facing) {
            case NORTH -> facingRot = 180f;
            case EAST -> facingRot = 90f;
            case SOUTH -> facingRot = 0f;
            case WEST -> facingRot = -90f;
        }

        float px = pos.getX() - renderOrigin.getX();
        float py = pos.getY() - renderOrigin.getY();
        float pz = pos.getZ() - renderOrigin.getZ();

        return inst.setIdentityTransform()
                .translate(px + 0.5f, py, pz + 0.5f)
                .rotateY((float) Math.toRadians(facingRot))
                .translate(-0.5f, 0, -0.5f)
                .translate(2.0f, 0f, 2.0f);
    }

    public StanokVisual(VisualizationContext ctx, StanokBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        this.renderOrigin = ctx.renderOrigin();

        // Статичные части
        this.base = createInstance(ModModels.STANOK_BASE);
        PartialModel shaftModel = ModModels.SHAFT_MODELS.get("shaft_light_titanium");
        this.shaftWest = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(shaftModel))
                .createInstance();
        this.shaftEast = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(shaftModel))
                .createInstance();

        setupStaticPart(base, 0, 0, 0);

        float shaftOffsetX = -2.0f;
        float shaftOffsetZ = -1.0f;
        setupStaticPartRotated(shaftWest, -1f + shaftOffsetX, 0, shaftOffsetZ, 90f);
        setupStaticPartRotated(shaftEast,  1f + shaftOffsetX, 0, shaftOffsetZ, 90f);

        // Насадки
        this.pressCarriage   = createInstance(ModModels.STANOK_PRESS_CARRIAGE);
        this.pressHead       = createInstance(ModModels.STANOK_PRESS_HEAD);
        this.wireCarriage    = createInstance(ModModels.STANOK_WIRE_CARRIAGE);
        this.wireDrumLeft    = createInstance(ModModels.STANOK_WIRE_DRUM);
        this.wireDrumRight   = createInstance(ModModels.STANOK_WIRE_DRUM);
        this.frezaCarriage   = createInstance(ModModels.STANOK_FREZA_CARRIAGE);
        this.frezaAttachment = createInstance(ModModels.STANOK_FREZA_ATTACHMENT);
        this.freza           = createInstance(ModModels.STANOK_FREZA);

        updateLight(partialTick);
    }

    private TransformedInstance createInstance(PartialModel model) {
        return instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(model)).createInstance();
    }

    private void setupStaticPart(TransformedInstance inst, float dx, float dy, float dz) {
        startTransform(inst)
                .translate(dx, dy, dz)
                .translate(0.5f, 0.5f, 0.5f)
                .translate(-0.5f, -0.5f, -0.5f);
        inst.setChanged();
    }

    private void setupStaticPartRotated(TransformedInstance inst, float dx, float dy, float dz, float rotYDegrees) {
        startTransform(inst)
                .translate(dx, dy, dz)
                .translate(0.5f, 0.5f, 0.5f)
                .rotateY((float) Math.toRadians(rotYDegrees))
                .translate(-0.5f, -0.5f, -0.5f);
        inst.setChanged();
    }

    @Override
    public void beginFrame(Context ctx) {
        long nowNanos = System.nanoTime();
        if (lastFrameTime < 0) {
            lastFrameTime = 0f;
            lastNanoTime = nowNanos;
        }
        float delta = (nowNanos - lastNanoTime) / 1_000_000_000f;
        delta = Math.min(delta, 0.1f);
        lastNanoTime = nowNanos;

        float partialTick = ctx.partialTick();
        if (partialTick <= 0f || partialTick > 1f) partialTick = lastValidPartialTick;
        else lastValidPartialTick = partialTick;

        float timeInSeconds = (level.getGameTime() + partialTick) / 20.0f;

        float targetSpeed = blockEntity.getVisualSpeed();
        if (this.smoothedSpeed == 0 && targetSpeed != 0) {
            this.smoothedSpeed = targetSpeed;
            this.shaftAngle = (timeInSeconds * targetSpeed * ((float) Math.PI / 30.0f)) % ((float) Math.PI * 2);
            if (this.shaftAngle < 0) this.shaftAngle += (float) Math.PI * 2;
        }

        float speedDiff = targetSpeed - smoothedSpeed;
        if (Math.abs(speedDiff) > 0.1f) smoothedSpeed += speedDiff * 4.0f * delta;
        else smoothedSpeed = targetSpeed;

        shaftAngle += smoothedSpeed * ((float) Math.PI / 30.0f) * delta;
        shaftAngle %= (float)(2 * Math.PI);
        if (shaftAngle < 0) shaftAngle += (float) Math.PI * 2;

        int serverProg = blockEntity.getData().get(0);
        int maxProg    = blockEntity.getData().get(1);

        float animPhase;
        if (maxProg <= 0 || serverProg <= 0) {
            animPhase = 0f;
            prevAnimProgress = 0f;
            currAnimProgress = 0f;
            lastSeenServerProg = -1;
        } else {
            if (serverProg != lastSeenServerProg) {
                prevAnimProgress = (float) serverProg / maxProg;
                currAnimProgress = (float)(serverProg + 1) / maxProg;
                lastSeenServerProg = serverProg;
            }
            animPhase = Math.max(0f, Math.min(1f, prevAnimProgress + (currAnimProgress - prevAnimProgress) * partialTick));
        }

        updateShaftInstances();

        CarriageType carriage = blockEntity.getCurrentCarriageType();

        updatePressVisual(carriage, animPhase);
        updateWireVisual(carriage, delta, partialTick);
        updateFrezaVisual(carriage, timeInSeconds, delta, animPhase);
    }

    // ════════════════════════════════════════════════════════════
    //  ВАЛЫ
    // ════════════════════════════════════════════════════════════

    private void updateShaftInstances() {
        float shaftOffsetX = -2.0f;
        float shaftOffsetZ = -1.0f;
        updateShaftInstance(shaftWest, -1f + shaftOffsetX, 0, shaftOffsetZ);
        updateShaftInstance(shaftEast,  1f + shaftOffsetX, 0, shaftOffsetZ);
    }

    private void updateShaftInstance(TransformedInstance inst, float dx, float dy, float dz) {
        startTransform(inst)
                .translate(dx, dy, dz)
                .translate(0.5f, 0.5f, 0.5f)
                .rotateY((float) Math.toRadians(90f))
                .rotateZ(-shaftAngle)
                .translate(-0.5f, -0.5f, -0.5f);
        inst.setChanged();
    }

    // ════════════════════════════════════════════════════════════
    //  ПРЕСС
    // ════════════════════════════════════════════════════════════

    private void updatePressVisual(CarriageType carriage, float animPhase) {
        boolean active = carriage == CarriageType.PRESS;

        if (pressCarriage != null) {
            if (active) {
                startTransform(pressCarriage)
                        .translate(0.5f, 0.5f, 0.5f)
                        .translate(-0.5f, -0.5f, -0.5f);
                pressCarriage.setChanged();
            } else {
                hideInstance(pressCarriage);
            }
        }

        if (pressHead != null) {
            if (!active) {
                hideInstance(pressHead);
                return;
            }

            float headOffsetY;
            if (animPhase < 0.5f) {
                headOffsetY = -(animPhase / 0.5f) * 0.22f;  // вниз
            } else {
                headOffsetY = -((1.0f - animPhase) / 0.5f) * 0.22f; // вверх
            }

            startTransform(pressHead)
                    .translate(0, headOffsetY, 0)
                    .translate(0.5f, 0.5f, 0.5f)
                    .translate(-0.5f, -0.5f, -0.5f);
            pressHead.setChanged();
        }
    }

    // ════════════════════════════════════════════════════════════
    //  БАРАБАНЫ
    // ════════════════════════════════════════════════════════════

    private void updateWireVisual(CarriageType carriage, float delta, float partialTick) {
        boolean active = carriage == CarriageType.WIRE;

        if (wireCarriage != null) {
            if (active) {
                startTransform(wireCarriage)
                        .translate(0.5f, 0.5f, 0.5f)
                        .translate(-0.5f, -0.5f, -0.5f);
                wireCarriage.setChanged();
            } else {
                hideInstance(wireCarriage);
            }
        }

        updateDrum(wireDrumLeft,  active, DRUM_LEFT_X, DRUM_Y, DRUM_Z, false);
        updateDrum(wireDrumRight, active, DRUM_RIGHT_X, DRUM_Y, DRUM_Z, true);
    }

    private void updateDrum(@Nullable TransformedInstance drum, boolean active,
                             float dx, float dy, float dz, boolean reverse) {
        if (drum == null) return;
        if (!active) { hideInstance(drum); return; }

        float angle = reverse ? -shaftAngle : shaftAngle;
        startTransform(drum)
                .translate(dx, dy, dz)
                .rotateZ(angle)
                .rotateY((float) Math.toRadians(90));
        drum.setChanged();
    }

    // ════════════════════════════════════════════════════════════
    //  ФРЕЗА
    // ════════════════════════════════════════════════════════════

    private void updateFrezaVisual(CarriageType carriage, float timeInSeconds, float delta, float animPhase) {
        boolean active = carriage == CarriageType.FREZA;

        if (!active) {
            hideNullable(frezaCarriage);
            hideNullable(frezaAttachment);
            hideNullable(freza);
            return;
        }

        StanokRecipe recipe = blockEntity.getCurrentRecipe();
        int recipeTime = recipe != null ? recipe.getProcessTicks() : 80;
        float recipeSeconds = recipeTime / 20.0f;

        float opElapsed = animPhase * recipeSeconds;

        float shiftX, shiftZ, shiftY;

        if (opElapsed < 4.0f) {
            float t = opElapsed;
            if (t < 1.0f) {
                shiftX = t * FREZA_TRAVEL_X;
                shiftZ = 0f;
                shiftY = 0f;
            } else if (t < 2.0f) {
                shiftX = FREZA_TRAVEL_X;
                shiftZ = (t - 1.0f) * FREZA_TRAVEL_Z;
                shiftY = 0f;
            } else if (t < 3.0f) {
                shiftX = FREZA_TRAVEL_X - (t - 2.0f) * FREZA_TRAVEL_X;
                shiftZ = FREZA_TRAVEL_Z;
                shiftY = 0f;
            } else {
                shiftX = 0f;
                shiftZ = FREZA_TRAVEL_Z - (t - 3.0f) * FREZA_TRAVEL_Z;
                shiftY = 0f;
            }
        } else if (opElapsed < 8.0f) {
            float t2 = opElapsed - 4.0f;
            float[] pos2 = computeTrdPath(t2);
            shiftX = pos2[0];
            shiftZ = pos2[1];
            shiftY = pos2[2];
        } else {
            float loopTime = opElapsed % 8.0f;
            if (loopTime < 4.0f) {
                float t = loopTime;
                if (t < 1.0f) {
                    shiftX = t * FREZA_TRAVEL_X; shiftZ = 0f; shiftY = 0f;
                } else if (t < 2.0f) {
                    shiftX = FREZA_TRAVEL_X; shiftZ = (t - 1.0f) * FREZA_TRAVEL_Z; shiftY = 0f;
                } else if (t < 3.0f) {
                    shiftX = FREZA_TRAVEL_X - (t - 2.0f) * FREZA_TRAVEL_X; shiftZ = FREZA_TRAVEL_Z; shiftY = 0f;
                } else {
                    shiftX = 0f; shiftZ = FREZA_TRAVEL_Z - (t - 3.0f) * FREZA_TRAVEL_Z; shiftY = 0f;
                }
            } else {
                float[] pos2 = computeTrdPath(loopTime - 4.0f);
                shiftX = pos2[0]; shiftZ = pos2[1]; shiftY = pos2[2];
            }
        }

        shiftX = -shiftX;
        shiftZ = -shiftZ;

        float frezaSpin = shaftAngle * 5.0f;
        applyFrezaCarriage(shiftX);
        applyFrezaAttachment(shiftZ, shiftY, shiftX);
        applyFreza(shiftX, shiftZ, shiftY, frezaSpin);
    }

    private void applyFrezaCarriage(float shiftX) {
        if (frezaCarriage == null) return;
        startTransform(frezaCarriage)
                .translate(shiftX, 0, 0)
                .translate(0.5f, 0.5f, 0.5f)
                .translate(-0.5f, -0.5f, -0.5f);
        frezaCarriage.setChanged();
    }

    private void applyFrezaAttachment(float shiftZ, float shiftY, float shiftX) {
        if (frezaAttachment == null) return;
        startTransform(frezaAttachment)
                .translate(shiftX, shiftY, shiftZ)
                .translate(0.5f, 0.5f, 0.5f)
                .translate(-0.5f, -0.5f, -0.5f);
        frezaAttachment.setChanged();
    }

    private void applyFreza(float shiftX, float shiftZ, float shiftY, float frezaSpin) {
        if (freza == null) return;
        startTransform(freza)
                .translate(shiftX, shiftY, shiftZ)
                .translate(FREZA_X, FREZA_Y, FREZA_Z)
                .translate(0.5f, 0.5f, 0.5f)
                .translate(-0.5f, -0.5f, -0.5f)
                .rotateY(frezaSpin);
        freza.setChanged();
    }

    // ────────────────────────────────────────────────────────────
    //  Алгоритм пути TRD (этап 2, 4 секунды)
    // ────────────────────────────────────────────────────────────

    private static final float[][] TRD_SEGMENTS;
    private static final float TRD_TOTAL_LEN;

    static {
        List<float[]> segs = new ArrayList<>();

        // ── Буква T ──
        segs.add(new float[]{0f, 0f, 0.16f, 0f, 1f});
        segs.add(new float[]{0.16f, 0f, 0.08f, 0f, 0f});
        segs.add(new float[]{0.08f, 0f, 0.08f, 0.22f, 1f});
        segs.add(new float[]{0.08f, 0.22f, 0.16f, 0f, 0f});

        // ── Буква R ──
        segs.add(new float[]{0.16f, 0f, 0.2592f, 0f, 1f});
        addArcSegments(segs, 0.2592f, 0.055f, 0.055f, -90f, 180f, 8, 1f);
        segs.add(new float[]{0.2592f, 0.11f, 0.16f, 0.11f, 1f});
        segs.add(new float[]{0.16f, 0.11f, 0.33f, 0.22f, 1f});
        segs.add(new float[]{0.33f, 0.22f, 0.16f, 0.22f, 0f});
        segs.add(new float[]{0.16f, 0.22f, 0.16f, 0f, 1f});
        segs.add(new float[]{0.16f, 0f, 0.33f, 0f, 0f});

        // ── Буква D ──
        segs.add(new float[]{0.33f, 0f, 0.4433f, 0f, 1f});
        addArcSegments(segs, 0.4433f, 0.044f, 0.044f, -90f, 90f, 6, 1f);
        segs.add(new float[]{0.5f, 0.044f, 0.5f, 0.176f, 1f});
        addArcSegments(segs, 0.4433f, 0.176f, 0.044f, 0f, 90f, 6, 1f);
        segs.add(new float[]{0.4433f, 0.22f, 0.33f, 0.22f, 1f});
        segs.add(new float[]{0.33f, 0.22f, 0.33f, 0f, 1f});

        TRD_SEGMENTS = segs.toArray(new float[0][]);

        float total = 0;
        for (float[] seg : TRD_SEGMENTS) {
            float dx = seg[2] - seg[0];
            float dz = seg[3] - seg[1];
            total += (float) Math.sqrt(dx * dx + dz * dz);
        }
        TRD_TOTAL_LEN = total;
    }

    private static void addArcSegments(List<float[]> segs,
                                        float cx, float cz, float r,
                                        float startDeg, float sweepDeg, int steps, float cutting) {
        float startRad = (float) Math.toRadians(startDeg);
        float sweepRad = (float) Math.toRadians(sweepDeg);
        float prevX = cx + r * (float) Math.cos(startRad);
        float prevZ = cz + r * (float) Math.sin(startRad);
        for (int i = 1; i <= steps; i++) {
            float angle = startRad + sweepRad * i / steps;
            float nx = cx + r * (float) Math.cos(angle);
            float nz = cz + r * (float) Math.sin(angle);
            segs.add(new float[]{prevX, prevZ, nx, nz, cutting});
            prevX = nx;
            prevZ = nz;
        }
    }

    public static float[] computeTrdPath(float t) {
        if (TRD_TOTAL_LEN <= 0) return new float[]{0f, 0f, 0f};
        float speed  = TRD_TOTAL_LEN / 4.0f;
        float target = Math.min(t * speed, TRD_TOTAL_LEN);

        float traversed = 0f;
        for (float[] seg : TRD_SEGMENTS) {
            float x0 = seg[0], z0 = seg[1], x1 = seg[2], z1 = seg[3];
            boolean cutting = seg[4] > 0.5f;
            float len = (float) Math.sqrt((x1 - x0) * (x1 - x0) + (z1 - z0) * (z1 - z0));
            if (len < 1e-6f) continue;
            if (traversed + len >= target) {
                float frac = (target - traversed) / len;
                float rx = x0 + (x1 - x0) * frac;
                float rz = z0 + (z1 - z0) * frac;
                float ry = cutting ? 0f : FREZA_TRAVEL_Y_UP;
                return new float[]{rx, rz, ry};
            }
            traversed += len;
        }
        float[] last = TRD_SEGMENTS[TRD_SEGMENTS.length - 1];
        return new float[]{last[2], last[3], 0f};
    }

    private void hideInstance(TransformedInstance inst) {
        inst.setIdentityTransform().translate(0, -1000, 0);
        inst.setChanged();
    }

    private void hideNullable(@Nullable TransformedInstance inst) {
        if (inst != null) hideInstance(inst);
    }

    @Override
    public void updateLight(float partialTick) {
        relight(pos,
                base, shaftWest, shaftEast,
                pressCarriage, pressHead,
                wireCarriage, wireDrumLeft, wireDrumRight,
                frezaCarriage, frezaAttachment, freza);
    }

    @Override
    protected void _delete() {
        com.trd.client.sound.StanokSoundHandler.stop(pos);
        deleteIfNotNull(base, shaftWest, shaftEast,
                pressCarriage, pressHead,
                wireCarriage, wireDrumLeft, wireDrumRight,
                frezaCarriage, frezaAttachment, freza);
    }

    private void deleteIfNotNull(TransformedInstance... instances) {
        for (TransformedInstance inst : instances) {
            if (inst != null) inst.delete();
        }
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(base);
        consumer.accept(shaftWest);
        consumer.accept(shaftEast);
        if (pressCarriage   != null) consumer.accept(pressCarriage);
        if (pressHead       != null) consumer.accept(pressHead);
        if (wireCarriage    != null) consumer.accept(wireCarriage);
        if (wireDrumLeft    != null) consumer.accept(wireDrumLeft);
        if (wireDrumRight   != null) consumer.accept(wireDrumRight);
        if (frezaCarriage   != null) consumer.accept(frezaCarriage);
        if (frezaAttachment != null) consumer.accept(frezaAttachment);
        if (freza           != null) consumer.accept(freza);
    }
}
