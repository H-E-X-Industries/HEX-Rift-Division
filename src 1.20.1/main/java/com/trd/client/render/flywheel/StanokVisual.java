package com.trd.client.render.flywheel;

import com.trd.multiblock.industrial.stanok.CarriageType;
import com.trd.multiblock.industrial.stanok.StanokBlockEntity;
import com.trd.multiblock.industrial.stanok.StanokRecipe;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Flywheel Visual для станка (stanok).
 *
 * Анимации:
 *   PRESS  — press_head движется вверх/вниз (0.22 блока), рендер 2D иконки предмета через стандартный item renderer
 *   WIRE   — два барабана вращаются вокруг оси X со скоростью кинетической сети
 *   FREZA  — каретка по X, крепление по Z+Y, фреза вращается + следует иерархии (этап 1: прямоугольник, этап 2: TRD)
 *
 * Все OBJ-части загружены с нужными координатами при экспорте из Blender;
 * через код добавляются только анимационные смещения.
 *
 * ВАЖНО: В Blender Y и Z местами относительно Minecraft.
 *   Blender (x, y, z) → Minecraft (x, z, y) при переводе координат.
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
    private float lastFrameTime = -1f;  // -1 = не инициализировано
    private long  lastNanoTime  = 0L;   // для delta через System.nanoTime()
    private float smoothedSpeed = 0f;
    private float lastValidPartialTick = 0.5f; // fallback если getFrameTime() вернул невалидное

    // ─── Состояние пресса ───
    // pressPhase: 0.0–1.0 = одна операция. Синхронизируем с BE.progress/maxProgress.
    // Рассчитывается каждый кадр на основе серверных данных + интерполяция partialTick.

    // ─── Состояние фрезы ───
    // Локальные переменные анимации TRD
    private float frezaStage2Time = 0f; // 0.0–4.0 секунды в этапе 2

    // ─── Интерполяция прогресса операции ───
    // Храним прогресс двух последних серверных тиков для плавной интерполяции
    private float prevAnimProgress = 0f;  // прогресс на предыдущем тике
    private float currAnimProgress = 0f;  // прогресс на текущем тике
    private int lastSeenServerProg  = -1; // для определения смены тика

    // Константы координат барабанов (Blender→MC: swap Y↔Z, /16 для блоков)
    // Blender left drum:  x=1.0618, y=1.2997, z=1.3301
    // Blockbench: Left X=-26.65, Right X=-20.77, Y=21.3, Z=-18.55 (сдвиг +2px на юг)
    private static final float DRUM_LEFT_X  = -1.66570625f;
    private static final float DRUM_RIGHT_X = -1.29851875f;
    private static final float DRUM_Y       = 1.33125f;
    private static final float DRUM_Z       = -1.034375f; // -1.159375 + 0.125

    // Blender/Blockbench: x=-1.23125, y=1.225, z=-0.9296875
    private static final float FREZA_X = -1.23125f;
    private static final float FREZA_Y = 1.225f;
    private static final float FREZA_Z = -0.9296875f;

    // Максимальный ход каретки фрезы по X
    private static final float FREZA_TRAVEL_X = 0.5f;
    // Максимальный ход крепления фрезы по Z (вниз)
    private static final float FREZA_TRAVEL_Z = 0.22f;
    // Максимальный подъём крепления по Y
    private static final float FREZA_TRAVEL_Y_UP = 0.0449f;

    private final net.minecraft.core.Vec3i renderOrigin;

    private TransformedInstance startTransform(TransformedInstance inst) {
        net.minecraft.core.Direction facing = blockEntity.getBlockState().getValue(com.trd.multiblock.industrial.stanok.StanokBlock.FACING);
        float facingRot = 0f;
        switch (facing) {
            case NORTH: facingRot = 180f; break;
            case EAST: facingRot = 90f; break;
            case SOUTH: facingRot = 0f; break;
            case WEST: facingRot = -90f; break;
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
        this.base       = createInstance(ModModels.STANOK_BASE);
        // Валы — берём готовую модель лёгкого титанового вала (как в помпе)
        dev.engine_room.flywheel.lib.model.baked.PartialModel shaftModel =
                ModModels.SHAFT_MODELS.get("shaft_light_titanium");
        this.shaftWest = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(shaftModel))
                .createInstance();
        this.shaftEast = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(shaftModel))
                .createInstance();

        setupStaticPart(base, 0, 0, 0);
        
        // 2. Валы: сдвиг на 1 блок на север (-1.0f) и 2 блока на запад (-2.0f) от их базовых позиций
        float shaftOffsetX = -2.0f;
        float shaftOffsetZ = -1.0f;
        setupStaticPartRotated(shaftWest, -1f + shaftOffsetX, 0, shaftOffsetZ, 90f); // Западный порт
        setupStaticPartRotated(shaftEast,  1f + shaftOffsetX, 0, shaftOffsetZ, 90f); // Восточный порт

        // Насадки создаём всегда, но показываем только нужные
        this.pressCarriage  = createInstance(ModModels.STANOK_PRESS_CARRIAGE);
        this.pressHead      = createInstance(ModModels.STANOK_PRESS_HEAD);
        this.wireCarriage   = createInstance(ModModels.STANOK_WIRE_CARRIAGE);
        this.wireDrumLeft   = createInstance(ModModels.STANOK_WIRE_DRUM);
        this.wireDrumRight  = createInstance(ModModels.STANOK_WIRE_DRUM);
        this.frezaCarriage  = createInstance(ModModels.STANOK_FREZA_CARRIAGE);
        this.frezaAttachment = createInstance(ModModels.STANOK_FREZA_ATTACHMENT);
        this.freza          = createInstance(ModModels.STANOK_FREZA);

        updateLight(partialTick);
    }

    private TransformedInstance createInstance(dev.engine_room.flywheel.lib.model.baked.PartialModel model) {
        return instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(model)).createInstance();
    }

    /** Устанавливает статичную позицию части (без поворота, только смещение от origin) */
    private void setupStaticPart(TransformedInstance inst, float dx, float dy, float dz) {
        startTransform(inst)
                .translate(dx, dy, dz)
                .translate(0.5f, 0.5f, 0.5f)
                .translate(-0.5f, -0.5f, -0.5f);
        inst.setChanged();
    }

    /** Устанавливает статичную позицию с поворотом вокруг Y */
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
        // ─── Вычисляем delta через реальное время (не зависит от Minecraft timer) ───
        long nowNanos = System.nanoTime();
        if (lastFrameTime < 0) {
            lastFrameTime = 0f;
            lastNanoTime = nowNanos;
        }
        float delta = (nowNanos - lastNanoTime) / 1_000_000_000f;
        // Ограничиваем delta: при зависании / первом кадре не делаем огромный скачок
        delta = Math.min(delta, 0.1f);
        lastNanoTime = nowNanos;

        // partialTick для интерполяции анимации
        float partialTick = Minecraft.getInstance().getFrameTime();
        // Защита: getFrameTime() может вернуть 0 в первый кадр или при некоторых условиях
        if (partialTick <= 0f || partialTick > 1f) partialTick = lastValidPartialTick;
        else lastValidPartialTick = partialTick;

        float timeInSeconds = (level.getGameTime() + partialTick) / 20.0f;

        // Синхронизация фазы с остальной кинетической сетью
        float targetSpeed = blockEntity.getVisualSpeed();
        if (this.smoothedSpeed == 0 && targetSpeed != 0) {
            this.smoothedSpeed = targetSpeed;
            this.shaftAngle = (timeInSeconds * targetSpeed * ((float) Math.PI / 30.0f)) % ((float) Math.PI * 2);
            if (this.shaftAngle < 0) this.shaftAngle += (float) Math.PI * 2;
        }

        float speedDiff = targetSpeed - smoothedSpeed;
        if (Math.abs(speedDiff) > 0.1f) smoothedSpeed += speedDiff * 4.0f * delta;
        else smoothedSpeed = targetSpeed;

        // Вращение накапливаем каждый кадр через delta (реальное время между кадрами)
        shaftAngle += smoothedSpeed * ((float) Math.PI / 30.0f) * delta;
        shaftAngle %= (float)(2 * Math.PI);
        if (shaftAngle < 0) shaftAngle += (float) Math.PI * 2;

        // ─── Интерполяция прогресса операции ───
        // Серверный прогресс меняется раз в тик. При каждом новом значении запоминаем
        // его и ожидаемое следующее, интерполируем по partialTick внутри тика.
        int serverProg = blockEntity.getData().get(0);
        int maxProg    = blockEntity.getData().get(1);

        float animPhase;
        if (maxProg <= 0 || serverProg <= 0) {
            // Не работает — сбрасываем
            animPhase = 0f;
            prevAnimProgress = 0f;
            currAnimProgress = 0f;
            lastSeenServerProg = -1;
        } else {
            if (serverProg != lastSeenServerProg) {
                // Новое значение от сервера: это наша «нижняя» точка тика
                prevAnimProgress = (float) serverProg / maxProg;
                currAnimProgress = (float)(serverProg + 1) / maxProg; // верхняя точка (следующий тик)
                lastSeenServerProg = serverProg;
            }
            // Интерполируем между prev (начало тика) и curr (конец тика) по partialTick
            animPhase = Math.max(0f, Math.min(1f, prevAnimProgress + (currAnimProgress - prevAnimProgress) * partialTick));
        }

        // Обновляем валы с вращением
        updateShaftInstances();

        // Определяем насадку
        CarriageType carriage = blockEntity.getCurrentCarriageType();

        // Обновляем визуалы
        updatePressVisual(carriage, animPhase);
        updateWireVisual(carriage, delta, partialTick);
        updateFrezaVisual(carriage, timeInSeconds, delta, animPhase);
    }

    // ════════════════════════════════════════════════════════════
    //  ВАЛЫ
    // ════════════════════════════════════════════════════════════

    /** Обновляет вращение валов в кинетических портах, синхронизируя с shaftAngle */
    private void updateShaftInstances() {
        float shaftOffsetX = -2.0f;
        float shaftOffsetZ = -1.0f;
        updateShaftInstance(shaftWest, -1f + shaftOffsetX, 0, shaftOffsetZ);
        updateShaftInstance(shaftEast,  1f + shaftOffsetX, 0, shaftOffsetZ);
    }

    /** Рендерит вал с вращением вокруг его оси (запад-восток = ось X, модель повёрнута rotateY(90°)) */
    private void updateShaftInstance(TransformedInstance inst, float dx, float dy, float dz) {
        startTransform(inst)
                .translate(dx, dy, dz)
                .translate(0.5f, 0.5f, 0.5f)
                .rotateY((float) Math.toRadians(90f))   // ориентация вала по оси X
                .rotateZ(-shaftAngle)                    // ИНВЕРСИЯ: вращение в правильную сторону относительно сети
                .translate(-0.5f, -0.5f, -0.5f);
        inst.setChanged();
    }

    // ════════════════════════════════════════════════════════════
    //  ПРЕСС
    // ════════════════════════════════════════════════════════════

    private void updatePressVisual(CarriageType carriage, float animPhase) {

        boolean active = carriage == CarriageType.PRESS;

        // Каретка (статична, просто показываем/скрываем)
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

            // Первая половина: голова идёт вниз (0 → 0.22)
            // Вторая половина: голова идёт вверх (0.22 → 0)
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

        // ВАЖНО: барабан должен иметь пивот в центре меша при экспорте из Blockbench!
        // Порядок трансформаций:
        // 1. Повернуть саму модель барабана на 90 градусов (rotateY)
        // 2. Вращать вокруг собственной оси Z (rotateZ)
        // 3. Переместить к позиции на станке (translate)
        float angle = reverse ? -shaftAngle : shaftAngle;
        startTransform(drum)
                .translate(dx, dy, dz)
                .rotateZ(angle) // ось вращения
                .rotateY((float) Math.toRadians(90)); // поворот модели на 90 градусов
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
        int recipeTime = recipe != null ? recipe.getProcessTicks() : 80; // 4 сек по умолчанию
        float recipeSeconds = recipeTime / 20.0f;

        // Абсолютное время в текущей операции (в секундах), интерполированное
        float opElapsed = animPhase * recipeSeconds;

        // Определяем текущий этап анимации
        float shiftX, shiftZ, shiftY;

        if (opElapsed < 4.0f) {
            // ──── Этап 1: прямоугольник 0.5 × 0.22 ────
            // Каждая сторона занимает 1 секунду из 4.
            float t = opElapsed;
            if (t < 1.0f) {
                // → вправо (0 → 0.5)
                shiftX = t * FREZA_TRAVEL_X;
                shiftZ = 0f;
                shiftY = 0f;
            } else if (t < 2.0f) {
                // ↓ вниз (0 → 0.22)
                shiftX = FREZA_TRAVEL_X;
                shiftZ = (t - 1.0f) * FREZA_TRAVEL_Z;
                shiftY = 0f;
            } else if (t < 3.0f) {
                // ← влево (0.5 → 0)
                shiftX = FREZA_TRAVEL_X - (t - 2.0f) * FREZA_TRAVEL_X;
                shiftZ = FREZA_TRAVEL_Z;
                shiftY = 0f;
            } else {
                // ↑ вверх (0.22 → 0)
                shiftX = 0f;
                shiftZ = FREZA_TRAVEL_Z - (t - 3.0f) * FREZA_TRAVEL_Z;
                shiftY = 0f;
            }
        } else if (opElapsed < 8.0f) {
            // ──── Этап 2: гравировка TRD (4 секунды) ────
            float t2 = opElapsed - 4.0f; // 0 – 4 сек
            float[] pos2 = computeTrdPath(t2);
            shiftX = pos2[0];
            shiftZ = pos2[1];
            shiftY = pos2[2]; // 0 или FREZA_TRAVEL_Y_UP
        } else {
            // Более длинные рецепты: чередуем этапы 1 и 2
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

        // Инвертируем оси X и Z как просил игрок (вместо юг/запад будет север/восток)
        shiftX = -shiftX;
        shiftZ = -shiftZ;

        // Вращение фрезы: в 5 раз быстрее вала
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
                .rotateY(frezaSpin); // frezaSpin теперь в радианах (основан на shaftAngle)
        freza.setChanged();
    }

    // ────────────────────────────────────────────────────────────
    //  Алгоритм пути TRD (этап 2, 4 секунды)
    //  Возвращает float[3] = {shiftX, shiftZ, shiftY}
    //  Координаты пути (X; Z) из ТЗ, Y = 0 (резка) или FREZA_TRAVEL_Y_UP (переход)
    // ────────────────────────────────────────────────────────────

    /**
     * Сегменты пути TRD.
     * Каждый сегмент: {x0, z0, x1, z1, isCutting}
     * Для дуг используем аппроксимацию через промежуточные точки (линейные сегменты).
     */
    private static final float[][] TRD_SEGMENTS;
    private static final float TRD_TOTAL_LEN;

    static {
        // Строим список сегментов по описанию из ТЗ
        // Все координаты в единицах блоков (0 – 0.5 по X, 0 – 0.22 по Z)
        java.util.List<float[]> segs = new java.util.ArrayList<>();

        // ── Буква T ──
        // 1. [РЕЗКА] (0,0)→(0.16,0)
        segs.add(new float[]{0f, 0f, 0.16f, 0f, 1f});
        // 2. [ПЕРЕХОД] → (0.08,0)
        segs.add(new float[]{0.16f, 0f, 0.08f, 0f, 0f});
        // 3. [РЕЗКА] (0.08,0)→(0.08,0.22)
        segs.add(new float[]{0.08f, 0f, 0.08f, 0.22f, 1f});
        // 4. [ПЕРЕХОД] → (0.16,0)
        segs.add(new float[]{0.08f, 0.22f, 0.16f, 0f, 0f});

        // ── Буква R ──
        // 5. [РЕЗКА] (0.16,0)→(0.2592,0)
        segs.add(new float[]{0.16f, 0f, 0.2592f, 0f, 1f});
        // 6. [РЕЗКА–ДУГА] полукруг 180° вправо, старт (0.2592,0) финиш (0.2592,0.11)
        //    центр дуги: (0.2592, 0.055), радиус 0.055
        addArcSegments(segs, 0.2592f, 0.055f, 0.055f, -90f, 180f, 8, 1f);
        // 7. [РЕЗКА] (0.2592,0.11)→(0.16,0.11)
        segs.add(new float[]{0.2592f, 0.11f, 0.16f, 0.11f, 1f});
        // 8. [РЕЗКА] диагональ (0.16,0.11)→(0.33,0.22)
        segs.add(new float[]{0.16f, 0.11f, 0.33f, 0.22f, 1f});
        // 9. [ПЕРЕХОД] → (0.16,0.22)
        segs.add(new float[]{0.33f, 0.22f, 0.16f, 0.22f, 0f});
        // 10. [РЕЗКА] (0.16,0.22)→(0.16,0) вертикальная мачта
        segs.add(new float[]{0.16f, 0.22f, 0.16f, 0f, 1f});
        // 11. [ПЕРЕХОД] → (0.33,0)
        segs.add(new float[]{0.16f, 0f, 0.33f, 0f, 0f});

        // ── Буква D ──
        // 12. [РЕЗКА] (0.33,0)→(0.4433,0)
        segs.add(new float[]{0.33f, 0f, 0.4433f, 0f, 1f});
        // 13. [РЕЗКА–ДУГА] верхнее закругление 90° (0.4433,0)→(0.5,0.044)
        //     центр: (0.4433, 0.044), радиус 0.044
        addArcSegments(segs, 0.4433f, 0.044f, 0.044f, -90f, 90f, 6, 1f);
        // 14. [РЕЗКА] (0.5,0.044)→(0.5,0.176)
        segs.add(new float[]{0.5f, 0.044f, 0.5f, 0.176f, 1f});
        // 15. [РЕЗКА–ДУГА] нижнее закругление 90° (0.5,0.176)→(0.4433,0.22)
        //     центр: (0.4433, 0.176), радиус 0.044
        addArcSegments(segs, 0.4433f, 0.176f, 0.044f, 0f, 90f, 6, 1f);
        // 16. [РЕЗКА] (0.4433,0.22)→(0.33,0.22)
        segs.add(new float[]{0.4433f, 0.22f, 0.33f, 0.22f, 1f});
        // 17. [РЕЗКА] (0.33,0.22)→(0.33,0) вертикальная мачта
        segs.add(new float[]{0.33f, 0.22f, 0.33f, 0f, 1f});

        TRD_SEGMENTS = segs.toArray(new float[0][]);

        // Считаем суммарную длину пути
        float total = 0;
        for (float[] seg : TRD_SEGMENTS) {
            float dx = seg[2] - seg[0];
            float dz = seg[3] - seg[1];
            total += (float) Math.sqrt(dx * dx + dz * dz);
        }
        TRD_TOTAL_LEN = total;
    }

    /**
     * Добавляет аппроксимированную дугу в список сегментов.
     * @param cx, cz — центр дуги
     * @param r       — радиус
     * @param startDeg — начальный угол в градусах
     * @param sweepDeg — угловой охват (положительный = против часовой стрелки)
     * @param steps    — количество линейных сегментов
     * @param cutting  — 1.0 = резка, 0.0 = переход
     */
    private static void addArcSegments(java.util.List<float[]> segs,
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

    /**
     * Вычисляет позицию фрезы на пути TRD в момент времени t [0, 4].
     * Скорость равномерная: V = TRD_TOTAL_LEN / 4.
     * @return float[3] = {shiftX, shiftZ, shiftY}
     */
    private static float[] computeTrdPath(float t) {
        if (TRD_TOTAL_LEN <= 0) return new float[]{0f, 0f, 0f};
        float speed  = TRD_TOTAL_LEN / 4.0f; // единиц блока в секунду
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
        // Конец пути
        float[] last = TRD_SEGMENTS[TRD_SEGMENTS.length - 1];
        return new float[]{last[2], last[3], 0f};
    }

    // ─── Вспомогательное: скрыть инстанс (уводим за сцену) ───
    private void hideInstance(TransformedInstance inst) {
        inst.setIdentityTransform().translate(0, -1000, 0);
        inst.setChanged();
    }

    private void hideNullable(@Nullable TransformedInstance inst) {
        if (inst != null) hideInstance(inst);
    }

    // ─── Освещение ───
    @Override
    public void updateLight(float partialTick) {
        relight(pos,
                base, shaftWest, shaftEast,
                pressCarriage, pressHead,
                wireCarriage, wireDrumLeft, wireDrumRight,
                frezaCarriage, frezaAttachment, freza);
    }

    // ─── Удаление ───
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
        if (pressCarriage  != null) consumer.accept(pressCarriage);
        if (pressHead      != null) consumer.accept(pressHead);
        if (wireCarriage   != null) consumer.accept(wireCarriage);
        if (wireDrumLeft   != null) consumer.accept(wireDrumLeft);
        if (wireDrumRight  != null) consumer.accept(wireDrumRight);
        if (frezaCarriage  != null) consumer.accept(frezaCarriage);
        if (frezaAttachment != null) consumer.accept(frezaAttachment);
        if (freza          != null) consumer.accept(freza);
    }
}
