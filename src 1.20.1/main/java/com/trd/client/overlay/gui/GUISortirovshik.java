package com.trd.client.overlay.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.trd.block.entity.industrial.conveyors.SortirovshikBlockEntity;
import com.trd.main.MainRegistry;
import com.trd.menu.industrial.SortirovshikMenu;
import com.trd.network.ModPacketHandler;
import com.trd.network.packet.conveyor.UpdateSortirovshikFilterC2SPacket;
import com.trd.network.packet.conveyor.UpdateSortirovshikModeC2SPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI сортировщика.
 *
 * Текстура 256x256, видимая часть 186x172 (blit с 0,0).
 * Инвентарь игрока начинается на 13-90 (внутренняя область слота).
 * Секции (координаты внутреннего поля первого слота секции):
 *   красная 9-9, оранжевая 67-9, жёлтая 125-9,
 *   зелёная 9-49, циановая 67-49, маджента 125-49.
 * Схема секции (#@# / ###): @ — кнопка режима 15x15, между ней и слотами 1px промежуток.
 * Иконки режимов лежат на текстуре в ряд от 187-1 (по 15x15 без промежутков).
 */
public class GUISortirovshik extends AbstractContainerScreen<SortirovshikMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/storage/sortirovshik_gui.png");

    private static final int IMAGE_WIDTH = 186;
    private static final int IMAGE_HEIGHT = 172;

    // Координаты секций (левый верх внутреннего поля первого слота)
    private static final int[][] SECTION_ORIGINS = {
            {9, 9},    // RED
            {67, 9},   // ORANGE
            {125, 9},  // YELLOW
            {9, 49},   // GREEN
            {67, 49},  // CYAN
            {125, 49}  // MAGENTA
    };

    // Смещения 5 слотов внутри секции (верхний левый угол внутреннего поля слота 16x16)
    private static final int[][] SLOT_OFFSETS = {
            {0, 0}, {36, 0},
            {0, 18}, {18, 18}, {36, 18}
    };

    // Кнопка режима внутри секции: иконка 15x15
    private static final int BUTTON_OFFSET_X = 18;
    private static final int BUTTON_OFFSET_Y = 0;
    private static final int BUTTON_SIZE = 15;

    // Иконки режимов на текстуре: ряд от (187,1), по 15px
    private static final int MODE_ICONS_U = 187;
    private static final int MODE_ICONS_V = 1;

    public GUISortirovshik(SortirovshikMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        // Убираем рендеринг заголовка и плашки "Инвентарь"
        this.titleLabelX = -9999;
        this.inventoryLabelX = -9999;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = this.leftPos;
        int y = this.topPos;

        // Основная текстура GUI
        graphics.blit(TEXTURE, x, y, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

        // Иконки текущих режимов на кнопках всех секций
        for (int s = 0; s < SortirovshikBlockEntity.SECTIONS; s++) {
            int mode = menu.getMode(s);
            graphics.blit(TEXTURE,
                    x + SECTION_ORIGINS[s][0] + BUTTON_OFFSET_X,
                    y + SECTION_ORIGINS[s][1] + BUTTON_OFFSET_Y,
                    MODE_ICONS_U + mode * BUTTON_SIZE, MODE_ICONS_V,
                    BUTTON_SIZE, BUTTON_SIZE);
        }

        // Фантомные предметы фильтров
        for (int s = 0; s < SortirovshikBlockEntity.SECTIONS; s++) {
            for (int i = 0; i < SortirovshikBlockEntity.FILTERS_PER_SECTION; i++) {
                ItemStack ghost = menu.getFilter(s * SortirovshikBlockEntity.FILTERS_PER_SECTION + i);
                if (!ghost.isEmpty()) {
                    graphics.renderItem(ghost,
                            x + SECTION_ORIGINS[s][0] + SLOT_OFFSETS[i][0],
                            y + SECTION_ORIGINS[s][1] + SLOT_OFFSETS[i][1]);
                }
            }
        }
    }

    // --- Тултипы ---

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        // Фантомные предметы
        Integer filterIndex = hoveredFilterIndex(mouseX, mouseY);
        if (filterIndex != null) {
            ItemStack ghost = menu.getFilter(filterIndex);
            if (!ghost.isEmpty()) {
                graphics.renderTooltip(this.font, ghost, mouseX, mouseY);
                return;
            }
        }

        // Кнопки режимов
        Integer section = hoveredSectionButton(mouseX, mouseY);
        if (section != null) {
            String key = switch (menu.getMode(section)) {
                case SortirovshikBlockEntity.MODE_BLACKLIST -> "blacklist";
                case SortirovshikBlockEntity.MODE_WHITELIST -> "whitelist";
                case SortirovshikBlockEntity.MODE_UNIVERSAL -> "universal";
                default -> "closed";
            };

            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("gui.trd.sortirovshik.mode." + key)
                    .withStyle(SortirovshikBlockEntity.Section.values()[section].color()));
            tooltip.add(Component.translatable("gui.trd.sortirovshik.mode." + key + ".desc")
                    .withStyle(ChatFormatting.GRAY));
            graphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    // --- Клики ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Кнопки режимов — чередуют режимы по кругу
        Integer section = hoveredSectionButton(mouseX, mouseY);
        if (section != null) {
            playSound();
            menu.blockEntity.cycleMode(section); // предсказание на клиенте
            ModPacketHandler.INSTANCE.sendToServer(new UpdateSortirovshikModeC2SPacket(menu.blockEntity.getBlockPos(), section));
            return true;
        }

        // Фантомные слоты фильтров
        Integer filterIndex = hoveredFilterIndex(mouseX, mouseY);
        if (filterIndex != null) {
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty()) {
                menu.blockEntity.setFilter(filterIndex, carried); // копия, предмет игрока не тратится
            } else {
                menu.blockEntity.clearFilter(filterIndex); // фантом исчезает
            }
            ModPacketHandler.INSTANCE.sendToServer(new UpdateSortirovshikFilterC2SPacket(menu.blockEntity.getBlockPos(), filterIndex));
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // --- Хелперы попадания курсора ---

    private Integer hoveredSectionButton(double mouseX, double mouseY) {
        for (int s = 0; s < SortirovshikBlockEntity.SECTIONS; s++) {
            if (isMouseOver(mouseX, mouseY,
                    SECTION_ORIGINS[s][0] + BUTTON_OFFSET_X,
                    SECTION_ORIGINS[s][1] + BUTTON_OFFSET_Y,
                    BUTTON_SIZE, BUTTON_SIZE)) {
                return s;
            }
        }
        return null;
    }

    private Integer hoveredFilterIndex(double mouseX, double mouseY) {
        for (int s = 0; s < SortirovshikBlockEntity.SECTIONS; s++) {
            for (int i = 0; i < SortirovshikBlockEntity.FILTERS_PER_SECTION; i++) {
                if (isMouseOver(mouseX, mouseY,
                        SECTION_ORIGINS[s][0] + SLOT_OFFSETS[i][0],
                        SECTION_ORIGINS[s][1] + SLOT_OFFSETS[i][1],
                        16, 16)) {
                    return s * SortirovshikBlockEntity.FILTERS_PER_SECTION + i;
                }
            }
        }
        return null;
    }

    private boolean isMouseOver(double mouseX, double mouseY, int x, int y, int sizeX, int sizeY) {
        return (mouseX >= this.leftPos + x && mouseX <= this.leftPos + x + sizeX &&
                mouseY >= this.topPos + y && mouseY <= this.topPos + y + sizeY);
    }

    private void playSound() {
        if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}
