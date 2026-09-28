package com.trd.client.renderer;

import com.trd.client.overlay.hud.OverlayAmmoHud;
import com.trd.item.weapons.guns.MachineGunItem;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Слои GUI, связанные с автопушкой.
 * <p>
 * В 1.20.1 это делалось отменой ванильного оверлея прицела
 * ({@code RenderGuiOverlayEvent.Pre} + {@code setCanceled(true)}) и точечным
 * рисунком. В NeoForge 1.21 оверлеи стали слоями {@code LayeredDraw}, которые
 * нельзя отменить, но можно подменить ({@code wrapLayer}) — это единственный
 * способ сохранить поведение 1.20.1: пока пушка в руках, рисуется точка
 * вместо прицела, иначе прицел остаётся ванильным.
 * <p>
 * Счётчик патронов в 1.20.1 вешался над слоем хотбара, здесь он регистрируется
 * там же через {@code registerAbove}, иначе {@code RenderGuiEvent.Pre} рисовал
 * бы его под всеми слоями GUI.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientRenderHandler {

    /** Точка 1x1 в центре экрана, полупрозрачная (как в 1.20.1). */
    private static final int DOT_COLOR = 0x80FFFFFF;

    private static final ResourceLocation AMMO_HUD_LAYER =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "ammo_hud");

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.wrapLayer(VanillaGuiLayers.CROSSHAIR, vanillaCrosshair -> (graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getMainHandItem().getItem() instanceof MachineGunItem) {
                int x = mc.getWindow().getGuiScaledWidth() / 2;
                int y = mc.getWindow().getGuiScaledHeight() / 2;

                graphics.fill(x, y, x + 1, y + 1, DOT_COLOR);
                return;
            }

            // Пушка не в руках — рисуем родной прицел, иначе он бы пропадал совсем.
            vanillaCrosshair.render(graphics, delta);
        });

        event.registerAbove(VanillaGuiLayers.HOTBAR, AMMO_HUD_LAYER, OverlayAmmoHud::render);
    }
}
