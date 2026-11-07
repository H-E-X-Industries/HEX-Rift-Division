package com.trd.climate.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Левый HUD-индикатор ртутного термометра.
 * <p>
 * Показывается, когда термометр находится в хотбаре (в т.ч. в главной руке) или в offhand.
 * <p>
 * Геометрия арта {@code textures/gui/item/mercury_thermometer_gui.png} (канвас 256x256, арт в левом верхнем углу:
 * тело 24x71, полоска ртути лежит в правом канале текстуры на (24,0), 4x65):
 * <ul>
 *   <li>тело термометра (шкала +50..-50 и тёмные окна трубки) рендерится целиком;</li>
 *   <li>столб ртути блитится на смещение (3,3) — точно поверх тёмных окон трубки;</li>
 *   <li>заполнение снизу вверх: -50°C = пусто, +50°C = полный столб (65 px), 0°C — центральная отметка (32,5 px).</li>
 * </ul>
 */
@EventBusSubscriber(modid = "trd", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MercuryThermometerHud {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/item/mercury_thermometer_gui.png");

    // Размеры арта внутри канваса 256x256
    private static final int BODY_WIDTH = 24;       // видимая часть тела (x 0..23)
    private static final int BODY_HEIGHT = 71;      // высота арта (y 0..70)

    // Полоска ртути: источник в правом канале текстуры и позиция внутри тела
    private static final int MERCURY_SRC_U = 24;
    private static final int MERCURY_SRC_V = 0;
    private static final int MERCURY_W = 4;
    private static final int MERCURY_H = 65;
    private static final int MERCURY_X = 3;         // смещение ртути внутри тела
    private static final int MERCURY_Y = 3;

    // Диапазон шкалы столба ртути
    private static final float TEMP_MIN = -50.0f;   // нижняя граница (пусто)
    private static final float TEMP_MAX = 50.0f;    // верхняя граница (полный столб)

    // Позиция HUD на экране: слева, по центру вертикали
    private static final int HUD_X = 3;

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.PLAYER_HEALTH,
                ResourceLocation.fromNamespaceAndPath("trd", "mercury_thermometer_hud"),
                MercuryThermometerHud::render
        );
    }

    private static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();

        // Не отображать если GUI скрыт, мир не загружен или игрок в режиме наблюдателя
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) {
            return;
        }
        if (!ThermometerClientHelper.hasMercuryThermometerVisible(mc.player)) {
            return;
        }

        float currentTemp = ClientTemperatureHandler.getCurrentTemp();

        // Тело термометра: слева, по вертикальному центру экрана
        int x = HUD_X;
        int y = (mc.getWindow().getGuiScaledHeight() - BODY_HEIGHT) / 2;

        guiGraphics.blit(TEXTURE, x, y, BODY_WIDTH, BODY_HEIGHT,
                0, 0, BODY_WIDTH, BODY_HEIGHT, 256, 256);

        // Столб ртути: -50..+50 -> 0..65 px снизу вверх
        float fraction = (currentTemp - TEMP_MIN) / (TEMP_MAX - TEMP_MIN);
        int fill = Math.round(Mth.clamp(fraction, 0.0f, 1.0f) * MERCURY_H);
        if (fill <= 0) {
            return; // нечего рисовать: термометр полностью «пуст»
        }

        // Нижняя часть источника длиной fill px, прижата к низу трубки
        int vSrc = MERCURY_SRC_V + (MERCURY_H - fill);
        int destY = y + MERCURY_Y + (MERCURY_H - fill);

        guiGraphics.blit(TEXTURE, x + MERCURY_X, destY, MERCURY_W, fill,
                MERCURY_SRC_U, vSrc, MERCURY_W, fill, 256, 256);
    }
}
