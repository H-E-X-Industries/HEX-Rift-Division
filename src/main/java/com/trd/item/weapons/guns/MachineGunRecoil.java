package com.trd.item.weapons.guns;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * Отдача пушки: разворот взгляда стрелка вверх и вбок на каждый выстрел.
 *
 * <p>Отдача бьёт по <b>настоящему повороту игрока</b> ({@code setXRot} /
 * {@code setYRot}), а не по углам камеры в {@code ViewportEvent}, как было
 * раньше. Разница принципиальная: углы камеры серверу неизвестны, пуля летит
 * по настоящему повороту, поэтому смещение камеры всегда расходилось с
 * прицелом — игрок целился в одно место, а видел мушку в другом. Такое смещение
 * можно было сколько угодно усиливать и обязательно приходилось гасить: иначе
 * дёрганое изображение перестаёт читаться. Старое затухание за семь тиков и
 * было этим гашением — на автоматическом огне камера дрожала, а попасть можно
 * было туда же, куда и до начала очереди.
 *
 * <p>Теперь увод остаётся в повороте, то есть одинаково в прицеле и в самой
 * пуле, и <b>не возвращается</b>: ствол уехал — уехал и прицел. Чтобы вести
 * очередь, придётся всё время тянуть мышь вниз и против угла вбок, и чем
 * очередь длиннее, тем сильнее увод (см. накопление огня в
 * {@link MachineGunItem#recoilScale}). Короткая очередь стоит почти ничего,
 * длинная без коррекции уходит в стену — ровно то, ради чего пушка и
 * управляется руками, а не прицелом.
 *
 * <p>Поворот уезжает на сервер сам, из обычного пакета движения: клиент шлёт
 * {@code Rot}, как только угол отличается от последнего отправленного, поэтому
 * другие игроки видят, куда уводит стрелка. Вместе с текущим углом двигается и
 * предыдущий кадр ({@code xRotO} / {@code yRotO}) — иначе в третьем лице
 * модель игрока растянулась бы на все накопленные градусы, потому что между
 * кадрами она интерполируется между этими двумя величинами.
 *
 * <p>Знак по обеим осям случаен: настоящая отдача автомата уводит ствол вверх
 * и вбок неравномерно, а ровное смещение в одну сторону читалось бы как
 * прилипание прицела к краю экрана.
 */
public final class MachineGunRecoil {

    private static final Random RANDOM = new Random();

    private MachineGunRecoil() {
    }

    /**
     * Уводит взгляд стрелка на выстрел.
     * <p>
     * Выстрел при этом уходит из пакета по <i>прошлому</i> углу: см. порядок
     * вызовов в {@link MachineGunItem.ClientHandlers}. Иначе первый же выстрел
     * летел бы уже сдвинутым прицелом, то есть отдача съедала бы всю точность
     * первого выстрела.
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

        float pitchDelta = sign(vertical);
        float yawDelta = sign(horizontal);

        // Наклон клампится вручную: Entity#setXRot в 1.21.1 ничего не
        // ограничивает, а за 90° взгляд переваливается через пол и картинка
        // переворачивается. Yaw только заворачивается в круг, иначе за долгую
        // очередь он уходит на тысячи градусов.
        player.setXRot(Mth.clamp(player.getXRot() + pitchDelta, -90.0F, 90.0F));
        player.setYRot(Mth.wrapDegrees(player.getYRot() + yawDelta));

        // Предыдущий кадр поворота едет на те же дельты — ровно как это делает
        // Entity#turn, из которого взят и клампинг, и правка xRotO/yRotO.
        player.xRotO = Mth.clamp(player.xRotO + pitchDelta, -90.0F, 90.0F);
        player.yRotO = Mth.wrapDegrees(player.yRotO + yawDelta);
    }

    private static float sign(float magnitude) {
        return RANDOM.nextBoolean() ? magnitude : -magnitude;
    }
}