package com.trd.client.overlay.hud;

import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.vishelashivatel.VishelashivatelBlockEntity;
import com.trd.multiblock.industrial.vishelashivatel.VishelashivatelRecipe;
import com.trd.multiblock.industrial.vishelashivatel.VishelashivatelRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD выщелащивателя — расположение, стиль и оформление скопированы
 * с HUD реакционной камеры химической установки.
 */
@Mod.EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VishelashivatelOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);

        // Наведение на парт мультиблока — показываем HUD контроллера
        if (be instanceof com.trd.multiblock.system.MultiblockPartEntity part && part.getControllerPos() != null) {
            be = mc.level.getBlockEntity(part.getControllerPos());
        }
        if (!(be instanceof VishelashivatelBlockEntity leacher)) return;

        renderHUD(event.getGuiGraphics(), leacher, event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight(), mc.font);
    }

    private static void renderHUD(GuiGraphics gui, VishelashivatelBlockEntity be,
                                  int screenW, int screenH, Font font) {
        int x = screenW / 2 + 12;
        int y = screenH / 2 + 4;

        // На клиенте serverTick не вызывается — рецепт резолвим так же, как в тултипе GUI:
        // по входному предмету, чтобы игрок видел требования до заливки жидкости
        FluidStack tankFluid = be.getFluidTank().getFluid();
        ItemStack input = be.getInventory().getStackInSlot(VishelashivatelBlockEntity.INPUT_SLOT);
        VishelashivatelRecipe recipe = VishelashivatelRecipes.findForInput(input);

        if (recipe == null) {
            String txt = Component.translatable("hud.trd.leacher.no_recipe").getString();
            int w = font.width(txt);
            if (x + w + 4 > screenW) x = screenW / 2 - w - 12;
            gui.fill(x - 3, y - 2, x + w + 3, y + font.lineHeight + 2, 0x90000000);
            gui.drawString(font, txt, x, y, 0xAAAAAA, true);
            return;
        }

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        int maxW = 0;

        // Рецепт
        String recipeName = "§e" + Component.translatable("recipe.trd." + recipe.getId().getPath()).getString();
        lines.add(recipeName);
        colors.add(0xFFFFFF);
        maxW = Math.max(maxW, font.width(recipeName));

        // Входная жидкость
        FluidStack required = recipe.getRequiredFluid();
        int cur = be.getFluidTank().getFluid().getAmount();
        String arrowIn = Component.translatable("hud.trd.leacher.arrow_in").getString(); // -->
        String fluidLine = arrowIn + " " + Component.translatable("hud.trd.leacher.input").getString()
                + " " + cur + "/" + required.getAmount() + " mB " + required.getDisplayName().getString();
        lines.add(fluidLine);
        colors.add(IClientFluidTypeExtensions.of(required.getFluid()).getTintColor() | 0xFF000000);
        maxW = Math.max(maxW, font.width(fluidLine));

        // Входной предмет
        ItemStack requiredItem = recipe.getItemInput();
        String itemLine = requiredItem.getHoverName().getString() + ": "
                + input.getCount() + "/" + requiredItem.getCount();
        lines.add(itemLine);
        colors.add(0xFFFF00); // Yellow
        maxW = Math.max(maxW, font.width(itemLine));

        // Выходы
        for (ItemStack out : recipe.getItemOutputs()) {
            int outCur = 0;
            for (int i = VishelashivatelBlockEntity.FIRST_OUTPUT_SLOT;
                 i < VishelashivatelBlockEntity.FIRST_OUTPUT_SLOT + VishelashivatelBlockEntity.OUTPUT_SLOTS; i++) {
                ItemStack slot = be.getInventory().getStackInSlot(i);
                if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, out)) {
                    outCur += slot.getCount();
                }
            }
            String line = out.getHoverName().getString() + " ("
                    + Component.translatable("hud.trd.leacher.output").getString() + "): " + outCur;
            lines.add(line);
            colors.add(0xFFFF00); // Yellow
            maxW = Math.max(maxW, font.width(line));
        }

        // Прогресс
        int maxProgress = recipe.getProcessTime();
        if (maxProgress > 0) {
            double percent = (double) be.getProgress() / maxProgress;
            int totalBars = 20;
            int greenBars = (int) (percent * totalBars);
            int grayBars = totalBars - greenBars;

            StringBuilder bar = new StringBuilder();
            bar.append("§a");
            for (int i = 0; i < greenBars; i++) bar.append("|");
            bar.append("§7");
            for (int i = 0; i < grayBars; i++) bar.append("|");

            String progressText = Component.translatable("hud.trd.leacher.progress", (int) (percent * 100)).getString();
            String p = progressText + " [" + bar.toString() + "§r]";

            lines.add(p);
            colors.add(0xFFFFFF);
            maxW = Math.max(maxW, font.width(p));
        }

        // Скорость вращения
        long curSpeed = Math.abs(be.getSpeed());
        long reqSpeed = recipe.getMinRpm();
        String s = "RPM: " + curSpeed + "/" + reqSpeed;
        lines.add(s);
        colors.add(curSpeed >= reqSpeed ? 0x00FF00 : 0xFF0000);
        maxW = Math.max(maxW, font.width(s));

        int lh = font.lineHeight + 2;
        int th = lines.size() * lh;
        if (x + maxW + 4 > screenW) x = screenW / 2 - maxW - 12;

        gui.fill(x - 3, y - 2, x + maxW + 3, y + th + 2, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            gui.drawString(font, lines.get(i), x, y + i * lh, colors.get(i), true);
        }
    }
}
