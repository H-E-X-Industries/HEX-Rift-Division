package com.trd.client.overlay.gui;

import com.trd.api.fluids.ModFluids;
import com.trd.main.MainRegistry;
import com.trd.menu.industrial.VishelashivatelMenu;
import com.trd.multiblock.industrial.vishelashivatel.VishelashivatelBlockEntity;
import com.trd.multiblock.industrial.vishelashivatel.VishelashivatelRecipe;
import com.trd.multiblock.industrial.vishelashivatel.VishelashivatelRecipes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class GUIVishelashivatel extends AbstractContainerScreen<VishelashivatelMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MainRegistry.MOD_ID, "textures/gui/machine/vishelashivatel_gui.png");

    // Зона рендера жидкости
    private static final int FLUID_X = 54, FLUID_Y = 11, FLUID_W = 50, FLUID_H = 26;
    // Прогрессбар
    private static final int PROGRESS_X = 46, PROGRESS_Y = 49, PROGRESS_W = 48, PROGRESS_H = 8;

    public GUIVishelashivatel(VishelashivatelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 153;
        this.inventoryLabelX = -9999;
        this.inventoryLabelY = -9999;
        this.titleLabelX = -9999;
        this.titleLabelY = -9999;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        gui.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // Прогрессбар: спрайт на текстуре в (177,25)
        if (menu.getMaxProgress() > 0) {
            int fillWidth = (int) ((menu.getProgress() / (float) menu.getMaxProgress()) * PROGRESS_W);
            if (fillWidth > 0) {
                gui.blit(TEXTURE, x + PROGRESS_X, y + PROGRESS_Y, 177, 25, fillWidth, PROGRESS_H);
            }
        }

        renderFluidTank(gui, x + FLUID_X, y + FLUID_Y, FLUID_W, FLUID_H);
    }

    /** Рендер жидкости — стиль бочки/коксовой печи. */
    private void renderFluidTank(GuiGraphics gui, int x, int y, int w, int h) {
        VishelashivatelBlockEntity be = menu.getBlockEntity();
        if (be == null) return;
        FluidStack fluid = be.getFluidTank().getFluid();
        if (fluid.isEmpty()) return;

        int amount = fluid.getAmount();
        int capacity = be.getFluidTank().getCapacity();
        int fillH = (int) ((amount * h) / (float) capacity);
        if (fillH <= 0) return;

        ResourceLocation guiTexture = ModFluids.getGuiTexture(fluid.getFluid());
        gui.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        int top = y + h - fillH;
        for (int j = 0; j < fillH; j += 16) {
            int segH = Math.min(fillH - j, 16);
            int drawY = top + j;
            for (int i = 0; i < w; i += 16) {
                int segW = Math.min(w - i, 16);
                int drawX = x + i;
                gui.blit(guiTexture, drawX, drawY, 0, 0, segW, segH, 16, 16);
            }
        }

        int surfaceY = y + h - fillH;
        gui.fill(x, surfaceY, x + w, surfaceY + 1, 0x40FFFFFF);
        gui.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {}

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, delta);

        if (isHovering(FLUID_X, FLUID_Y, FLUID_W, FLUID_H, mouseX, mouseY)) {
            renderFluidTooltip(gui, mouseX, mouseY);
        } else if (this.hoveredSlot != null && this.hoveredSlot.hasItem()
                && this.hoveredSlot.getContainerSlot() == VishelashivatelBlockEntity.INPUT_SLOT) {
            // Подсказка: что нужно для обработки предмета во входном слоте
            renderInputRecipeTooltip(gui, mouseX, mouseY);
        } else {
            this.renderTooltip(gui, mouseX, mouseY);
        }
    }

    /** Тултип жидкости — стиль жидкостной бочки; пустой буфер показывает запомненный тип. */
    private void renderFluidTooltip(GuiGraphics gui, int mx, int my) {
        VishelashivatelBlockEntity be = menu.getBlockEntity();
        if (be == null) return;
        FluidStack fluid = be.getFluidTank().getFluid();

        List<Component> lines = new ArrayList<>();

        if (!fluid.isEmpty()) {
            MutableComponent fluidName = fluid.getDisplayName().copy();
            int tint = IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor() | 0xFF000000;
            fluidName = fluidName.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tint)));
            lines.add(fluidName);
            lines.add(Component.translatable("gui.trd.vishelashivatel.fluid_amount",
                            fluid.getAmount(), be.getFluidTank().getCapacity())
                    .withStyle(ChatFormatting.GRAY));
        } else if (be.getTargetFluid() != Fluids.EMPTY) {
            // Буфер запомнил тип жидкости — показываем его, даже если бак пуст
            var target = be.getTargetFluid();
            MutableComponent targetName = new FluidStack(target, 1000).getDisplayName().copy();
            int tint = IClientFluidTypeExtensions.of(target).getTintColor() | 0xFF000000;
            targetName = targetName.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tint)));
            lines.add(targetName);
            lines.add(Component.translatable("gui.trd.vishelashivatel.fluid_amount",
                            0, be.getFluidTank().getCapacity())
                    .withStyle(ChatFormatting.GRAY));
        } else {
            gui.renderTooltip(this.font,
                    Component.translatable("gui.trd.vishelashivatel.empty_tank").withStyle(ChatFormatting.GRAY), mx, my);
            return;
        }
        gui.renderComponentTooltip(this.font, lines, mx, my);
    }

    /** Рецепты для предмета во входном слоте: жидкость, обороты, время. */
    private void renderInputRecipeTooltip(GuiGraphics gui, int mx, int my) {
        ItemStack input = this.hoveredSlot.getItem();
        List<Component> lines = new ArrayList<>();
        boolean any = false;

        for (VishelashivatelRecipe recipe : VishelashivatelRecipes.getAllRecipes()) {
            if (!ItemStack.isSameItemSameTags(input, recipe.getItemInput())) continue;
            any = true;

            lines.add(Component.translatable("recipe.trd." + recipe.getId().getPath())
                    .withStyle(ChatFormatting.YELLOW));

            FluidStack required = recipe.getRequiredFluid();
            MutableComponent fluidName = required.getDisplayName().copy();
            int tint = IClientFluidTypeExtensions.of(required.getFluid()).getTintColor() | 0xFF000000;
            fluidName = fluidName.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tint)));
            lines.add(Component.translatable("gui.trd.vishelashivatel.fluid_req").withStyle(ChatFormatting.GRAY)
                    .append(" ").append(fluidName)
                    .append(" x" + required.getAmount() + " mB"));

            // Предметные входы
            lines.add(Component.translatable("gui.trd.vishelashivatel.item_input").withStyle(ChatFormatting.GRAY)
                    .append(" ").append(recipe.getItemInput().getHoverName())
                    .append(" x" + recipe.getItemInput().getCount()));

            // Предметные выходы
            for (ItemStack out : recipe.getItemOutputs()) {
                lines.add(Component.translatable("gui.trd.vishelashivatel.item_output").withStyle(ChatFormatting.GRAY)
                        .append(" ").append(out.getHoverName())
                        .append(" x" + out.getCount()));
            }

            lines.add(Component.translatable("gui.trd.vishelashivatel.min_rpm", recipe.getMinRpm())
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.trd.vishelashivatel.time",
                            String.format("%.1f", recipe.getProcessTime() / 20f))
                    .withStyle(ChatFormatting.GRAY));
        }

        if (!any) return;
        gui.renderComponentTooltip(this.font, lines, mx, my);
    }
}
