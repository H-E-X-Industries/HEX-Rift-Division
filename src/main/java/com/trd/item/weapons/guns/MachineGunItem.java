package com.trd.item.weapons.guns;

import com.trd.client.config.ModKeyBindings;
import com.trd.client.gecko.item.guns.MachineGunRenderer;
import com.trd.client.overlay.MachineGunScope;
import com.trd.entity.weapons.bullets.GilseEntity;
import com.trd.entity.weapons.bullets.TurretBulletEntity;
import com.trd.item.weapons.ammo.AmmoRegistry;
import com.trd.main.MainRegistry;
import com.trd.network.packet.guns.PacketMachineGunAnim;
import com.trd.network.packet.guns.PacketReloadGun;
import com.trd.network.packet.guns.PacketShoot;
import com.trd.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

/**
 * Автоматическая 20-мм пушка. Питается патронами калибра {@code 20mm_turret},
 * которые регистрируются в {@link AmmoRegistry}. Стрельба и перезарядка идут
 * через пакеты: клиент только рисует и отправляет намерение, всё решение
 * принимает сервер.
 * <p>
 * Модель и анимации живут в glTF ({@code models/item/ap_17.gltf}) и рисуются
 * через GemRender, поэтому класс больше не {@code GeoItem}. Клиентское состояние
 * анимаций держит {@link MachineGunClientAnim}, а сервер сообщает о разовых
 * клипах пакетом {@link PacketMachineGunAnim}.
 */
public class MachineGunItem extends Item {

    /**
     * Минимальный перерыв между выстрелами в тиках.
     * <p>
     * Полтик — это 0.05 с, то есть предел, ниже которого стрельба превращается
     * в лаги: каждый выстрел это ещё и сущность пули, сущность гильзы, звук на
     * всехNearby и пакет анимации. Значение только удерживает нижнюю границу,
     * а не задаёт темп.
     */
    private static final int MIN_SHOT_INTERVAL_TICKS = 1;

    /**
     * Длина клипа выстрела в тиках: glTF-клип {@code shot} идёт 0.3333 с, то есть
     * 6.67 тика.
     * <p>
     * <b>Почему 6, а не 7.</b> Клип отыгрывается начиная с первого тика, поэтому
     * на интервале в 7 тиков последние 0.033 с анимации не успевали показаться:
     * ствол на последнем кадре уже почти вернулся, а потом резко прыгал в
     * покой. Плюс между клипами оставался целый тик позы покоя, и при автоматичеcком
     * огне это читалось как то, что пушку колотит назад. Шесть тиков — это ровно
     * столько, сколько клип реально успевает отыграть, и следующий выстрел
     * приходит, пока он ещё идёт.
     * <p>
     * Длина проверяется по самой модели: {@link MachineGunClientAnim} тянет её из
     * glTF, и если clip перезальют с другой длиной, здесь понадобится правка.
     */
    private static final int SHOT_ANIM_TICKS = 6;

    /** Длина клипа выстрела в секундах — ею подгоняются времена звуков. */
    private static final float SHOT_CLIP_SECONDS = SHOT_ANIM_TICKS / 20.0F;

    private static final int MAG_CAPACITY = 24;
    private static final int MAX_TOTAL_AMMO = MAG_CAPACITY + 1;

    /**
     * Длины блокировки перезарядки и разрядки, в тиках.
     * <p>
     * Взяты из glTF, а не из старой пятисекундной модели: у клипа
     * {@code reload} последний ключ на 4.5833 с (92 тика), у {@code flip} — на
     * 4.1667 с (84 тика). Раньше здесь стояли 63 и 79 от прежних клипов, из-за
     * чего оружие переставало быть занятым за полторы секунды до конца жеста, а
     * патроны досыпались в середине перезарядки — и звуки, привязанные к
     * анимации, уезжали мимо неё.
     */
    private static final int RELOAD_ANIM_TICKS = 92;
    private static final int FLIP_ANIM_TICKS = 84;

    /** На каком тике перезарядки патроны доезжают в магазин. */
    private static final int RELOAD_AMMO_ADD_TICK = 60;
    private static final String LOADED_AMMO_ID_TAG = "LoadedAmmoID";
    private static final String HANDS_WARN_TAG = "HandsWarn";

    /** Очередь звуков клипа: список compound'ов с отсчётом и путём звука. */
    private static final String SCHEDULED_SOUNDS_TAG = "ScheduledSounds";

    private static final String GUN_CALIBER = "20mm_turret";

    /**
     * Базовый разброс выстрела, как гауссово отклонение по каждой оси единичного
     * вектора направления.
     * <p>
     * Раньше здесь стояло 0.0075 — примерно 0.43° на ось. При скорости пули
     * 12 блоков/тик это давало уход около 0.75 блока на сотне метров, то есть
     * пушка мазала заметно сильнее лука. Сейчас значение уменьшено в 15 раз:
     * на сотне метров разброс составляет около 0.05 блока, а в прицеле — вдвое
     * меньше. Разброс оставлен ненулевым специально: он гасит идеальную
     * параллельность ствола и камеры, но глазом уже не читается.
     * <p>
     * Важно: константа живёт здесь, а не в {@link MachineGunScope}. Разброс
     * считает сервер, а клиентский класс на выделенном сервере всё равно
     * отдавал бы 1.0 — прицел попросту не влиял бы на меткость.
     */
    private static final float SCATTER = 0.0005f;

    /** Во сколько раз в прицеле падает разброс, то есть растёт точность. */
    public static final float SCOPED_ACCURACY_BONUS = 2.0f;

    /**
     * Тиков тишины между сообщениями «нужны обе руки». Без этого игрок,
     * удерживающий огонь, получал бы сообщение на каждый пакет стрельбы.
     */
    private static final int HANDS_WARN_COOLDOWN = 30;

    /** Ускорение пули относительно скорости патрона из реестра. */
    private static final float SPEED_MULTIPLIER = 2.0f;

    /** Насколько ствол смещён вбок от вертикали через плечо стрелка. */
    private static final double GUN_SIDE_OFFSET = 0.2D;

    /** Насколько ствол опущен относительно уровня глаз. */
    private static final double MUZZLE_DROP = 0.1D;

    /**
     * Насколько пуля вылетает из камеры вперёд в прицеле.
     * <p>
     * Ровно из глаза она вылетать не может: {@code traceHit} проверяет блоки по
     * лучу, и старт внутри собственной головы означал бы мгновенное попадание
     * в любой блок, к которому иглот прижался.
     * <p>
     * Значение выросло с 0.3 до 0.8, а потом до 1.8: в прицеле камера стоит у
     * глаза, и пуля, появлявшаяся в полблоке от него, читалась как вылетающая
     * из головы. Ещё блок вперёд уводит её и от стен в упоре — стоять к
     * противнику вплотную и простреливать его насквозь было нельзя, луч
     * столкновений стартовал бы в его клетке.
     * <p>
     * Промахнуться мимо цели в упоре это не значит: луч стартует в точке
     * появления и тут же идёт на шесть блоков, поэтому моб в упоре и блок под
     * ногами всё равно попадают под трассировку на первом же тике полёта.
     */
private static final double SCOPED_FORWARD = 1.8D;

    /**
     * Насколько длинно дуло выходит из-за плеча стрелка.
     * <p>
     * Точка появления пули сдвинута вперёд по стволу, а не остаётся у плеча:
     * иначе пуля возникает прямо перед камерой, ещё не выйдя из оружия, и
     * первый кадр выстрела читается как вспышка в воздухе.
     */
    private static final double MUZZLE_LENGTH = 0.45D;

    /**
     * Доворот точки вылета от третьего лица: столько вперёд, столько влево и
     * столько вправо от оси ствола.
     * <p>
     * Считается от ствола, а не от игрока. Правый сдвиг нужен потому, что ствол
     * и так вынесен вправо на {@link #GUN_SIDE_OFFSET}: пуля оттуда вылетала
     * заметно сбоку от дула, и на третьем лице визг уходил мимо оружия.
     * <p>
     * Дальше точка вылета доводится ещё и на {@link #MUZZLE_SIDE_SHIFT} влево.
     */
    private static final double THIRD_PERSON_FORWARD = 0.25D;
    private static final double THIRD_PERSON_LEFT    = 0.125D;
    private static final double THIRD_PERSON_RIGHT   = 0.3D;

    /**
     * Доворот точки вылета на четыре с половиной пикселя влево от всего, что
     * нарисовано.
     * <p>
     * Полпикселя — это {@code 0.5/16} блока; остальные смещения считаются в
     * той же мере. Сдвиг общий для первого и третьего лица и применяется один
     * раз, уже к выбранной точке вылета: и к клиентскому кончику ствола из
     * модели, и к серверной формуле. Иначе формула и модель разъезжались бы на
     * эти пиксели между собой, а пуля всё равно появлялась бы с одного места.
     * <p>
     * Направление берётся от взгляда, а не от руки: в прицеле и в третьем лице
     * это одно и то же «влево от стрелка».
     * <p>
     * В прицеле сдвиг не применяется. Точка вылета там берётся из глаза и
     * обязана лежать на оптической оси — иначе визг уходил бы вбок от
     * перекрестья, а на близкой дистанции расхождение вообще переставало бы
     * попадать туда, куда смотрит игрок.
     */
    private static final double MUZZLE_SIDE_SHIFT = 4.5D / 16.0D;

    /**
 * Отдача: насколько игрока отбрасывает назад за один выстрел, в блоках на тик.
 * <p>
 * Импульс кладётся прямо в скорость игрока, а не в «виртуальную» величину:
 * {@code LivingEntity#travel} берёт текущую скорость, домножает её на трение и
 * добавляет движение от игрока, поэтому толчок живёт по-настоящему — игрок
 * сдвигается, а на бегу его заметно сносит назад.
 */
    private static final double RECOIL_PUSH = 0.2D;

    /**
     * Разброс отдачи камеры на один выстрел, градусы.
     * <p>
     * Сначала было полтора и один: на автоматическом огне камера дрожала так,
     * что прицел был не удержать. Сейчас это верхняя граница без брони и без
     * прицела; всё остальное множители.
     */
    static final float RECOIL_YAW_DEGREES = 0.3F;
    static final float RECOIL_PITCH_DEGREES = 0.45F;

    /** Прицел: отдача вдвое слабее. */
    private static final float SCOPED_RECOIL_SCALE = 0.5F;

    /** Присед или лёжа: отдача втрое слабее. */
    private static final float STABLE_RECOIL_SCALE = 1.0F / 3.0F;

    /** Полный комплект брони — 20 очков, а насколько он гасит отдачу. */
    private static final float MAX_ARMOR_RECOIL_DAMPING = 0.5F;

    /** На сколько толчок растёт при полностью накопленной отдаче. */
    private static final float HEAT_PUSH_MULTIPLIER = 1.5F;

    /**
     * Сколько очков брони съедают накопленную отдачу.
     * <p>
     * Броня съедает тем больше, чем дольше стреляешь: короткая очередь на
     * брони почти не гасится, а длинная — уже заметно.
     */
    private static final float HEAT_ARMOR_DAMPING = 0.6F;

    /**
     * Накопленная отдача, 0..1.
     * <p>
     * Растёт тем быстрее, чем короче пауза между выстрелами: ровно на два
     * выстрела в секунду она не растёт вовсе, а на автоматическом огне добирается
     * до единицы примерно за две секунды. Падает сама, стоит только перестать
     * стрелять.
     */
    private static final String RECOIL_HEAT_TAG = "RecoilHeat";

    /** За сколько секунд непрерывной стрельбы отдача добирается до максимума. */
    private static final float HEAT_RAMP_SECONDS = 2.0F;

    /**
     * Смещение окна выброса гильзы вбок и вверх относительно ствола.
     * <p>
     * Гильза появляется здесь, а не у дула: она должна быть видна целиком до
     * того, как вылетит. Значения близки к реальным — окно у автоматического
     * ствола расположено сразу позади и выше оси ствола.
     */
    private static final double PORT_SIDE_OFFSET = 0.16D;
    private static final double PORT_UP_OFFSET = 0.02D;

    /**
     * Смещение окна выброса гильзы вдоль ствола.
     * <p>
     * Положительное значение выносит гильзу вперёд, к дулу. Раньше она
     * появлялась заметно позади, у самого казённика, и на автоматическом огне
     * падающие гильзы вылетали из-под руки, а не из оружия.
     */
    private static final double PORT_FORWARD = 0.4D;

    /**
     * Насколько окно выброса выносится из хитбокса стрелка при вертикальном
     * выстреле. Запас нужен, чтобы гильза не тонула в игроке.
     */
    private static final double SHOOTER_CLEARANCE = 0.1D;

    /**
     * Единичный вектор вправо относительно направления взгляда.
     * <p>
     * Выстрел строго вверх или строго вниз даёт нулевое векторное произведение
     * с вертикалью, и {@code normalize()} на нуле молча возвращает нулевой
     * вектор — то есть смещение вбок просто исчезало бы. Тогда берётся
     * другая ось, но и она к yaw стрелка не привязана: строго вертикальный
     * вектор вообще не несёт информации о повороте, и все стрелки, смотрящие
     * вверх, получали бы одну и ту же мировую «правую» сторону. Поэтому
     * вырожденный случай считается по {@code yaw} — там боковое смещение
     * остаётся настоящим «вправо от стрелка».
     */
    private static Vec3 perpendicular(Vec3 direction, float yaw) {
        Vec3 side = direction.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-6D) {
            double yawRad = yaw * (Math.PI / 180.0D);
            side = new Vec3(-Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        }
        return side.normalize();
    }

    /**
     * Выносит точку выброса гильзы из хитбокса стрелка.
     * <p>
     * Только для гильзы. У пули своя точка вылета есть — кончик ствола из
     * модели, — и сдвигать её вдоль взгляда нельзя: смотрит игрок вверх, точка
     * уезжала вверх от дула, смотрит вниз — вниз, то есть «гуляла» вместе с
     * наклоном. Гильза же считается только по этой формуле, и при вертикальном
     * выстреле её окно оказывалось внутри игрока.
     * <p>
     * Дальше считается обычным слэб-методом: для каждой оси берётся ближайшая
     * из двух границ и из всех трёх — наибольшая. Оси, параллельные лучу,
     * пропускаются: по ним луч не пересекает границу и ждать выхода неоткуда.
     */
    private static Vec3 clearOfShooter(Vec3 point, Vec3 forward, Player player) {
        AABB box = player.getBoundingBox().inflate(SHOOTER_CLEARANCE);

        // Точка и так снаружи — двигать нечего. Проверка обязательна: slab-метод
        // для луча, идущего от хитбокса наружу в сторону, противоположную его
        // грани, выдаёт положительный выход, и пустой сдвиг увел бы точку зря.
        if (!box.contains(point)) {
            return point;
        }

        double[] origin = {point.x, point.y, point.z};
        double[] direction = {forward.x, forward.y, forward.z};
        double[] min = {box.minX, box.minY, box.minZ};
        double[] max = {box.maxX, box.maxY, box.maxZ};

        double exit = 0.0D;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(direction[axis]) < 1.0E-9D) {
                continue;
            }
            double near = (min[axis] - origin[axis]) / direction[axis];
            double far = (max[axis] - origin[axis]) / direction[axis];
            exit = Math.max(exit, Math.min(near, far));
        }
        return point.add(forward.scale(exit));
    }

    /**
     * Насколько слабее отдача от брони, прицела, стойки и накопленного огня.
     * <p>
     * Броня — единственный множитель, который смотрит на снаряжение: в полном
     * комплекте отдача вдвое слабее, в половине — на четверть, и при этом тем
     * сильнее гасит накопленный огонь. Огонь копится от частоты стрельбы и
     * спадает, стоит только перестать стрелять. Прицел и стойка уменьшают
     * отдачу втрое и не смотрят ни на что.
     * <p>
     * Один и тот же метод зовут и сервер (для толчка игрока), и клиент (для
     * камеры), поэтому при разных значениях на двух сторонах одно дёргалось бы
     * не в тон другому.
     *
     * @param scoped смотрит ли игрок в прицел
     * @param heat   накопленная отдача, 0..1
     */
    public static float recoilScale(Player player, boolean scoped, float heat) {
        float armor = player.getArmorValue() / 20.0F;
        float scale = (1.0F - armor * MAX_ARMOR_RECOIL_DAMPING)
                * (1.0F + heat * HEAT_PUSH_MULTIPLIER * (1.0F - armor * HEAT_ARMOR_DAMPING));

        if (scoped) {
            scale *= SCOPED_RECOIL_SCALE;
        }
        if (player.isCrouching()
                || player.getPose() == net.minecraft.world.entity.Pose.SLEEPING
                || player.getPose() == net.minecraft.world.entity.Pose.FALL_FLYING) {
            scale *= STABLE_RECOIL_SCALE;
        }
        return Mth.clamp(scale, 0.0F, 3.0F);
    }

    /**
     * Отдача без огня: то же самое, но без накопления. Удобно там, где выстрела
     * не было и считать нечего.
     */
    public static float recoilScale(Player player, boolean scoped) {
        return recoilScale(player, scoped, 0.0F);
    }

    /**
     * Накопленная отдача, 0..1, из NBT оружия.
     * <p>
     * Хранится в самом оружии, а не в статике: у игрока их может быть
     * сколько угодно, и у каждого своя. Через {@code DataComponents.CUSTOM_DATA}
     * значение уезжает и на клиент, поэтому дёрганье камеры считается по той же
     * величине, что и толчок на сервере.
     */
    private static float recoilHeat(ItemStack stack) {
        return readTag(stack).getFloat(RECOIL_HEAT_TAG);
    }

    /**
     * Копит отдачу по темпу выстрелов и возвращает новое значение.
     * <p>
     * Темп считается по интервалу между выстрелами, а не по числу выстрелов в
     * секунду: обратный отсчёт тиков между ними уже есть в самом оружии, и он
     * переживает любые пропуски. Пауза короче, чем {@link #SHOT_ANIM_TICKS},
     * добавляет отдачу; пауза длиннее — отнимает.
     */
    private static float addRecoilHeat(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        int ticks = tag.getInt(RECOIL_HEAT_TAG + "Ticks") + 1;

        float step = SHOT_ANIM_TICKS <= ticks
                ? 1.0F / HEAT_RAMP_SECONDS * (1.0F - (float) SHOT_ANIM_TICKS / ticks)
                : -(float) (ticks - SHOT_ANIM_TICKS) / (HEAT_RAMP_SECONDS * 20.0F);

        float heat = Mth.clamp(tag.getFloat(RECOIL_HEAT_TAG) + step, 0.0F, 1.0F);

        tag.putInt(RECOIL_HEAT_TAG + "Ticks", ticks);
        tag.putFloat(RECOIL_HEAT_TAG, heat);
        writeTag(stack, tag);
        return heat;
    }

    /**
     * Остывание. Копится оно само по темпу выстрелов, а тут только сброс: без
     * новых выстрелов последний записанный интервал так и остался бы коротким,
     * и отдача держалась бы вечно.
     */
    private static void coolRecoilHeat(ItemStack stack) {
        CompoundTag tag = readTag(stack);
        int ticks = tag.getInt(RECOIL_HEAT_TAG + "Ticks");
        float heat = tag.getFloat(RECOIL_HEAT_TAG);

        if (ticks <= SHOT_ANIM_TICKS || heat <= 0.0F) {
            if (ticks == 0 && heat <= 0.0F) {
                return;
            }
            tag.putInt(RECOIL_HEAT_TAG + "Ticks", 0);
            tag.putFloat(RECOIL_HEAT_TAG, 0.0F);
            writeTag(stack, tag);
            return;
        }

        heat = Mth.clamp(heat - (float) (ticks - SHOT_ANIM_TICKS) / (HEAT_RAMP_SECONDS * 20.0F), 0.0F, 1.0F);
        tag.putFloat(RECOIL_HEAT_TAG, heat);
        writeTag(stack, tag);
    }

    /**
     * Точка, из которой вылетает пуля.
     * <p>
     * Считается здесь и на сервере, и на клиенте, иначе вспышка в дуле и сама
     * пуля разъезжались бы: клиентской кончик ствола из модели для этого не
     * годится — при первом взгляде он сбит от нарисованного дула, потому что
     * модель анимируется остатком предыдущего кадра, а смена оружия и переход
     * между первым и третьим лицом вовсе не анимируются.
     * <p>
     * В прицеле пуля идёт строго по оптической оси, из глаза: точка у дула там
     * сбита вбок и уводила бы визг с перекрестья.
     *
     * @param lookDir направление взгляда стрелка
     */
    public static Vec3 shotOrigin(Player player, Vec3 lookDir, float yaw, boolean scoped) {
        Vec3 side = perpendicular(lookDir, yaw);
        Vec3 forward = lookDir.normalize();

        if (scoped) {
            return player.getEyePosition().add(forward.scale(SCOPED_FORWARD));
        }

        Vec3 gunPos = player.position()
                .add(side.scale(GUN_SIDE_OFFSET))
                .add(0.0D, player.getEyeY() - player.getY() - MUZZLE_DROP, 0.0D);

        return gunPos
                .add(forward.scale(MUZZLE_LENGTH + THIRD_PERSON_FORWARD))
                .add(side.scale(THIRD_PERSON_RIGHT - THIRD_PERSON_LEFT - MUZZLE_SIDE_SHIFT));
    }

    public MachineGunItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /**
     * Просит клиент проиграть клип: {@code shot}, {@code reload} или {@code flip}.
     * <p>
     * Клиент держит строго один клип за раз, поэтому повторные выстрелы во время
     * уже идущей анимации просто игнорируются на его стороне.
     */
    private static void sendAnim(Player player, String anim) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new PacketMachineGunAnim(anim));
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (oldStack.getItem() == newStack.getItem() && !slotChanged) return false;
        return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }

    // === NBT МЕТОДЫ ===

    private static CompoundTag readTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void writeTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public int getAmmo(ItemStack stack) {
        return readTag(stack).getInt("Ammo");
    }

    public void setAmmo(ItemStack stack, int ammo) {
        CompoundTag tag = readTag(stack);
        tag.putInt("Ammo", Math.max(0, Math.min(ammo, MAX_TOTAL_AMMO)));
        writeTag(stack, tag);
    }

    public int getShootDelay(ItemStack stack) {
        return readTag(stack).getInt("ShootDelay");
    }

    public void setShootDelay(ItemStack stack, int delay) {
        CompoundTag tag = readTag(stack);
        tag.putInt("ShootDelay", delay);
        writeTag(stack, tag);
    }

    public int getReloadTimer(ItemStack stack) {
        return readTag(stack).getInt("ReloadTimer");
    }

    public void setReloadTimer(ItemStack stack, int timer) {
        CompoundTag tag = readTag(stack);
        tag.putInt("ReloadTimer", timer);
        writeTag(stack, tag);
    }

    public int getPendingAmmo(ItemStack stack) {
        return readTag(stack).getInt("PendingAmmo");
    }

    public void setPendingAmmo(ItemStack stack, int ammo) {
        CompoundTag tag = readTag(stack);
        tag.putInt("PendingAmmo", ammo);
        writeTag(stack, tag);
    }

    public String getLoadedAmmoID(ItemStack stack) {
        return readTag(stack).getString(LOADED_AMMO_ID_TAG);
    }

    public void setLoadedAmmoID(ItemStack stack, String ammoID) {
        CompoundTag tag = readTag(stack);
        tag.putString(LOADED_AMMO_ID_TAG, ammoID);
        writeTag(stack, tag);
    }

    public int getHandsWarnTimer(ItemStack stack) {
        return readTag(stack).getInt(HANDS_WARN_TAG);
    }

    public void setHandsWarnTimer(ItemStack stack, int timer) {
        CompoundTag tag = readTag(stack);
        tag.putInt(HANDS_WARN_TAG, Math.max(0, timer));
        writeTag(stack, tag);
    }

    /**
     * Пушка — оружие под обе руки, поэтому стрелять можно, только когда вторая
     * рука свободна. Тот же принцип, что у литой кирки (см.
     * {@code CastPickaxeItem#canUse}), но проверка живёт на сервере: клиент про
     * пакет ничего не знает.
     */
    public static boolean hasBothHandsFree(Player player) {
        return player.getOffhandItem().isEmpty();
    }

    /**
     * Пока стрелок в воде, оружие не работает: мокрый затвор и отказной стопор.
     * Проверяются и тело, и глаза — в брызгах прицел уже не видно, а вода на
     * уровне глаз означает полное погружение.
     */
    public static boolean isSubmerged(Player player) {
        return player.isInWater() || player.isEyeInFluid(FluidTags.WATER);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        if (!level.isClientSide && entity instanceof Player player) {

            // Отдача остывает, пока не стреляют. Без этого она держалась бы
            // вечно: последний записанный интервал между выстрелами так и
            // остался бы коротким.
            if (getShootDelay(stack) <= 0) {
                coolRecoilHeat(stack);
            }

            if (!isSelected) {
                if (getReloadTimer(stack) > 0) {
                    setReloadTimer(stack, 0);
                    setPendingAmmo(stack, 0);
                    player.getInventory().setChanged();
                }
                return;
            }

            int warn = getHandsWarnTimer(stack);
            if (warn > 0) setHandsWarnTimer(stack, warn - 1);

            int delay = getShootDelay(stack);
            if (delay > 0) setShootDelay(stack, delay - 1);

            tickScheduledSounds(level, player, stack);

            int reloadTimer = getReloadTimer(stack);
            if (reloadTimer > 0) {
                setReloadTimer(stack, reloadTimer - 1);

                // Изъятие патронов из инвентаря на 50-м тике (2.5 сек)
                if (reloadTimer == (RELOAD_ANIM_TICKS - 50) || reloadTimer == (FLIP_ANIM_TICKS - 50)) {
                    int pending = getPendingAmmo(stack);
                    if (pending > 0 && !player.isCreative()) {
                        String loadedId = getLoadedAmmoID(stack);
                        if (loadedId != null && !loadedId.isEmpty()) {
                            consumeAmmoById(player, loadedId, pending);
                            player.getInventory().setChanged();
                        }
                    }
                }

                // Добавление патронов в оружие на 10-м тике (конец анимации)
                if (reloadTimer == (RELOAD_ANIM_TICKS - RELOAD_AMMO_ADD_TICK) ||
                        reloadTimer == (FLIP_ANIM_TICKS - RELOAD_AMMO_ADD_TICK)) {
                    int pending = getPendingAmmo(stack);
                    if (pending > 0) {
                        setAmmo(stack, getAmmo(stack) + pending);
                        setPendingAmmo(stack, 0);
                        syncHand(player, stack);
                    }
                }
            }
        }
    }

    /** Строковый id предмета, как его записывает {@link AmmoRegistry}. */
    private static String itemIdOf(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Подсчитывает количество патронов конкретного id в инвентаре (не изымая). */
    private int countAmmoById(Player player, String ammoId) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) continue;
            if (!AmmoRegistry.isValidAmmo(slot)) continue;
            if (!ammoId.equals(itemIdOf(slot))) continue;
            count += slot.getCount();
        }
        return count;
    }

    private void syncHand(Player player, ItemStack stack) {
        if (player instanceof ServerPlayer serverPlayer) {
            int slot = serverPlayer.getInventory().selected;
            serverPlayer.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2, 0, slot, stack));
        }
    }

    // === ПЕРЕЗАРЯДКА ===

    /**
     * Запускает клип перезарядки: блокирует оружие, ставит звуки клипа в очередь
     * и отправляет анимацию.
     * <p>
     * Все три дела обязаны идти отсюда. Блокировка и анимация — по длине клипа,
     * а звуки — по его же помеченным кадрам; если разъехались, оружие
     * освободится раньше конца жеста и патроны досыпаются в его середине.
     */
    private void startClip(Player player, ItemStack stack, String clip) {
        setReloadTimer(stack, MachineGunClientAnim.RELOAD.equals(clip)
                ? RELOAD_ANIM_TICKS
                : FLIP_ANIM_TICKS);

        scheduleClipSounds(player.level(), player, stack, clip, clipSeconds(clip));
        sendAnim(player, clip);
    }

    /**
     * Длина клипа в секундах по тем же числам, что и блокировка.
     * <p>
     * Нужна для подгонки времён звуков: {@link MachineGunAnimation} приводит их
     * к той длине, которую сюда передали, а длиной на сервере остаётся ровно то,
     * чему равна блокировка в тиках. Так маркер не уедет за конец анимации.
     */
    private static float clipSeconds(String clip) {
        int ticks = MachineGunClientAnim.RELOAD.equals(clip) ? RELOAD_ANIM_TICKS : FLIP_ANIM_TICKS;
        return ticks / 20.0F;
    }

    public void reloadGun(Player player, ItemStack stack) {
        if (player.level().isClientSide) return;
        if (getReloadTimer(stack) > 0) return;

        int currentAmmo = getAmmo(stack);

        // 1) Полный магазин -> FLIP (разрядить/проверить)
        if (currentAmmo >= MAX_TOTAL_AMMO) {
            startClip(player, stack, MachineGunClientAnim.FLIP);
            return;
        }

        String currentLoadedID = getLoadedAmmoID(stack);

        // Если в магазине что-то есть -> ищем строго такой же id.
        // Если магазин пуст -> ищем любой подходящий калибра.
        String targetAmmoId = findAmmoIdForReload(player,
                (currentAmmo > 0 && currentLoadedID != null && !currentLoadedID.isEmpty()) ? currentLoadedID : null);

        // 2) Подходящих патронов нет -> FLIP (даже в креативе)
        if (targetAmmoId == null) {
            startClip(player, stack, MachineGunClientAnim.FLIP);
            return;
        }

        // 3) Патроны есть, начинаем перезарядку
        if (player.isCreative()) {
            int toAdd = MAX_TOTAL_AMMO - currentAmmo;
            setPendingAmmo(stack, toAdd);

            if (currentAmmo == 0) {
                setLoadedAmmoID(stack, targetAmmoId);
            }

            startClip(player, stack, MachineGunClientAnim.RELOAD);
            return;
        }

        int needed = MAX_TOTAL_AMMO - currentAmmo;
        int available = countAmmoById(player, targetAmmoId);
        int taken = Math.min(needed, available);

        if (taken > 0) {
            if (currentAmmo == 0) {
                setLoadedAmmoID(stack, targetAmmoId);
            }
            setPendingAmmo(stack, taken);
            player.getInventory().setChanged();
            startClip(player, stack, MachineGunClientAnim.RELOAD);
        } else {
            startClip(player, stack, MachineGunClientAnim.FLIP);
        }
    }

    // === ЛОГИКА РАЗРЯДКИ ===

    public void unloadGun(ServerPlayer player, ItemStack stack) {
        int currentAmmo = getAmmo(stack);
        if (currentAmmo <= 0) return;
        if (getReloadTimer(stack) > 0) return;

        String loadedID = getLoadedAmmoID(stack);
        if (loadedID == null || loadedID.isEmpty()) return;

        AmmoRegistry.AmmoType ammoInfo = TurretBulletEntity.lookupAmmoType(loadedID);
        if (ammoInfo == null) return;

        int amountToReturn = player.isCreative() ? 1 : currentAmmo;

        ItemStack returnedStack = new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(loadedID)),
                amountToReturn);

        if (!player.getInventory().add(returnedStack)) {
            player.drop(returnedStack, false);
        }

        setAmmo(stack, 0);
        setLoadedAmmoID(stack, "");

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    /** Ищет первый подходящий id патрона в инвентаре. Если requiredId != null, ищет строго его. */
    private String findAmmoIdForReload(Player player, String requiredId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) continue;
            if (!AmmoRegistry.isValidAmmo(slot)) continue;
            if (!GUN_CALIBER.equals(AmmoRegistry.getCaliber(slot))) continue;

            String id = itemIdOf(slot);

            if (requiredId != null && !requiredId.equals(id)) continue;

            return id;
        }
        return null;
    }

    /** Забирает патроны конкретного id из инвентаря. */
    private int consumeAmmoById(Player player, String ammoId, int needed) {
        int taken = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (taken >= needed) break;

            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) continue;
            if (!AmmoRegistry.isValidAmmo(slot)) continue;
            if (!ammoId.equals(itemIdOf(slot))) continue;

            int toTake = Math.min(slot.getCount(), needed - taken);
            slot.shrink(toTake);
            taken += toTake;
            if (slot.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        return taken;
    }

    // === ЗВУКИ, ПРИВЯЗАННЫЕ К АНИМАЦИИ ===

    /**
     * Ставит звуки клипа в очередь на серверные тики.
     * <p>
     * Сервер — единственный, кто может проиграть звук так, чтобы его услышали
     * все вокруг: {@code level.playSound} рассылает его nearby-игрокам. Но момент
     * выстрела на сервере и на клиенте один и тот же (пакет анимации уходит в
     * тот же тик), поэтому очередь на серверных тиках попадает ровно на тот же
     * кадр анимации, что и у стрелка.
     * <p>
     * Очередь хранится в NBT самого оружия, а не в статике: переживает смену
     * слота, смену предмета в руке и не течёт после выхода игрока.
     *
     * @param clip          имя клипа, как в glTF
     * @param clipDuration  длина клипа в секундах
     */
    private void scheduleClipSounds(Level level, Player player, ItemStack stack, String clip,
                                    float clipDuration) {
        List<MachineGunAnimation.Marker> markers = MachineGunAnimation.sounds(clip, clipDuration);
        if (markers.isEmpty()) {
            return;
        }

        CompoundTag tag = readTag(stack);
        ListTag queue = tag.getList(SCHEDULED_SOUNDS_TAG, Tag.TAG_COMPOUND);

        for (MachineGunAnimation.Marker marker : markers) {
            // В секундах маркера — округление вверх, чтобы звук не ушёл на кадр
            // раньше анимации: клип на клиенте идёт с точностью до кадра.
            int in = Math.max(1, (int) Math.ceil(marker.time() * 20.0D));
            CompoundTag entry = new CompoundTag();
            entry.putInt("In", in);
            entry.putString("Id", marker.id());
            queue.add(entry);
        }

        if (!queue.isEmpty()) {
            tag.put(SCHEDULED_SOUNDS_TAG, queue);
            writeTag(stack, tag);
        }
    }

    /**
     * Проигрывает всё, что дозрело, и сдвигает остальное на тик.
     * <p>
     * Список каждый раз пересобирается заново: он длиной в единицы и живёт
     * меньше пяти секунд, а возиться с отдельным обратным отсчётом ради этого
     * невыгодно.
     */
    private void tickScheduledSounds(Level level, Player player, ItemStack stack) {
        CompoundTag tag = readTag(stack);
        ListTag queue = tag.getList(SCHEDULED_SOUNDS_TAG, Tag.TAG_COMPOUND);
        if (queue.isEmpty()) {
            return;
        }

        ListTag pending = new ListTag();
        for (int i = 0; i < queue.size(); i++) {
            CompoundTag entry = queue.getCompound(i).copy();
            int in = entry.getInt("In") - 1;
            if (in <= 0) {
                SoundEvent sound = MachineGunAnimation.sound(entry.getString("Id"));
                if (sound != null) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), sound,
                            SoundSource.PLAYERS, 1.0F, soundPitch(level));
                }
                continue;
            }
            entry.putInt("In", in);
            pending.add(entry);
        }

        if (pending.isEmpty()) {
            tag.remove(SCHEDULED_SOUNDS_TAG);
        } else {
            tag.put(SCHEDULED_SOUNDS_TAG, pending);
        }
        writeTag(stack, tag);
    }

    private static float soundPitch(Level level) {
        return 0.9F + level.random.nextFloat() * 0.2F;
    }

    // === СЕТКА ===

    /** Выстрел по углу обзора самого сервера. */
    public void performShooting(Level level, Player player, ItemStack stack) {
        performShooting(level, player, stack, player.getYRot(), player.getXRot(), false);
    }

    /**
     * Выстрел в направлении, заданном двумя осями: {@code yaw} и {@code pitch}.
     * Клиент присылает их в {@link PacketShoot}, чтобы пуля уходила ровно туда,
     * куда показывал прицел в момент нажатия огня.
     */
    public void performShooting(Level level, Player player, ItemStack stack, float yaw, float pitch,
                                boolean scoped) {
        if (level.isClientSide) return;
        if (getReloadTimer(stack) > 0 || getShootDelay(stack) > 0) return;

        // Пушка работает только под обе руки. Проверка до всего остального:
        // иначе заблокированный стрелок ещё и патрон бы расходовал.
        if (!hasBothHandsFree(player)) {
            warnBlocked(player, stack, "item.trd.machinegun.warning.twohanded");
            return;
        }

        // В воде оружие отказного типа: ни выстрела, ни звука, ни расхода
        // патрона. Всплыл — снова стреляет.
        if (isSubmerged(player)) {
            warnBlocked(player, stack, "item.trd.machinegun.warning.in_water");
            return;
        }

        int ammo = getAmmo(stack);

        // Пустой выстрел (ammo == 0)
        if (ammo <= 0) {
            SoundEvent drySound = ModSounds.DRY_FIRE.isBound() ? ModSounds.DRY_FIRE.get() : SoundEvents.DISPENSER_FAIL;
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    drySound, SoundSource.PLAYERS, 1.0F, 1.0F);

            setShootDelay(stack, SHOT_ANIM_TICKS);

            // Анимации пустого выстрела в glTF нет: клиент её не запускает,
            // остаётся только щелчок затвора выше.
            return;
        }

        String loadedID = getLoadedAmmoID(stack);

        if (!player.isCreative()) {
            setAmmo(stack, ammo - 1);
        }

        syncHand(player, stack);
        setShootDelay(stack, SHOT_ANIM_TICKS);

        if (!(level instanceof ServerLevel serverLevel)) return;

        // Огонь отдачи копится по темпу выстрелов и уходит в толчок и в камеру.
        float heat = addRecoilHeat(stack);

        TurretBulletEntity bullet = new TurretBulletEntity(serverLevel, player);

        AmmoRegistry.AmmoType ammoInfo = loadedID != null && !loadedID.isEmpty()
                ? TurretBulletEntity.lookupAmmoType(loadedID)
                : null;

        if (ammoInfo == null) {
            ammoInfo = new AmmoRegistry.AmmoType("default", GUN_CALIBER, 6.0f, 3.0f, false);
        }

        bullet.setAmmoType(ammoInfo);

        // Та же математика, что у Entity#getLookAngle, но по двум осям из пакета.
        // Разброс в прицеле вдвое ниже: точность ×2. Флаг прицела едет с
        // клиента в этом же пакете — раньше он там был, но не использовался, и
        // разброс считался по клиентскому классу, который на сервере всегда
        // отдавал 1.0.
        float spread = scoped ? SCATTER / SCOPED_ACCURACY_BONUS : SCATTER;

        // Скорость пули удваивается относительно паспортной у патрона: из скорости
        // 6.0 блока/тик (120 м/с) она становится 240 м/с. Множитель вынесен в
        // константу, потому что AIR_RESISTANCE в пуле тоже подобрана под прежнюю
        // скорость — при вдвое более быстрой пуле она почти не влияет.
        Vec3 lookDir = player.calculateViewVector(pitch, yaw);
        Vec3 velocity = lookDir.normalize().add(
                level.random.nextGaussian() * spread,
                level.random.nextGaussian() * spread,
                level.random.nextGaussian() * spread
        ).scale(ammoInfo.speed * SPEED_MULTIPLIER);

        // Точка вылета. В прицеле пуля обязана идти по оптической оси: только
        // там перекрестье совпадает с направлением взгляда. Раньше точка вылета
        // всегда бралась от ствола — на 0.2 блока вбок и на 0.1 вниз от глаз, —
        // и в прицеле пуля уходила заметно сбоку от мушки.
        //
        // Вперёд от камеры, а не из неё самой: луч столкновений стартует в точке
        // появления, и мгновенный старт внутри собственной головы означал бы
        // попадание в любой блок вплотную к игроку.
        //
        // Без прицела остаётся как было: от ствола вниз и в сторону, там
        // расхождение со стволом и нужно, но уже с конца дула и с доворотом
        // влево, чтобы визг шёл из оружия, а не мимо него.
        //
        // Сторона (вправо от стрелка) и направление считаются один раз и
        // используются дальше и для точки вылета пули, и для точки выброса
        // гильзы: брать их от разных векторов означало бы, что прицел и
        // казённик живут в разных системах координат.
        Vec3 side = perpendicular(lookDir, yaw);
        Vec3 forward = velocity.normalize();

        Vec3 gunPos = player.position()
                .add(side.scale(GUN_SIDE_OFFSET))
                .add(0.0D, player.getEyeY() - player.getY() - MUZZLE_DROP, 0.0D);

        // Точка вылета — ровно та же, что и у вспышки на клиенте, иначе визг и
        // пуля разъезжались бы на длину ствола.
        Vec3 spawnPos = shotOrigin(player, lookDir, yaw, scoped);

        bullet.setPos(spawnPos.x, spawnPos.y, spawnPos.z);

        // Скорость и ориентация — одним вызовом и обязательно до
        // addFreshEntity: пакет появления формируется при добавлении сущности в
        // уровень и несёт только поворот сущности, то есть выставить углы позже
        // уже некуда. Плюс сюда кладётся точный вектор вылета, и первый кадр на
        // клиенте совпадает с последующими.
        bullet.setLaunchDirection(velocity);

        serverLevel.addFreshEntity(bullet);

        // Отдача толкает стрелка назад по курсу выстрела. Именно по курсу, а не
        // по горизонтали: при выстреле вверх толчок идёт вверх, и стоя под
        // низким потолком это ещё и подбрасывает, что и читается как отдача.
        //
        // Множитель один и тот же, что и у камеры, но посчитанный здесь, на
        // сервере: свой клиентский у стрелка ещё не обновлён.
        player.setDeltaMovement(player.getDeltaMovement().add(
                forward.scale(-RECOIL_PUSH * recoilScale(player, scoped, heat))));

        // Звука и вспышки здесь больше нет: они приходят с помеченных кадров
        // анимации — звук ставит в очередь сервер, вспышку по локатору спавнит
        // клиент. Так они совпадают с отдачей ствола, а не с моментом нажатия.

        // Гильза вылетает из казённика вбок и вверх от направления выстрела.
        Vec3 up = new Vec3(0, 1, 0);

        // Появляется у самого казённика, а не у дула: гильза обязана быть видна
        // целиком до того, как вылетит из пушки. Раньше точка появления была на
        // 0.35 блока впереди gunPos, то есть у самой руки, и гильза возникала
        // на виду, уже отлетев от ствола.
        Vec3 portPos = gunPos
                .add(side.scale(PORT_SIDE_OFFSET))
                .add(up.scale(PORT_UP_OFFSET))
                .add(forward.scale(PORT_FORWARD));

        // Гильза вылетает из той же точки, что и пуля, и по той же причине
        // выносится из хитбокса стрелка: иначе при вертикальном выстреле она
        // появлялась бы в полу блока под ногами и тут же в нём и тонула.
        portPos = clearOfShooter(portPos, forward, player);

        // Скорости подобраны так, чтобы гильза вылетела примерно на блок в
        // сторону, упала и тут же осела: дальний разлёт гасит трение в
        // GilseEntity (HORIZONTAL_AIR_DRAG), а на полу гильза раскачивается по
        // затухающей пружине (SETTLE_STIFFNESS там же).
        //
        // Боковой выброс заметно сильнее вертикального: гильза должна улететь
        // из-под ствола в сторону, а не просто упасть под ноги.
        Vec3 shellVelocity = side.scale(0.30).add(up.scale(0.34)).add(forward.scale(0.04));

        GilseEntity gilse = new GilseEntity(serverLevel, player, portPos, shellVelocity);
        gilse.enforceLimit(serverLevel, player);
        serverLevel.addFreshEntity(gilse);

        // Звук выстрела — серверный, но не «сейчас»: времена берём из помеченных
        // кадров анимации и раскладываем по серверным тикам, чтобы щелчок
        // совпал с пиком отдачи. Вспышку добавляет клиент по локатору модели.
        scheduleClipSounds(serverLevel, player, stack, MachineGunClientAnim.SHOT, SHOT_CLIP_SECONDS);
        // Анимацию выстрела запускаем только здесь — на реальном выстреле.
        sendAnim(player, MachineGunClientAnim.SHOT);
    }

    /**
     * Сообщает игроку, почему оружие не сработало. Показывается в action bar и
     * не чаще раза в {@link #HANDS_WARN_COOLDOWN} тиков, иначе удержание огня
     * превращало бы подсказку в непрерывный поток текста.
     */
    private void warnBlocked(Player player, ItemStack stack, String key) {
        if (getHandsWarnTimer(stack) > 0) return;

        setHandsWarnTimer(stack, HANDS_WARN_COOLDOWN);
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    // === КЛИЕНТСКИЙ РЕНДЕР ===

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private MachineGunRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new MachineGunRenderer();
                return renderer;
            }

            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        int ammoCount = getAmmo(stack);
        String ammoId = getLoadedAmmoID(stack);

        if (ammoCount > 0) {
            int inMag = ammoCount - 1;
            tooltip.add(Component.translatable("tooltip.trd.machinegun.ammo",
                    inMag, MAX_TOTAL_AMMO).withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("tooltip.trd.machinegun.ammo_empty").withStyle(ChatFormatting.RED));
            return;
        }

        if (ammoId == null || ammoId.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.trd.machinegun.type.standard").withStyle(ChatFormatting.GRAY));
            return;
        }

        AmmoRegistry.AmmoType ammoType = AmmoRegistry.getAmmoTypeById(ammoId);
        if (ammoType == null) {
            tooltip.add(Component.translatable("tooltip.trd.machinegun.type.unknown").withStyle(ChatFormatting.GRAY));
            return;
        }

        String typeKey = "tooltip.trd.machinegun.type.standard";
        if (ammoId.contains("piercing")) {
            typeKey = "tooltip.trd.machinegun.type.piercing";
        } else if (ammoId.contains("hollow")) {
            typeKey = "tooltip.trd.machinegun.type.hollow";
        } else if (ammoId.contains("fire") || ammoId.contains("incendiary")) {
            typeKey = "tooltip.trd.machinegun.type.incendiary";
        } else if (ammoId.contains("radio")) {
            typeKey = "tooltip.trd.machinegun.type.radio";
        }

        tooltip.add(Component.translatable(typeKey).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.trd.machinegun.damage", ammoType.damage)
                .withStyle(ChatFormatting.DARK_RED));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    // === КЛИЕНТ ===

    @EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
    public static class ClientHandlers {
        /**
         * Локальный интервал между пакетами. Намеренно меньше {@link #SHOT_ANIM_TICKS}:
         * темп стрельбы задаёт сервер через {@code ShootDelay}, а клиент только
         * сигнализирует о намерении. Если клиентский интервал равнялся бы
         * серверному, пакет мог прийти на тик раньше, чем сервер дозреет, и
         * выстрел молча терялся — при удержании ЛКМ это давало провалы.
         * <p>
         * Значение равно {@link #MIN_SHOT_INTERVAL_TICKS}: клиент шлёт пакет
         * каждый тик и жмёт темп себе, а сервер отсекает лишнее. Держать
         * здесь больше незачем, а при запасе меньше полтика клиент начнёт
         * насыпать пакетами, которые сервер всё равно выбросит.
         */
        private static final int CLIENT_MIN_INTERVAL = MIN_SHOT_INTERVAL_TICKS;

        private static int clientShootTimer = 0;

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;

ItemStack stack = mc.player.getMainHandItem();
            if (!(stack.getItem() instanceof MachineGunItem)) {
                clientShootTimer = SHOT_ANIM_TICKS;
                MachineGunClientAnim.reset();
                return;
            }

            // Анимацию не выбирает этот класс: её запускает сервер через
            // PacketMachineGunAnim по факту выстрела или перезарядки. Здесь
            // только двигаем текущий клип до конца.
            MachineGunClientAnim.tick();

            if (ModKeyBindings.RELOAD_KEY.consumeClick()) {
                PacketDistributor.sendToServer(new PacketReloadGun());
                return;
            }

            if (ModKeyBindings.UNLOAD_KEY.consumeClick()) {
                PacketDistributor.sendToServer(new com.trd.network.packet.guns.PacketUnloadGun());
                return;
            }

            if (clientShootTimer > 0) clientShootTimer--;

            // Те же два запрета, что и на сервере, продублированы здесь ради
            // отклика: иначе игрок в воде или со щитом в левой руке удерживал бы
            // огонь вхолостую. Решение всё равно принимает сервер — клиентская
            // проверка нужна только чтобы не слать пакеты впустую.
            boolean blocked = !MachineGunItem.hasBothHandsFree(mc.player)
                    || MachineGunItem.isSubmerged(mc.player);

            // Проверки ReloadTimer здесь намеренно нет: сервер всё равно её делает,
            // а значение приходит на клиент только в конце перезарядки, то есть
            // может быть устаревшим и заблокировать стрельбу.
            if (mc.options.keyAttack.isDown() && clientShootTimer <= 0 && !blocked) {
                // Угол обзора по обеим осям едет вместе с пакетом: серверная копия
                // поворота игрока отстаёт на тик, и без этого пуля уходила мимо прицела.
                // Прицел тоже едет: сервер снимет разброс вдвое, когда игрок в него смотрит.
                boolean scoped = com.trd.client.overlay.MachineGunScope.isScoped();

                // Отдача камеры дёргается здесь же, в тике нажатия: углы камеры
                // живут на клиенте, и по пакету дёрганье пришло бы на тик позже
                // и не совпало бы с анимацией. Огонь отдачи берётся из оружия —
                // он уехал с сервера тем же пакетом данных, что и патроны.
                MachineGunRecoil.kick(recoilScale(mc.player, scoped, recoilHeat(stack)));

                PacketDistributor.sendToServer(
                        PacketShoot.of(mc.player.getYRot(), mc.player.getXRot(), scoped));
                clientShootTimer = CLIENT_MIN_INTERVAL;
                mc.player.attackAnim = 0;
                mc.player.oAttackAnim = 0;
                mc.player.swinging = false;
            }
        }

        @SubscribeEvent
        public static void onInput(InputEvent.InteractionKeyMappingTriggered event) {
            if (!event.isAttack()) return;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && mc.player.getMainHandItem().getItem() instanceof MachineGunItem) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        }
    }

    @EventBusSubscriber(modid = MainRegistry.MOD_ID)
    public static class CommonHandlers {
        @SubscribeEvent
        public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
            if (event.getItemStack().getItem() instanceof MachineGunItem && !event.getEntity().isCreative()) {
                event.setCanceled(true);
            }
        }
    }
}
