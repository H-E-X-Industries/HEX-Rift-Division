package com.trd.item.weapons.guns;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import com.trd.main.MainRegistry;

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
            if ("reload".equals(currentAnim) || "flip".equals(currentAnim) || "shot_empty".equals(currentAnim)) {
                return PlayState.CONTINUE;
            }
            if ("shot".equals(currentAnim)) {
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
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundName));
        if (sound == null && !soundName.contains(":")) {
            sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(MainRegistry.MOD_ID, soundName));
        }

        if (sound != null) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                player.playSound(sound, 1.0F, 1.0F);
            }
        }
    }
}
