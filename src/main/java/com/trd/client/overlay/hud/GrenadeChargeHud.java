package com.trd.client.overlay.hud;

import com.trd.item.weapons.grenades.ChargableGrenadeItem;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Полоса зарядки гранаты под прицелом и полоса кулдауна после броска.
 * Показывается только когда в руке граната с зарядкой.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class GrenadeChargeHud {

    private static final int BAR_WIDTH = 20;
    private static final int BAR_HEIGHT = 1;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ChargableGrenadeItem)) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Если игрок заряжает (удерживает ПКМ)
        if (player.isUsingItem() && player.getItemInHand(player.getUsedItemHand()) == stack) {
            int useTicks = player.getTicksUsingItem();
            float progress = Math.min(1.0f, useTicks / (float) ChargableGrenadeItem.MAX_CHARGE_TICKS);

            renderChargeBar(graphics, screenWidth, screenHeight, progress);
        }
        // Если есть кулдаун (после броска) — показываем перезарядку
        else if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            float cooldownPercent = player.getCooldowns().getCooldownPercent(stack.getItem(), 0);
            renderCooldownBar(graphics, screenWidth, screenHeight, cooldownPercent);
        }
    }

    private static void renderChargeBar(GuiGraphics graphics, int screenWidth, int screenHeight, float progress) {
        int x = (screenWidth - BAR_WIDTH) / 2;
        int y = screenHeight / 2 + 7;

        int fillWidth = (int) (BAR_WIDTH * progress);
        int color = progress >= 1.0f ? 0xFF00FF00 : 0xFFFFAA00;

        graphics.fill(x, y, x + fillWidth, y + BAR_HEIGHT, color);
        graphics.fill(x, y, x + fillWidth, y + 1, 0xFFFFFFFF & (progress >= 1.0f ? 0x88FFFFFF : 0x44FFFFFF));
    }

    private static void renderCooldownBar(GuiGraphics graphics, int screenWidth, int screenHeight, float cooldownPercent) {
        int x = (screenWidth - BAR_WIDTH) / 2;
        int y = screenHeight / 2 + 7;

        int fillWidth = (int) (BAR_WIDTH * (1.0f - cooldownPercent));

        graphics.fill(x, y, x + fillWidth, y + BAR_HEIGHT, 0xFFFFFFFF);
    }
}
