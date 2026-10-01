package com.trd.item.weapons.guns;

import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Клиентский автомат анимаций пушки.
 * <p>
 * Раньше состояние анимаций держал геколибовский {@code AnimationController}:
 * предикат выбирал клип, а {@code SoundKeyframeHandler} проигрывал звуки в
 * keyframe-точках. С переходом на glTF тикать контроллеры больше некому —
 * рендерером стал GemRender, который про геколиб ничего не знает. Поэтому
 * автомат держит своё состояние: имя текущего клипа, сколько тиков он идёт и
 * на каких тиках проигрывать звуки.
 * <p>
 * Звуки и тайминги перенесены из {@code animations/machinegun.animation.json}
 * один в один: gunpull на 0.4583 с, heavy_gunclick на 3.6667 с, gunclick на
 * 4.625 с — это 9, 73 и 92 тика.
 */
public final class MachineGunClientAnim {

    /** Клипы, которые сервер может попросить проиграть один раз. */
    public static final String RELOAD = "reload";
    public static final String FLIP = "flip";

    /** Клип стрельбы крутится циклически, пока зажат огонь. */
    public static final String SHOT = "shot";

    private static final float SHOT_DURATION = 0.3333f;
    private static final float RELOAD_DURATION = 5.0f;
    private static final float FLIP_DURATION = 5.0f;

    /** Тики, на которых звучат затворные звуки перезарядки. */
    private static final int SOUND_MAG_PULL = 9;
    private static final int SOUND_HEAVY_CLICK = 73;
    private static final int SOUND_CLICK = 92;

    @Nullable
    private static String current;
    private static int age;
    private static boolean oneShot;

    private MachineGunClientAnim() {
    }

    /** Клиентский запрос на разовый клип: {@link #RELOAD} или {@link #FLIP}. */
    public static void trigger(String anim) {
        if (!RELOAD.equals(anim) && !FLIP.equals(anim)) return;
        current = anim;
        age = 0;
        oneShot = true;
    }

    /**
     * Тик клиента: продвигает текущий клип и держит «стрельбу», пока зажат огонь.
     */
    public static void tick(ItemStack stack, boolean firing) {
        if (oneShot) {
            age++;
            float duration = RELOAD.equals(current) ? RELOAD_DURATION : FLIP_DURATION;
            if (age >= duration * 20.0f) {
                current = null;
                oneShot = false;
                age = 0;
            } else {
                playReloadSound(age);
            }
            return;
        }

        boolean canFire = false;
        if (firing && stack.getItem() instanceof MachineGunItem gun) {
            canFire = gun.getAmmo(stack) > 0 && gun.getReloadTimer(stack) <= 0;
        }

        if (canFire) {
            if (!SHOT.equals(current)) {
                current = SHOT;
                age = 0;
            } else {
                age++;
                if (age >= SHOT_DURATION * 20.0f) {
                    age = 0;
                }
            }
        } else {
            current = null;
            age = 0;
        }
    }

    /** Сброс состояния — пушка убрана из руки или открыт какой-то экран. */
    public static void reset() {
        current = null;
        age = 0;
        oneShot = false;
    }

    /** Имя клипа для рендера либо {@code null}, если пушка в покое. */
    @Nullable
    public static String current() {
        return current;
    }

    /** Время клипа в секундах — GemRender ждёт именно секунды. */
    public static float seconds() {
        return age / 20.0f;
    }

    private static void playReloadSound(int tick) {
        String name = switch (tick) {
            case SOUND_MAG_PULL -> "gunpull";
            case SOUND_HEAVY_CLICK -> "heavy_gunclick";
            case SOUND_CLICK -> "gunclick";
            default -> null;
        };
        if (name == null) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(
                ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, name));
        if (sound == null) return;

        player.playSound(sound, 1.0F, 1.0F);
    }
}