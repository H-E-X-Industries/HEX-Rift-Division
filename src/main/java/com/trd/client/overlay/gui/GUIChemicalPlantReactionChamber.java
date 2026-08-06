package com.trd.client.overlay.gui;

import com.trd.api.chemistry.ChemicalPlantRecipe;
import com.trd.api.chemistry.ChemicalPlantRecipeRegistry;
import com.trd.api.fluids.ModFluids;
import com.trd.main.MainRegistry;
import com.trd.menu.industrial.ChemicalPlantReactionChamberMenu;
import com.trd.network.ModPacketHandler;
import com.trd.network.packet.chemistry.ClearChemicalRecipePacket;
import com.trd.network.packet.chemistry.SelectChemicalRecipePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class GUIChemicalPlantReactionChamber extends AbstractContainerScreen<ChemicalPlantReactionChamberMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/machine/chemical_plant_reaction_chamber_gui.png");

    private static final int IMAGE_WIDTH = 153;
    private static final int IMAGE_HEIGHT = 184;

    private static final int SEARCH_X = 39;
    private static final int SEARCH_Y = 8;
    private static final int SEARCH_W = 64;
    private static final int SEARCH_H = 15;

    private static final int RESET_X = 123;
    private static final int RESET_Y = 6;
    private static final int RESET_W = 9;
    private static final int RESET_H = 19;
    private static final int RESET_PRESSED_U = 163;
    private static final int RESET_PRESSED_V = 46;

    private static final int SCROLLBAR_TRACK_X = 123;
    private static final int SCROLLBAR_TRACK_Y = 33;
    private static final int SCROLLBAR_TRACK_W = 8;
    private static final int SCROLLBAR_TRACK_H = 141;
    private static final int THUMB_H = 15;
    private static final int THUMB_U = 154;
    private static final int THUMB_V = 46;

    private static final int LIST_X = 22;
    private static final int LIST_Y = 33;
    private static final int LIST_W = 99;
    private static final int LIST_H = 141;
    private static final int ENTRY_H = 22;
    private static final int ENTRY_U = 154;
    private static final int ENTRY_V = 0;
    private static final int ENTRY_SELECTED_U = 154;
    private static final int ENTRY_SELECTED_V = 23;

    private static final int ICON_X = 3; // inside entry
    private static final int ICON_Y = 3; // inside entry
    private static final int ICON_SIZE = 16;
    private int guiTickCounter = 0;
    private EditBox searchBox;
    private float scrollAmount = 0f;
    private boolean isDraggingScrollBar = false;
    private final List<ChemicalPlantRecipe> displayList = new ArrayList<>();
    private int resetPressTimer = 0;
    private int cursorTimer = 0;

    public GUIChemicalPlantReactionChamber(ChemicalPlantReactionChamberMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        this.searchBox = new EditBox(this.font, this.leftPos + SEARCH_X, this.topPos + SEARCH_Y, SEARCH_W, SEARCH_H, Component.empty());
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(32);
        this.searchBox.setTextColor(0x00FFFFFF);
        this.searchBox.setFocused(true);
        this.searchBox.setResponder(text -> {
            scrollAmount = 0;
            updateRecipeList();
        });

        updateRecipeList();
    }



    private void updateRecipeList() {
        displayList.clear();
        String search = searchBox.getValue().toLowerCase().trim();

        for (ChemicalPlantRecipe recipe : ChemicalPlantRecipeRegistry.getAllRecipes()) {
            String name = recipe.getId().toString().toLowerCase();
            String localized = Component.translatable("recipe.trd." + recipe.getId().getPath()).getString().toLowerCase();
            if (search.isEmpty() || name.contains(search) || localized.contains(search)) {
                displayList.add(recipe);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        guiTickCounter++;
        if (guiTickCounter % 3 == 0) {
            if (resetPressTimer > 0) resetPressTimer--;
            cursorTimer++;
        }

        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // No default labels
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        graphics.blit(TEXTURE, x, y, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

        // Reset button
        if (resetPressTimer > 0) {
            graphics.blit(TEXTURE, x + RESET_X, y + RESET_Y, RESET_PRESSED_U, RESET_PRESSED_V, RESET_W, RESET_H);
        }

        // Recipe list
        renderRecipeList(graphics, x, y, mouseX, mouseY);

        // Scrollbar
        renderScrollBar(graphics, x, y);

        // Search text
        String content = searchBox.getValue();
        boolean focused = searchBox.isFocused();
        String cursorSymbol = (focused && (cursorTimer / 10 % 2 == 0)) ? "_" : "";
        String fullText = content + cursorSymbol;
        if (this.font.width(fullText) > SEARCH_W - 4) {
            fullText = this.font.plainSubstrByWidth(fullText, SEARCH_W - 4, true);
        }
        graphics.drawString(this.font, fullText, searchBox.getX(), searchBox.getY(), 0xAEC6CF, false);
    }

    private void renderRecipeList(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int listX = x + LIST_X;
        int listY = y + LIST_Y;

        graphics.enableScissor(listX, listY, listX + LIST_W, listY + LIST_H);

        int maxScroll = Math.max(0, (displayList.size() * ENTRY_H) - LIST_H);
        int currentOffset = (int) (scrollAmount * maxScroll);
        ResourceLocation selectedId = menu.blockEntity.getCurrentRecipeId();

        List<Component> tooltipToRender = null;
        int tooltipX = 0, tooltipY = 0;

        for (int i = 0; i < displayList.size(); i++) {
            ChemicalPlantRecipe recipe = displayList.get(i);
            int entryY = listY + (i * ENTRY_H) - currentOffset;

            if (entryY + ENTRY_H < listY || entryY > listY + LIST_H) continue;

            boolean isSelected = recipe.getId().equals(selectedId);
            int u = isSelected ? ENTRY_SELECTED_U : ENTRY_U;
            int v = isSelected ? ENTRY_SELECTED_V : ENTRY_V;

            graphics.blit(TEXTURE, listX, entryY, u, v, LIST_W, ENTRY_H);

            // Render output icon
            ItemStack iconStack = ItemStack.EMPTY;
            String amountText = "";
            if (!recipe.getItemOutputs().isEmpty()) {
                iconStack = recipe.getItemOutputs().get(0);
            } else if (!recipe.getFluidOutputs().isEmpty()) {
                FluidStack fluidOut = recipe.getFluidOutputs().get(0);
                amountText = fluidOut.getAmount() + " mB";
                net.minecraft.world.item.Item dropItem = ModFluids.getFluidDrop(fluidOut.getFluid().getFluidType());
                if (dropItem != null) {
                    iconStack = new ItemStack(dropItem);
                } else {
                    iconStack = new ItemStack(Items.BUCKET);
                }
            }

            if (!iconStack.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(listX + ICON_X, entryY + ICON_Y, 0);
                graphics.pose().scale(0.875f, 0.875f, 1f);
                graphics.renderItem(iconStack, 0, 0);
                graphics.pose().popPose();
            }

            if (!amountText.isEmpty()) {
                graphics.drawString(this.font, amountText, listX + ICON_X + 18, entryY + ICON_Y + 4, 0xFFAAAAAA, false);
            }

            // Render recipe name
            Component name = Component.translatable("recipe.trd." + recipe.getId().getPath());
            int color = getRecipeColor(recipe);
            graphics.drawString(this.font, name, listX + ICON_X + 18, entryY + ICON_Y + (amountText.isEmpty() ? 4 : 14), color, false);

            // Hover detection
            if (mouseX >= listX && mouseX < listX + LIST_W && mouseY >= entryY && mouseY < entryY + ENTRY_H) {
                tooltipToRender = buildRecipeTooltip(recipe);
                tooltipX = mouseX;
                tooltipY = mouseY;
            }
        }

        graphics.disableScissor();

        if (tooltipToRender != null) {
            graphics.renderComponentTooltip(this.font, tooltipToRender, tooltipX, tooltipY);
        }
    }

    private int getRecipeColor(ChemicalPlantRecipe recipe) {
        return 0xFFFFFF;
    }

    private List<Component> buildRecipeTooltip(ChemicalPlantRecipe recipe) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("recipe.trd." + recipe.getId().getPath()).withStyle(Style.EMPTY.withColor(0xAEC6CF)));
        tooltip.add(Component.literal(""));

        if (!recipe.getFluidInputs().isEmpty() || !recipe.getItemInputs().isEmpty()) {
            tooltip.add(Component.translatable("gui.trd.chemistry.inputs").withStyle(Style.EMPTY.withColor(0xAAAAAA)));
            for (FluidStack fs : recipe.getFluidInputs()) {
                int tint = IClientFluidTypeExtensions.of(fs.getFluid()).getTintColor() | 0xFF000000;
                MutableComponent name = fs.getDisplayName().copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tint)));
                tooltip.add(Component.literal("  ").append(name).append(Component.literal(": " + fs.getAmount() + " mB")));
            }
            for (ItemStack is : recipe.getItemInputs()) {
                tooltip.add(Component.literal("  ").append(is.getDisplayName()).append(Component.literal(" x" + is.getCount())));
            }
        }

        if (!recipe.getFluidOutputs().isEmpty() || !recipe.getItemOutputs().isEmpty()) {
            tooltip.add(Component.translatable("gui.trd.chemistry.outputs").withStyle(Style.EMPTY.withColor(0xAAAAAA)));
            for (FluidStack fs : recipe.getFluidOutputs()) {
                int tint = IClientFluidTypeExtensions.of(fs.getFluid()).getTintColor() | 0xFF000000;
                MutableComponent name = fs.getDisplayName().copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tint)));
                tooltip.add(Component.literal("  ").append(name).append(Component.literal(": " + fs.getAmount() + " mB")));
            }
            for (ItemStack is : recipe.getItemOutputs()) {
                tooltip.add(Component.literal("  ").append(is.getDisplayName()).append(Component.literal(" x" + is.getCount())));
            }
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.translatable("gui.trd.chemistry.time", String.format("%.1f", recipe.getProcessTime() / 20.0f)).withStyle(Style.EMPTY.withColor(0x888888)));
        return tooltip;
    }

    private void renderScrollBar(GuiGraphics graphics, int x, int y) {
        int trackX = x + SCROLLBAR_TRACK_X;
        int trackY = y + SCROLLBAR_TRACK_Y;

        int thumbY = trackY + (int) (scrollAmount * (SCROLLBAR_TRACK_H - THUMB_H));
        graphics.blit(TEXTURE, trackX, thumbY, THUMB_U, THUMB_V, SCROLLBAR_TRACK_W, THUMB_H);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int x = this.leftPos;
        int y = this.topPos;

        // Reset button
        if (mouseX >= x + RESET_X && mouseX <= x + RESET_X + RESET_W &&
                mouseY >= y + RESET_Y && mouseY <= y + RESET_Y + RESET_H) {
            resetPressTimer = 10;
            playClickSound();
            ModPacketHandler.INSTANCE.sendToServer(new ClearChemicalRecipePacket(menu.blockEntity.getBlockPos()));
            return true;
        }

        // Scrollbar drag
        int trackX = x + SCROLLBAR_TRACK_X;
        int trackY = y + SCROLLBAR_TRACK_Y;
        int thumbY = trackY + (int) (scrollAmount * (SCROLLBAR_TRACK_H - THUMB_H));

        if (mouseX >= trackX && mouseX <= trackX + SCROLLBAR_TRACK_W &&
                mouseY >= thumbY && mouseY <= thumbY + THUMB_H) {
            isDraggingScrollBar = true;
            return true;
        }

        if (mouseX >= trackX && mouseX <= trackX + SCROLLBAR_TRACK_W &&
                mouseY >= trackY && mouseY <= trackY + SCROLLBAR_TRACK_H) {
            float clickRatio = (float) ((mouseY - trackY) / (double) (SCROLLBAR_TRACK_H - THUMB_H));
            scrollAmount = Math.max(0f, Math.min(1f, clickRatio));
            return true;
        }

        // Recipe list click
        int listX = x + LIST_X;
        int listY = y + LIST_Y;
        if (mouseX >= listX && mouseX <= listX + LIST_W && mouseY >= listY && mouseY <= listY + LIST_H) {
            int maxScroll = Math.max(0, (displayList.size() * ENTRY_H) - LIST_H);
            int currentOffset = (int) (scrollAmount * maxScroll);
            int clickedIndex = (int) ((mouseY - listY + currentOffset) / ENTRY_H);

            if (clickedIndex >= 0 && clickedIndex < displayList.size()) {
                ChemicalPlantRecipe recipe = displayList.get(clickedIndex);
                playClickSound();
                ModPacketHandler.INSTANCE.sendToServer(new SelectChemicalRecipePacket(menu.blockEntity.getBlockPos(), recipe.getId()));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScrollBar && button == 0) {
            int trackY = this.topPos + SCROLLBAR_TRACK_Y;
            float dragRatio = (float) ((mouseY - trackY) / (double) (SCROLLBAR_TRACK_H - THUMB_H));
            scrollAmount = Math.max(0f, Math.min(1f, dragRatio));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDraggingScrollBar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int maxScroll = Math.max(0, (displayList.size() * ENTRY_H) - LIST_H);
        if (maxScroll > 0) {
            scrollAmount = Math.max(0f, Math.min(1f, scrollAmount - (float) (delta * ENTRY_H / maxScroll)));
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return searchBox.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return searchBox.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playClickSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}