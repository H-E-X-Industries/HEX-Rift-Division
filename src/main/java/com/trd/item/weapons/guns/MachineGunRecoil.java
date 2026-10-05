package com.trd.item.weapons.guns;

import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.util.Random;

/**
 * Отдача пушки: плавные подёргивания взгляда стрелка.
 *
 * <p>Отдача бьёт по настоящему повороту игрока ({@code setXRot} / {@code setYRot}),
 * оставаясь в повороте без рассинхрона с пулей и прицелом.
 *
 * <p>Знак по обеим осям случаен (вверх/вниз и влево/вправо), создавая динамическое
 * подёргивание оружия вокруг точки прицеливания без постоянного увода прицела в небо.
 *
 * <p>Чтобы подёргивания не рябили и не стробили резкими скачками, каждый
 * выстрел заносит импульсы в буфер ({@code pendingPitch} / {@code pendingYaw}),
 * а рендер-обработчик {@link RenderFrameEvent.Pre} плавно переносит их в поворот
 * игрока перед каждым кадром через экспоненциальное сглаживание по {@code deltaTime}.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public final class MachineGunRecoil {

    private static final Random RANDOM = new Random();

    /** Скорость сглаживания подёргивания в секунду (экспоненциальный спад). */
    private static final float SMOOTH_RATE = 26.0F;

    private static float pendingPitch = 0.0F;
    private static float pendingYaw = 0.0F;
    private static long lastFrameNanos = 0L;

    private MachineGunRecoil() {
    }

    /**
     * Сбрасывает накопленный импульс отдачи (при смене оружия или выходе).
     */
    public static void reset() {
        pendingPitch = 0.0F;
        pendingYaw = 0.0F;
        lastFrameNanos = 0L;
    }

    /**
     * Добавляет случайное подёргивание от выстрела в буфер плавного применения.
     *
     * @param scale множитель ослабления: броня, прицел, стойка и накопленный
     *              огонь, см. {@link MachineGunItem#recoilScale}
     */
    public static void kick(float scale) {
        if (scale <= 0.0F) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        float vertical = MachineGunItem.RECOIL_PITCH_DEGREES * scale;
        float horizontal = MachineGunItem.RECOIL_YAW_DEGREES * scale;

        // Случайный знак по обеим осям (старая механика подёргивания),
        // плюс органическая вариация силы импульса (90% .. 110%).
        float pitchImpulse = sign(vertical);
        float yawImpulse = sign(horizontal);

        pendingPitch += pitchImpulse;
        pendingYaw += yawImpulse;

        // Защита от чрезмерного накопления при сильных лагах
        pendingPitch = Mth.clamp(pendingPitch, -15.0F, 15.0F);
        pendingYaw = Mth.clamp(pendingYaw, -15.0F, 15.0F);
    }

    private static float sign(float magnitude) {
        float variation = 0.9F + RANDOM.nextFloat() * 0.2F;
        return (RANDOM.nextBoolean() ? magnitude : -magnitude) * variation;
    }

    /**
     * Плавное попиксельное применение подёргиваний перед каждым отрисовываемым кадром.
     */
    @SubscribeEvent
    public static void onRenderFramePre(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) {
            lastFrameNanos = 0L;
            return;
        }

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return;
        }

        float dt = (now - lastFrameNanos) * 1.0E-9F;
        lastFrameNanos = now;

        // Ограничиваем dt во избежание скачков при лагах/прогрузке мира
        dt = Mth.clamp(dt, 0.0005F, 0.05F);

        if (Math.abs(pendingPitch) < 0.0005F && Math.abs(pendingYaw) < 0.0005F) {
            pendingPitch = 0.0F;
            pendingYaw = 0.0F;
            return;
        }

        float fraction = 1.0F - (float) Math.exp(-SMOOTH_RATE * dt);
        float stepPitch = pendingPitch * fraction;
        float stepYaw = pendingYaw * fraction;

        LocalPlayer player = mc.player;

        // Наклон клампится в [-90; 90], чтобы не перевернулась камера.
        // Yaw не заворачивается — камера интерполирует yRotO и yRot без скачков через 360.
        player.setXRot(Mth.clamp(player.getXRot() + stepPitch, -90.0F, 90.0F));
        player.setYRot(player.getYRot() + stepYaw);

        player.xRotO = Mth.clamp(player.xRotO + stepPitch, -90.0F, 90.0F);
        player.yRotO += stepYaw;

        pendingPitch -= stepPitch;
        pendingYaw -= stepYaw;
    }
}