package com.trd.item.weapons.guns;

import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.Random;

/**
 * Отдача пушки: короткое смещение камеры вбок и вверх после каждого выстрела.
 *
 * <p>Смещение <b>чисто клиентское</b> и никогда не отправляется на сервер:
 * поворот игрока в майнкрафте серверный, и если бы дёргать настоящий
 * {@code setYRot}, то уходило бы на полградуса всерьёз, а на стороне других
 * игроков камера всё равно осталась бы ровной. Поэтому смещение прибавляется к
 * углам камеры в {@link ViewportEvent.ComputeCameraAngles} и гаснет само.
 *
 * <p>Затухание идёт по smoothstep, а не умножением на коэффициент за тик.
 * Умножение начинается сразу с полного значения и потом просто деградирует —
 * в начале тика камера дёргается рывком, и на автоматическом огне это читается
 * как дрожь, а не как отдача. Smoothstep даёт нулевую скорость на обоих концах,
 * то есть камера мягко уходит и так же мягко возвращается.
 *
 * <p>Знак по обеим осям случаен: настоящая отдача автомата уводит ствол вверх
 * и вбок неравномерно, а ровное смещение в одну сторону читалось бы как
 * прилипание прицела к краю экрана.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public final class MachineGunRecoil {

    /** Сколько тиков живёт одно дёрганье. */
    private static final int DURATION_TICKS = 7;

    private static final Random RANDOM = new Random();

    /** Амплитуда по горизонтали на момент последнего выстрела, градусы. */
    private static float yaw;

    /** Амплитуда по вертикали на момент последнего выстрела, градусы. */
    private static float pitch;

    private static int age;

    private MachineGunRecoil() {
    }

    /**
     * Дёргает камеру на выстрел.
     *
     * @param scale множитель ослабления: броня, прицел и стойка глушат отдачу,
     *              см. {@link MachineGunItem#recoilScale}
     */
    public static void kick(float scale) {
        if (scale <= 0.0F) {
            return;
        }

        float vertical = MachineGunItem.RECOIL_PITCH_DEGREES * scale;
        float horizontal = MachineGunItem.RECOIL_YAW_DEGREES * scale;

        // Складывается, а не заменяется: очередь выстрелов должна давать более
        // плотную отдачу, чем одиночная, и не обнулять предыдущую. Потолок —
        // две амплитуды: на автоматическом огне дёрганье и так сливается в
        // сплошное дрожание, а дальше оно только отнимает читаемость прицела.
        pitch = Mth.clamp(pitch + sign(vertical), -vertical * 2.0F, vertical * 2.0F);
        yaw = Mth.clamp(yaw + sign(horizontal), -horizontal * 2.0F, horizontal * 2.0F);
        age = 0;
    }

    private static float sign(float magnitude) {
        return RANDOM.nextBoolean() ? magnitude : -magnitude;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (yaw == 0.0F && pitch == 0.0F) {
            return;
        }

        if (++age >= DURATION_TICKS) {
            yaw = 0.0F;
            pitch = 0.0F;
            return;
        }
    }

    /**
     * Доля амплитуды на текущем тике: 1 в начале, 0 в конце, и ноль по скорости
     * на обоих краях.
     */
    private static float envelope() {
        float t = 1.0F - (float) age / DURATION_TICKS;
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Прибавляет отдачу к углам камеры.
     * <p>
     * В третьем лице отдача не показывается: смещение там уехало бы вместе с
     * камерой от плеча игрока и выглядело бы как поворот всего мира.
     */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (yaw == 0.0F && pitch == 0.0F) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        float amount = envelope();
        event.setYaw((float) (event.getYaw() + yaw * amount));
        event.setPitch(Mth.clamp(event.getPitch() + pitch * amount, -90.0F, 90.0F));
    }
}
