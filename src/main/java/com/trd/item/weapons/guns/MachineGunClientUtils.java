package com.trd.item.weapons.guns;

import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Клиентская часть анимаций пушки. Вызывается только на клиенте, поэтому
 * {@link Minecraft} здесь доступен без проверки окружения.
 */
public class MachineGunClientUtils {

    public static PlayState handleAnimation(MachineGunItem item, AnimationState<MachineGunItem> event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return PlayState.CONTINUE;

        ItemStack mainHandStack = mc.player.getMainHandItem();
        if (mainHandStack.getItem() != item) {
            return PlayState.STOP;
        }

        if (event.getController().getAnimationState() == AnimationController.State.RUNNING) {
            String currentAnim = event.getController().getCurrentAnimation().animation().name();
            if ("reload".equals(currentAnim) || "flip".equals(currentAnim) ||
                    "shot_empty".equals(currentAnim) || "shot".equals(currentAnim)) {
                return PlayState.CONTINUE;
            }
        }

        boolean isKeyDown = mc.options.keyAttack.isDown();
        boolean hasAmmo = item.getAmmo(mainHandStack) > 0;
        boolean isReloading = item.getReloadTimer(mainHandStack) > 0;
        int shootDelay = item.getShootDelay(mainHandStack);

        if (isKeyDown && !isReloading) {
            if (hasAmmo || shootDelay > 10) {
                return event.setAndContinue(RawAnimation.begin().thenPlay("shot"));
            }
            return PlayState.CONTINUE;
        }

        return PlayState.STOP;
    }

    public static void playSoundClient(String soundName) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(soundName));
        if (sound == null && !soundName.contains(":")) {
            sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, soundName));
        }

        if (sound != null) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                player.playSound(sound, 1.0F, 1.0F);
            }
        }
    }
}
