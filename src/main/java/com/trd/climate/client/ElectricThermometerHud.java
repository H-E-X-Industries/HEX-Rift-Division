package com.trd.climate.client;

import com.trd.item.climate.ElectricThermometerItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Дисплей электронного термометра — HUD в левом нижнем углу экрана.
 * <p>
 * Весь прибор нарисован на одной текстуре {@code textures/gui/item/electric_thermometer_gui.png}
 * (канвас 256x256). Геометрия арта:
 * <ul>
 *   <li>корпус прибора — (0,0), 40x22; в нём же уже нарисованы тёмные («выключенные») экраны;</li>
 *   <li>включённый экран температуры — источник (8,23), 19x7, выводится на (8,5);</li>
 *   <li>включённый экран графика — источник (30,23), 7x7, выводится на (30,5);</li>
 *   <li>цифры 0..9 — источник (2 + d*4, 39), каждая 3x5;</li>
 *   <li>точка — (3,48) 1x1, минус — (6,45) 3x3, плюс — (10,45) 3x3, «°C» — (14,45) 4x4.</li>
 * </ul>
 * Показание выводится символами с текстуры: знак на (10,7), первая цифра на (14,6),
 * вторая на (18,6), «°C» на (22,6).
 * <p>
 * Состояния прибора:
 * <ul>
 *   <li>заряд есть — экраны подсвечены (накладываются «включённые» экраны), показание и график рисуются;</li>
 *   <li>заряд кончился — остаётся только корпус с тёмными экранами.</li>
 * </ul>
 * График показывает изменение температуры за секунду: один градус — один пиксель,
 * при этом точка не перепрыгивает больше чем на пиксель за шаг, так что график
 * поднимается и опускается плавно (см. {@link TemperatureGraphHistory}).
 */
@EventBusSubscriber(modid = "trd", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ElectricThermometerHud {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("trd", "textures/gui/item/electric_thermometer_gui.png");

    /** Размер текстуры (канвас), px. */
    private static final int TEXTURE_SIZE = 256;

    // ---------------------------------------------------------------
    // Корпус прибора
    // ---------------------------------------------------------------

    /** Ширина арта корпуса, px. */
    public static final int BODY_WIDTH = 40;
    /** Высота арта корпуса, px. */
    public static final int BODY_HEIGHT = 22;

    /** Отступ дисплея от левого нижнего угла экрана, px. */
    private static final int HUD_MARGIN = 4;

    // ---------------------------------------------------------------
    // Экраны (выключенные нарисованы прямо в корпусе, включённые — отдельным куском арта)
    // ---------------------------------------------------------------

    /** Источник включённого экрана температуры (u,v) и его размер. */
    private static final int ON_TEMP_U = 8;
    private static final int ON_TEMP_V = 23;
    private static final int ON_TEMP_W = 19;
    private static final int ON_TEMP_H = 7;

    /** Источник включённого экрана графика (u,v) и его размер. */
    private static final int ON_GRAPH_U = 30;
    private static final int ON_GRAPH_V = 23;
    private static final int ON_GRAPH_SIZE = 7;

    /** Положение экрана температуры на корпусе. */
    private static final int TEMP_SCREEN_X = 8;
    private static final int TEMP_SCREEN_Y = 5;

    /** Положение окна графика на корпусе (левый верхний угол окна 7x7). */
    private static final int GRAPH_X = 30;
    private static final int GRAPH_Y = 5;

    // ---------------------------------------------------------------
    // Символы (берутся прямо с текстуры, обычный текст не рисуется)
    // ---------------------------------------------------------------

    /** Ряд цифр: 0..9, каждая 3x5, шаг между цифрами 4 px (3 + зазор 1). */
    private static final int DIGIT_U = 2;
    private static final int DIGIT_V = 39;
    private static final int DIGIT_W = 3;
    private static final int DIGIT_H = 5;
    private static final int DIGIT_STEP = 4;

    /** Ряд знаков: точка 1x1, минус 3x3, плюс 3x3, «°C» 4x4. */
    private static final int DOT_U = 3;
    private static final int DOT_V = 48;
    private static final int MINUS_U = 6;
    private static final int PLUS_U = 10;
    private static final int SIGN_V = 45;
    private static final int SIGN_SIZE = 3;
    private static final int CELSIUS_U = 14;
    private static final int CELSIUS_V = 45;
    private static final int CELSIUS_W = 4;
    private static final int CELSIUS_H = 4;

    /** Места вывода показания на корпусе. */
    private static final int SIGN_X = 10;
    private static final int SIGN_Y = 7;
    private static final int DIGIT1_X = 14;
    private static final int DIGIT2_X = 18;
    private static final int DIGIT_Y = 6;
    private static final int CELSIUS_X = 22;
    private static final int CELSIUS_Y = 6;

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.PLAYER_HEALTH,
                ResourceLocation.fromNamespaceAndPath("trd", "electric_thermometer_hud"),
                ElectricThermometerHud::render
        );
    }

    private static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();

        // Не отображать если GUI скрыт, мир не загружен или игрок в режиме наблюдателя
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) {
            return;
        }

        // Прибор работает, пока он лежит в инвентаре (заряд тут не проверяем —
        // разряженный термометр тоже показывается, но с тёмными экранами)
        ItemStack thermometer = ThermometerClientHelper.findElectricThermometer(mc.player);
        if (thermometer == null) {
            return;
        }

        // Левый нижний угол экрана
        int x = HUD_MARGIN;
        int y = mc.getWindow().getGuiScaledHeight() - BODY_HEIGHT - HUD_MARGIN;

        drawThermometer(guiGraphics, x, y, ElectricThermometerItem.getEnergy(thermometer) > 0);
    }

    /**
     * Рисует прибор: корпус, включённые экраны, показание температуры и график.
     *
     * @param guiGraphics график интерфейса
     * @param x          левый край прибора на экране
     * @param y          верхний край прибора на экране
     * @param powered    true — прибор запитан (экраны горят), false — разряжен
     */
    public static void drawThermometer(GuiGraphics guiGraphics, int x, int y, boolean powered) {
        // Корпус (в нём уже есть тёмные «выключенные» экраны)
        guiGraphics.blit(TEXTURE, x, y, BODY_WIDTH, BODY_HEIGHT,
                0.0F, 0.0F, BODY_WIDTH, BODY_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);

        if (!powered) {
            // Прибор разряжен — экраны остаются тёмными.
            return;
        }

        // Подсвеченные экраны
        guiGraphics.blit(TEXTURE, x + TEMP_SCREEN_X, y + TEMP_SCREEN_Y, ON_TEMP_W, ON_TEMP_H,
                ON_TEMP_U, ON_TEMP_V, ON_TEMP_W, ON_TEMP_H, TEXTURE_SIZE, TEXTURE_SIZE);
        guiGraphics.blit(TEXTURE, x + GRAPH_X, y + GRAPH_Y, ON_GRAPH_SIZE, ON_GRAPH_SIZE,
                ON_GRAPH_U, ON_GRAPH_V, ON_GRAPH_SIZE, ON_GRAPH_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);

        drawTemperature(guiGraphics, x, y, ClientTemperatureHandler.getCurrentTemp());
        drawGraph(guiGraphics, x, y);
    }

    /**
     * Рисует показание температуры символами с текстуры: знак, две цифры и «°C».
     */
    private static void drawTemperature(GuiGraphics guiGraphics, int x, int y, float currentTemp) {
        int rounded = Math.round(currentTemp);
        boolean negative = rounded < 0;
        // Два разряда: всё, что больше 99, обрезаем
        int value = Math.min(Math.abs(rounded), 99);
        int tens = value / 10;
        int units = value % 10;

        // Знак: «-» для отрицательной температуры, «+» для положительной
        int signU = negative ? MINUS_U : PLUS_U;
        guiGraphics.blit(TEXTURE, x + SIGN_X, y + SIGN_Y, SIGN_SIZE, SIGN_SIZE,
                signU, SIGN_V, SIGN_SIZE, SIGN_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);

        // Две цифры
        guiGraphics.blit(TEXTURE, x + DIGIT1_X, y + DIGIT_Y, DIGIT_W, DIGIT_H,
                DIGIT_U + tens * DIGIT_STEP, DIGIT_V, DIGIT_W, DIGIT_H, TEXTURE_SIZE, TEXTURE_SIZE);
        guiGraphics.blit(TEXTURE, x + DIGIT2_X, y + DIGIT_Y, DIGIT_W, DIGIT_H,
                DIGIT_U + units * DIGIT_STEP, DIGIT_V, DIGIT_W, DIGIT_H, TEXTURE_SIZE, TEXTURE_SIZE);

        // «°C»
        guiGraphics.blit(TEXTURE, x + CELSIUS_X, y + CELSIUS_Y, CELSIUS_W, CELSIUS_H,
                CELSIUS_U, CELSIUS_V, CELSIUS_W, CELSIUS_H, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    /**
     * Рисует график изменения температуры: по точке на каждый столбец,
     * новое значение справа, ноль — в центре окна.
     */
    private static void drawGraph(GuiGraphics guiGraphics, int x, int y) {
        int[] rows = TemperatureGraphHistory.getRows();
        for (int column = 0; column < TemperatureGraphHistory.COLUMNS; column++) {
            int row = rows[column];
            if (row < 0) {
                // Столбец ещё без данных
                continue;
            }
            guiGraphics.blit(TEXTURE, x + GRAPH_X + column, y + GRAPH_Y + row, 1, 1,
                    DOT_U, DOT_V, 1, 1, TEXTURE_SIZE, TEXTURE_SIZE);
        }
    }
}
