package com.trd.client.overlay.gui;

import com.trd.item.weapons.turrets.TurretChipItem;
import com.trd.main.MainRegistry;
import com.trd.menu.turrets.TromboneMenu;
import com.trd.network.packet.turrets.PacketModifyTurretChip;
import com.trd.network.packet.turrets.PacketToggleExtraButton;
import com.trd.network.packet.turrets.PacketToggleTurret;
import com.trd.network.packet.turrets.PacketUpdateTurretSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI ракетной турели «Тромбон» ( меню {@link TromboneMenu} ).
 *
 * <p>Смена версий 1.20.1 -> 1.21.1 (по образцу {@link GUITurretAmmo}):
 * <ul>
 *   <li>{@code ForgeGui}/{@code IGuiHelper} больше нет — текстура рисуется
 *       обычным {@code guiGraphics.blit(TEXTURE, x, y, u, v, w, h)}.</li>
 *   <li>C2S-пакеты шлются через {@code PacketDistributor.sendToServer(...)}
 *       вместо {@code ModPacketHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(), ...)}.</li>
 *   <li>NBT стака у {@link ItemStack} в 1.21.1 больше нет — данные чипа лежат
 *       в компоненте {@link DataComponents#CUSTOM_DATA} (см. {@link CustomData}).</li>
 *   <li>{@code renderBackground} теперь принимает мышь и дельту.</li>
 * </ul>
 *
 * <p>Данные экрана берутся из {@code ContainerData} блок-сущности
 * {@code MissileTurretBlockEntity} (индексы 0..15, константы — в {@link TromboneMenu}):
 * слоты 12..15 — общее количество ракет и разбивка по типам
 * (standard / he / fire), слоты 10 и 11 — состояние двух дополнительных кнопок.
 */
public class GUITrombone extends AbstractContainerScreen<TromboneMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MainRegistry.MOD_ID, "textures/gui/turret/trombone_gui.png");

    // --- СОСТОЯНИЯ GUI ---
    private static final int STATE_NORMAL = 0;
    private static final int STATE_MAIN_MENU = 1;
    private static final int STATE_CHIP_LIST = 2;
    private static final int STATE_ADD_INPUT = 3;
    private static final int STATE_RESULT_MSG = 4;
    private static final int STATE_ATTACK_MODE = 5;
    private static final int STATE_STATS = 6;
    private static final int STATE_MISSILES = 7;

    // --- ПАЛИТРА (Pastel Terminal) ---
    private static final int COLOR_TEXT    = 0xE0E0E0; // Белый (основной)
    private static final int COLOR_GOOD    = 0x77DD77; // Зеленый (ок/вкл)
    private static final int COLOR_BAD     = 0xFF6961; // Красный (ошибка/выкл/киллы)
    private static final int COLOR_WARN    = 0xFDFD96; // Желтый (зарядка/ввод)
    private static final int COLOR_INFO    = 0xAEC6CF; // Голубой (время/списки)
    private static final int COLOR_OFF     = 0x949494; // Серый (отключено)
    private static final int COLOR_FIRE    = 0xFF8C42; // Оранжевый (фугасные ракеты)
    private static final int COLOR_HE      = 0xFF4444; // Красный (фугасные ракеты)

    private int uiState = STATE_NORMAL;
    private int selectedIndex = 0;
    private String inputString = "";
    private int cursorTimer = 0;
    private String resultMessage = "";
    private int resultColor = 0xFFFFFF;
    private int resultDuration = 0;
    /**
     * Флаг успеха для возврата в список чипа. В 1.20.1 здесь сравнивалось
     * {@code resultMessage.equals("SUCCESS")}, но resultMessage к тому моменту
     * уже содержит переведённый текст из лока — сравнение никогда не срабатывало
     * и GUI всегда проваливался в STATE_ADD_INPUT.
     */
    private boolean lastFeedbackSuccess = false;

    private int timerPlus = 0, timerMinus = 0, timerCheck = 0,
            timerLeft = 0, timerRight = 0, timerMenu = 0,
            timerExtra1 = 0, timerExtra2 = 0;

    private static final int PRESS_DURATION = 10;

    public GUITrombone(TromboneMenu menu, Inventory inventory, Component component) {
        super(menu, inventory, component);
        this.imageWidth = 201;
        this.imageHeight = 188;
    }

    public void handleFeedback(boolean success) {
        this.uiState = STATE_RESULT_MSG;
        this.resultDuration = 40;
        if (success) {
            this.lastFeedbackSuccess = true;
            this.resultMessage = Component.translatable("gui.trd.turret.result.success").getString();
            this.resultColor = COLOR_GOOD;
        } else {
            this.lastFeedbackSuccess = false;
            this.resultMessage = Component.translatable("gui.trd.turret.result.error").getString();
            this.resultColor = COLOR_BAD;
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        long energy = this.menu.getEnergyStatic();
        long maxEnergy = this.menu.getMaxEnergyStatic();
        int status = this.menu.getDataSlot(TromboneMenu.DATA_STATUS);
        boolean isSwitchedOn = this.menu.getDataSlot(TromboneMenu.DATA_SWITCH) == 1;
        int bootTimer = this.menu.getDataSlot(TromboneMenu.DATA_BOOT_TIMER);

        boolean isExtra1On = this.menu.getDataSlot(TromboneMenu.DATA_EXTRA_1) == 1;
        boolean isExtra2On = this.menu.getDataSlot(TromboneMenu.DATA_EXTRA_2) == 1;

        // === АНИМАЦИЯ КНОПКИ ВКЛЮЧЕНИЯ ===
        if (isSwitchedOn) {
            guiGraphics.blit(TEXTURE, x + 10, y + 62, 204, 103, 10, 32);
        }

        // Анимация остальных кнопок
        if (timerPlus > 0) { timerPlus--; guiGraphics.blit(TEXTURE, x + 39, y + 62, 221, 171, 15, 15); }
        if (timerMinus > 0) { timerMinus--; guiGraphics.blit(TEXTURE, x + 56, y + 62, 204, 137, 15, 15); }
        if (timerCheck > 0) { timerCheck--; guiGraphics.blit(TEXTURE, x + 22, y + 62, 204, 171, 15, 15); }
        if (timerLeft > 0) { timerLeft--; guiGraphics.blit(TEXTURE, x + 73, y + 62, 221, 137, 15, 15); }
        if (timerRight > 0) { timerRight--; guiGraphics.blit(TEXTURE, x + 90, y + 62, 221, 120, 15, 15); }
        if (timerMenu > 0) { timerMenu--; guiGraphics.blit(TEXTURE, x + 73, y + 79, 221, 154, 15, 15); }

        // Доп кнопки
        if (isExtra1On) { guiGraphics.blit(TEXTURE, x + 39, y + 79, 221, 188, 15, 15); }
        if (timerExtra1 > 0) { timerExtra1--; guiGraphics.blit(TEXTURE, x + 39, y + 79, 221, 188, 15, 15); }

        if (isExtra2On) { guiGraphics.blit(TEXTURE, x + 56, y + 79, 204, 154, 15, 15); }
        if (timerExtra2 > 0) { timerExtra2--; guiGraphics.blit(TEXTURE, x + 56, y + 79, 204, 154, 15, 15); }

        // Светодиод чипа
        if (hasChip()) { guiGraphics.blit(TEXTURE, x + 12, y + 52, 221, 113, 6, 6); }

        // === ПОЛОСКА ЭНЕРГИИ ===
        if (maxEnergy > 0 && energy > 0) {
            int barHeight = 52;
            int filledHeight = (int) ((long) energy * barHeight / maxEnergy);
            if (filledHeight > 0) {
                guiGraphics.blit(TEXTURE, x + 180, y + 27 + (barHeight - filledHeight), 204, 27 + (barHeight - filledHeight), 16, filledHeight);
            }
        }

        // === ЭКРАН ===
        if (energy > 10000 && isSwitchedOn) {
            guiGraphics.blit(TEXTURE, x + 10, y + 32, 0, 196, 95, 16);

            if (bootTimer > 0) {
                drawBootingText(guiGraphics, x + 10, y + 32, 95, 16);
                if (uiState != STATE_NORMAL) uiState = STATE_NORMAL;
            } else {
                switch (uiState) {
                    case STATE_MAIN_MENU:
                        drawMainMenu(guiGraphics, x + 10, y + 32, 95, 16);
                        break;

                    case STATE_ATTACK_MODE:
                        drawAttackMode(guiGraphics, x + 10, y + 32, 95, 16);
                        break;

                    case STATE_STATS:
                        drawStats(guiGraphics, x + 10, y + 32, 95, 16);
                        break;

                    // Ракеты: 0 = std, 1 = he, 2 = fire, 3 = всего
                    case STATE_MISSILES:
                        drawMissiles(guiGraphics, x + 10, y + 32, 95, 16);
                        break;

                    case STATE_CHIP_LIST:
                        if (!hasChip()) { uiState = STATE_MAIN_MENU; break; }
                        drawChipUserList(guiGraphics, x + 10, y + 32, 95, 16);
                        break;

                    case STATE_ADD_INPUT:
                        cursorTimer++;
                        String display = inputString + ((cursorTimer / 10 % 2 == 0) ? "_" : "");
                        if (display.length() > 14) display = display.substring(display.length() - 14);
                        drawCenteredText(guiGraphics, display, 0xFFFF00, x + 10, y + 32, 95, 16);
                        break;

                    case STATE_RESULT_MSG:
                        if (resultDuration > 0) {
                            resultDuration--;
                            drawCenteredText(guiGraphics, resultMessage, resultColor, x + 10, y + 32, 95, 16);
                        } else {
                            if (lastFeedbackSuccess) uiState = STATE_CHIP_LIST;
                            else uiState = STATE_ADD_INPUT;
                        }
                        break;

                    default: // STATE_NORMAL
                        drawStatusText(guiGraphics, x + 10, y + 32, 95, 16, status, energy, maxEnergy);
                        break;
                }
            }
        } else {
            uiState = STATE_NORMAL;
        }
    }

    // --- ОТРИСОВКА ПОДМЕНЮ ---

    private void drawMainMenu(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        if (selectedIndex < 0) selectedIndex = 3;
        if (selectedIndex > 3) selectedIndex = 0;

        String text = "";
        int color = COLOR_TEXT;

        switch (selectedIndex) {
            case 0 -> {
                text = Component.translatable("gui.trd.turret.menu.chip_control").getString();
                if (!hasChip()) color = COLOR_OFF;
            }
            case 1 -> text = Component.translatable("gui.trd.turret.menu.attack_mode").getString();
            case 2 -> text = Component.translatable("gui.trd.turret.menu.stats").getString();
            case 3 -> {
                text = Component.translatable("gui.trd.turret.menu.missiles").getString();
                int missileCount = this.menu.getDataSlot(TromboneMenu.DATA_MISSILE_COUNT);
                if (missileCount == 0) color = COLOR_BAD;
                else color = COLOR_GOOD;
            }
        }

        text = "< " + text + " >";
        drawCenteredText(guiGraphics, text, color, x, y, w, h);
    }

    private void drawAttackMode(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        if (selectedIndex < 0) selectedIndex = 2;
        if (selectedIndex > 2) selectedIndex = 0;

        String name = "";
        int valHostile = this.menu.getDataSlot(TromboneMenu.DATA_TARGET_HOSTILE);
        int valNeutral = this.menu.getDataSlot(TromboneMenu.DATA_TARGET_NEUTRAL);
        int valPlayer = this.menu.getDataSlot(TromboneMenu.DATA_TARGET_PLAYERS);
        boolean isEnabled = false;

        switch (selectedIndex) {
            case 0: name = Component.translatable("gui.trd.turret.target.hostiles").getString(); isEnabled = valHostile == 1; break;
            case 1: name = Component.translatable("gui.trd.turret.target.neutrals").getString(); isEnabled = valNeutral == 1; break;
            case 2: name = Component.translatable("gui.trd.turret.target.players").getString(); isEnabled = valPlayer == 1; break;
        }

        String symbol = isEnabled
                ? Component.translatable("gui.trd.turret.toggle.on").getString()
                : Component.translatable("gui.trd.turret.toggle.off").getString();
        int color = isEnabled ? COLOR_GOOD : COLOR_BAD;

        String text = "< " + name + " " + symbol + " >";
        drawCenteredText(guiGraphics, text, color, x, y, w, h);
    }

    private void drawStats(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        if (selectedIndex < 0) selectedIndex = 2;
        if (selectedIndex > 2) selectedIndex = 0;

        String text = "";
        int color = COLOR_TEXT;

        switch (selectedIndex) {
            case 0 -> {
                int kills = this.menu.getDataSlot(TromboneMenu.DATA_KILLS);
                text = Component.translatable("gui.trd.turret.stats.kills", kills).getString();
                color = COLOR_BAD;
            }
            case 1 -> {
                int secondsTotal = this.menu.getDataSlot(TromboneMenu.DATA_LIFETIME);
                int hours = secondsTotal / 3600;
                int minutes = (secondsTotal % 3600) / 60;
                text = Component.translatable("gui.trd.turret.stats.time", hours, minutes).getString();
                color = COLOR_INFO;
            }
            case 2 -> {
                text = Component.translatable("gui.trd.turret.stats.owner").getString();
                color = COLOR_WARN;
            }
        }

        text = "< " + text + " >";
        drawCenteredText(guiGraphics, text, color, x, y, w, h);
    }

    private void drawMissiles(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        // Слоты 13/14/15 — разбивка по типам, слот 12 — общее количество.
        int stdCount = this.menu.getDataSlot(TromboneMenu.DATA_MISSILE_STANDARD);
        int heCount = this.menu.getDataSlot(TromboneMenu.DATA_MISSILE_HE);
        int fireCount = this.menu.getDataSlot(TromboneMenu.DATA_MISSILE_FIRE);
        int total = this.menu.getDataSlot(TromboneMenu.DATA_MISSILE_COUNT);

        if (selectedIndex < 0) selectedIndex = 3;
        if (selectedIndex > 3) selectedIndex = 0;

        String text;
        int color;

        if (total == 0) {
            text = Component.translatable("gui.trd.turret.missiles.none").getString();
            color = COLOR_BAD;
        } else {
            switch (selectedIndex) {
                case 0 -> {
                    text = Component.translatable("gui.trd.turret.missiles.standard", stdCount).getString();
                    color = COLOR_TEXT;
                }
                case 1 -> {
                    text = Component.translatable("gui.trd.turret.missiles.he", heCount).getString();
                    color = COLOR_HE;
                }
                case 2 -> {
                    text = Component.translatable("gui.trd.turret.missiles.fire", fireCount).getString();
                    color = COLOR_FIRE;
                }
                default -> {
                    text = Component.translatable("gui.trd.turret.missiles.total", total).getString();
                    color = COLOR_GOOD;
                }
            }
        }

        text = "< " + text + " >";
        drawCenteredText(guiGraphics, text, color, x, y, w, h);
    }

    // --- МЫШЬ ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.leftPos;
        int y = this.topPos;
        double relX = mouseX - x;
        double relY = mouseY - y;

        if (button == 0) {
            boolean hitPower = (relX >= 10 && relX < 20 && relY >= 62 && relY < 94);
            boolean hitMenu  = (relX >= 73 && relX < 88 && relY >= 79 && relY < 94);
            boolean hitCheck = (relX >= 22 && relX < 37 && relY >= 62 && relY < 77);
            boolean hitPlus  = (relX >= 39 && relX < 54 && relY >= 62 && relY < 77);
            boolean hitMinus = (relX >= 56 && relX < 71 && relY >= 62 && relY < 77);
            boolean hitLeft  = (relX >= 73 && relX < 88 && relY >= 62 && relY < 77);
            boolean hitRight = (relX >= 90 && relX < 105 && relY >= 62 && relY < 77);
            boolean hitExtra1 = (relX >= 39 && relX < 54 && relY >= 79 && relY < 94);
            boolean hitExtra2 = (relX >= 56 && relX < 71 && relY >= 79 && relY < 94);

            if (hitPower || hitMenu || hitCheck || hitPlus || hitMinus || hitLeft || hitRight || hitExtra1 || hitExtra2) {
                playClickSound();
            }

            if (hitMenu)  timerMenu  = PRESS_DURATION;
            if (hitCheck) timerCheck = PRESS_DURATION;
            if (hitPlus)  timerPlus  = PRESS_DURATION;
            if (hitMinus) timerMinus = PRESS_DURATION;
            if (hitLeft)  timerLeft  = PRESS_DURATION;
            if (hitRight) timerRight = PRESS_DURATION;
            if (hitExtra1) timerExtra1 = PRESS_DURATION;
            if (hitExtra2) timerExtra2 = PRESS_DURATION;

            // === КНОПКА ВКЛЮЧЕНИЯ ===
            if (hitPower) {
                PacketDistributor.sendToServer(new PacketToggleTurret(this.menu.getPos()));
                return true;
            }

            // Доп кнопки (только у тромбона)
            if (hitExtra1) {
                PacketDistributor.sendToServer(new PacketToggleExtraButton(this.menu.getPos(), 1));
                return true;
            }

            if (hitExtra2) {
                PacketDistributor.sendToServer(new PacketToggleExtraButton(this.menu.getPos(), 2));
                return true;
            }

            if (hitMenu) {
                if (uiState == STATE_NORMAL) {
                    uiState = STATE_MAIN_MENU;
                    selectedIndex = hasChip() ? 0 : 1;
                } else {
                    uiState = STATE_NORMAL;
                }
                return true;
            }

            if (uiState != STATE_NORMAL && uiState != STATE_RESULT_MSG) {

                if (hitCheck) {
                    if (uiState == STATE_MAIN_MENU) {
                        if (selectedIndex == 0) {
                            if (hasChip()) { uiState = STATE_CHIP_LIST; selectedIndex = 0; }
                        } else if (selectedIndex == 1) {
                            uiState = STATE_ATTACK_MODE; selectedIndex = 0;
                        } else if (selectedIndex == 2) {
                            uiState = STATE_STATS; selectedIndex = 0;
                        } else if (selectedIndex == 3) {
                            uiState = STATE_MISSILES; selectedIndex = 0;
                        }
                    } else if (uiState == STATE_ADD_INPUT) {
                        if (!inputString.isEmpty()) {
                            PacketDistributor.sendToServer(new PacketModifyTurretChip(1, inputString));
                        }
                    }
                    return true;
                }

                if (hitLeft || hitRight) {
                    if (uiState == STATE_MAIN_MENU) {
                        if (hitLeft) selectedIndex--; else selectedIndex++;
                    } else if (uiState == STATE_CHIP_LIST || uiState == STATE_ATTACK_MODE
                            || uiState == STATE_STATS || uiState == STATE_MISSILES) {
                        if (hitLeft) selectedIndex--; else selectedIndex++;
                    }
                    return true;
                }

                if (hitPlus || hitMinus) {
                    if (uiState == STATE_CHIP_LIST) {
                        if (hitPlus) { uiState = STATE_ADD_INPUT; inputString = ""; }
                        else {
                            PacketDistributor.sendToServer(new PacketModifyTurretChip(0, String.valueOf(selectedIndex)));
                            if (selectedIndex > 0) selectedIndex--;
                        }
                    } else if (uiState == STATE_ATTACK_MODE) {
                        // settingIndex 0/1/2 = враги / нейтралы / игроки
                        boolean newValue = hitPlus;
                        PacketDistributor.sendToServer(
                                new PacketUpdateTurretSettings(this.menu.getPos(), selectedIndex, newValue));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // --- ВВОД С КЛАВИАТУРЫ ---

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (uiState == STATE_ADD_INPUT) {
            if (Character.isLetterOrDigit(codePoint) || codePoint == '_') {
                if (inputString.length() < 16) {
                    inputString += codePoint;
                    return true;
                }
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (uiState == STATE_ADD_INPUT) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!inputString.isEmpty()) {
                    inputString = inputString.substring(0, inputString.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (!inputString.isEmpty()) {
                    playClickSound();
                    timerCheck = PRESS_DURATION;
                    PacketDistributor.sendToServer(new PacketModifyTurretChip(1, inputString));
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                uiState = STATE_CHIP_LIST;
                return true;
            }
            if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // --- ХЕЛПЕРЫ ---

    private boolean hasChip() {
        ItemStack stack = this.menu.getMissileContainer().getStackInSlot(TromboneMenu.CHIP_SLOT_INDEX);
        return !stack.isEmpty() && stack.getItem() instanceof TurretChipItem;
    }

    private void drawChipUserList(GuiGraphics guiGraphics, int screenX, int screenY, int w, int h) {
        ItemStack stack = this.menu.getMissileContainer().getStackInSlot(TromboneMenu.CHIP_SLOT_INDEX);
        List<String> names = new ArrayList<>();

        // 1.21.1: NBT стака больше нет, данные чипа лежат в компоненте CUSTOM_DATA.
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (!customData.isEmpty()) {
            CompoundTag nbt = customData.copyTag();
            if (nbt.contains("TurretOwners", Tag.TAG_LIST)) {
                ListTag list = nbt.getList("TurretOwners", Tag.TAG_STRING);
                for (Tag t : list) {
                    String s = t.getAsString();
                    names.add(s.contains("|") ? s.split("\\|")[1] : s);
                }
            }
        }

        if (selectedIndex < 0) selectedIndex = names.size() - 1;
        if (selectedIndex >= names.size()) selectedIndex = 0;

        String textToShow;
        if (names.isEmpty()) textToShow = Component.translatable("gui.trd.turret.chip.empty").getString();
        else textToShow = Component.translatable("gui.trd.turret.chip.format",
                (selectedIndex + 1), names.size(), names.get(selectedIndex)).getString();

        if (!names.isEmpty()) textToShow = "< " + textToShow + " >";

        drawCenteredText(guiGraphics, textToShow, COLOR_INFO, screenX, screenY, w, h);
    }

    private void drawCenteredText(GuiGraphics guiGraphics, String textStr, int color, int screenX, int screenY, int w, int h) {
        Component text = Component.literal(textStr);
        float scale = 0.7f;
        guiGraphics.pose().pushPose();
        float textX = (screenX + 5) / scale;
        float textY = (screenY + (h - 8 * scale) / 2) / scale;
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.drawString(this.font, text, (int) textX, (int) textY, color, false);
        guiGraphics.pose().popPose();
    }

    private void drawBootingText(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        long time = System.currentTimeMillis() / 500;
        String dots = ".".repeat((int) (time % 4));
        drawCenteredText(guiGraphics,
                Component.translatable("gui.trd.turret.boot", dots).getString(), COLOR_TEXT, x, y, w, h);
    }

    private void drawStatusText(GuiGraphics guiGraphics, int x, int y, int w, int h, int status, long energy, long maxEnergy) {
        String msg;
        int color;
        if (status == 1) {
            msg = Component.translatable("gui.trd.turret.status.online").getString();
            color = COLOR_GOOD;
        } else if (status >= 200 && status <= 300) {
            msg = Component.translatable("gui.trd.turret.status.repairing", (status - 200)).getString();
            color = COLOR_WARN;
        } else if (status >= 1000) {
            msg = Component.translatable("gui.trd.turret.status.reloading", ((status - 1000) / 20)).getString();
            color = COLOR_BAD;
        } else if (status == 3000) {
            msg = Component.translatable("gui.trd.turret.status.no_missiles").getString();
            color = COLOR_BAD;
        } else {
            if (energy < maxEnergy) {
                msg = Component.translatable("gui.trd.turret.status.charging").getString();
                color = COLOR_WARN;
            } else {
                msg = Component.translatable("gui.trd.turret.status.standby").getString();
                color = COLOR_OFF;
            }
        }
        if (!msg.isEmpty()) {
            drawCenteredText(guiGraphics, msg, color, x, y, w, h);
        }
    }

    private void playClickSound() {
        if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 13, 11, 4210752, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics, mouseX, mouseY, delta);
        super.render(guiGraphics, mouseX, mouseY, delta);
        renderTooltip(guiGraphics, mouseX, mouseY);
        if (isHovering(180, 27, 16, 52, mouseX, mouseY)) {
            long energy = this.menu.getEnergyStatic();
            long maxEnergy = this.menu.getMaxEnergyStatic();
            guiGraphics.renderTooltip(this.font,
                    Component.translatable("gui.trd.turret.energy_tooltip", energy, maxEnergy), mouseX, mouseY);
        }
    }

    /**
     * Экран инвентарного типа: в одиночной игре не должен ставить мир на паузу.
     * По умолчанию {@code Screen#isPauseScreen()} возвращает {@code true}.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
