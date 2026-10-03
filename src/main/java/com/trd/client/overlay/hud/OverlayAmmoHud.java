package com.trd.client.overlay.hud;

import com.trd.item.weapons.guns.MachineGunItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Счётчик патронов над хотбаром: иконка заряженного боеприпаса и «N + 1 / 25».
 * Показывается только когда в руке пушка.
 * <p>
 * Слой вешается в
 * {@link com.trd.client.renderer.ClientRenderHandler#registerGuiLayers} над
 * хотбаром — ровно как {@code registerAbove(HOTBAR, "ammo_hud", ...)} в 1.20.1.
 * <p>
 * В прицеле слой не рисуется вовсе: {@link com.trd.client.overlay.MachineGunScope}
 * отменяет {@code RenderGuiEvent.Pre}, а на отмене слой до конца не доходит.
 * Поэтому там счётчик рисуется вручную, сразу поверх оверлея прицела.
 */
public class OverlayAmmoHud {

    /** 24 в ленте + 1 в стволе. */
    private static final int MAX_AMMO = 25;
    private static final int NORMAL_COLOR = 0xFFFFFF;
    private static final int LOW_COLOR = 0xFF5555;

    /**
     * Зазор между иконкой боезаряда и текстом счётчика, в пикселях.
     * <p>
     * Раньше иконка стояла на фиксированном отступе от правого края экрана, и
     * расстояние до текста зависело от его длины: на «24 + 1 / 25» выходило
     * около семи пикселей, а на «0 / 25» — больше тридцати, и счётчик в
     * зависимости от наличия патронов то липнул к иконке, то ли разлетался от
     * неё. Теперь иконка отсчитывается от текста, поэтому зазор всегда один.
     */
    private static final int ICON_TEXT_GAP = 3;

    /** Сторона иконки предмета: {@code renderItem} рисует квадрат 16x16. */
    private static final int ICON_SIZE = 16;

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        render(graphics);
    }

    /** Без слоя: вызывается из прицела, где слои уже отменены. */
    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof MachineGunItem machineGun)) return;

        int currentAmmo = machineGun.getAmmo(stack);
        String loadedId = machineGun.getLoadedAmmoID(stack);

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int x = screenWidth - 16;
        int y = screenHeight - 16;

        String text;
        if (currentAmmo > 1) {
            text = (currentAmmo - 1) + " + 1 / " + MAX_AMMO;
        } else if (currentAmmo == 1) {
            text = "0 + 1 / " + MAX_AMMO;
        } else {
            text = "0 / " + MAX_AMMO;
        }

        int color = (currentAmmo < 5) ? LOW_COLOR : NORMAL_COLOR;

        // Текст считается раньше иконки: без его ширины нельзя знать, где
        // иконке место. Текст прижат правым краем к x, значит левая граница —
        // это x минус его ширина.
        int textWidth = mc.font.width(text);
        int textLeft = x - textWidth;

        if (currentAmmo > 0 && loadedId != null && !loadedId.isEmpty()) {
            Item ammoItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(loadedId));
            if (ammoItem != null) {
                // Иконка целиком слева от текста и на расстоянии ICON_TEXT_GAP от
                // него, так что пересечение невозможно при любой длине строки:
                // чем короче текст, тем правее уезжает иконка.
                graphics.renderItem(new ItemStack(ammoItem),
                        textLeft - ICON_TEXT_GAP - ICON_SIZE, y - 8);
            }
        }

        graphics.drawString(mc.font, text, textLeft, y - 6, color, true);
    }
}
