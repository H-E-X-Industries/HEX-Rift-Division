package com.trd.client.overlay.hud;

import com.trd.item.weapons.guns.MachineGunItem;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Счётчик патронов над хотбаром: иконка заряженного боеприпаса и «N + 1 / 25».
 * Показывается только когда в руке пушка.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class OverlayAmmoHud {

    /** 24 в ленте + 1 в стволе. */
    private static final int MAX_AMMO = 25;
    private static final int NORMAL_COLOR = 0xFFFFFF;
    private static final int LOW_COLOR = 0xFF5555;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof MachineGunItem machineGun)) return;

        int currentAmmo = machineGun.getAmmo(stack);
        String loadedId = machineGun.getLoadedAmmoID(stack);

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int x = screenWidth - 16;
        int y = screenHeight - 16;

        if (currentAmmo > 0 && loadedId != null && !loadedId.isEmpty()) {
            Item ammoItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(loadedId));
            if (ammoItem != null) {
                graphics.renderItem(new ItemStack(ammoItem), x - 85, y - 8);
            }
        }

        String text;
        if (currentAmmo > 1) {
            text = (currentAmmo - 1) + " + 1 / " + MAX_AMMO;
        } else if (currentAmmo == 1) {
            text = "0 + 1 / " + MAX_AMMO;
        } else {
            text = "0 / " + MAX_AMMO;
        }

        int color = (currentAmmo < 5) ? LOW_COLOR : NORMAL_COLOR;

        int textWidth = mc.font.width(text);
        graphics.drawString(mc.font, text, x - textWidth, y - 6, color, true);
    }
}
