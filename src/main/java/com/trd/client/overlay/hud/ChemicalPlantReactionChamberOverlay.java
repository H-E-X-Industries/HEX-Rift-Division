package com.trd.client.overlay.hud;

import com.trd.api.chemistry.ChemicalPlantRecipe;
import com.trd.api.chemistry.ChemicalPlantRecipeRegistry;
import com.trd.block.entity.industrial.chemistry.ChemicalPlantReactionChamberBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.trd.main.MainRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChemicalPlantReactionChamberOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof ChemicalPlantReactionChamberBlockEntity chamber)) return;

        renderHUD(event.getGuiGraphics(), chamber, event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight(), mc.font);
    }

    private static void renderHUD(GuiGraphics gui, ChemicalPlantReactionChamberBlockEntity be,
                                  int screenW, int screenH, Font font) {
        int x = screenW / 2 + 12;
        int y = screenH / 2 + 4;

        ResourceLocation recipeId = be.getCurrentRecipeId();
        if (recipeId == null) {
            String txt = Component.translatable("hud.trd.chamber.no_recipe").getString();
            int w = font.width(txt);
            if (x + w + 4 > screenW) x = screenW / 2 - w - 12;
            gui.fill(x - 3, y - 2, x + w + 3, y + font.lineHeight + 2, 0x90000000);
            gui.drawString(font, txt, x, y, 0xAAAAAA, true);
            return;
        }

        ChemicalPlantRecipe recipe = ChemicalPlantRecipeRegistry.getById(recipeId);
        if (recipe == null) return;

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        int maxW = 0;

        // Входы
        for (FluidStack in : recipe.getFluidInputs()) {
            int cur = 0;
            int tankCap = ChemicalPlantReactionChamberBlockEntity.TANK_CAPACITY;
            for (int i = 0; i < ChemicalPlantReactionChamberBlockEntity.TANK_COUNT; i++) {
                FluidStack f = be.getTanks()[i].getFluid();
                if (!f.isEmpty() && f.getFluid() == in.getFluid()) {
                    cur += f.getAmount();
                    tankCap = be.getTanks()[i].getCapacity();
                }
            }
            String arrow = Component.translatable("hud.trd.chamber.arrow_in").getString(); // -->
            String line = arrow + " " + Component.translatable("hud.trd.chamber.input").getString()
                    + " " + cur + "/" + tankCap + " mB " + in.getDisplayName().getString();
            lines.add(line);
            colors.add(IClientFluidTypeExtensions.of(in.getFluid()).getTintColor() | 0xFF000000);
            maxW = Math.max(maxW, font.width(line));
        }

        // Выходы
        for (FluidStack out : recipe.getFluidOutputs()) {
            int cur = 0;
            int tankCap = ChemicalPlantReactionChamberBlockEntity.TANK_CAPACITY;
            for (int i = 0; i < ChemicalPlantReactionChamberBlockEntity.TANK_COUNT; i++) {
                FluidStack f = be.getTanks()[i].getFluid();
                if (!f.isEmpty() && f.getFluid() == out.getFluid()) {
                    cur += f.getAmount();
                    tankCap = be.getTanks()[i].getCapacity();
                }
            }
            String arrow = Component.translatable("hud.trd.chamber.arrow_out").getString(); // <--
            String line = Component.translatable("hud.trd.chamber.output").getString()
                    + " " + arrow + " " + cur + "/" + tankCap + " mB " + out.getDisplayName().getString();
            lines.add(line);
            colors.add(IClientFluidTypeExtensions.of(out.getFluid()).getTintColor() | 0xFF000000);
            maxW = Math.max(maxW, font.width(line));
        }

        // Прогресс
        if (be.getMaxProgress() > 0) {
            String p = Component.translatable("hud.trd.chamber.progress",
                    (int) (be.getProgress() * 100.0 / be.getMaxProgress())).getString();
            lines.add(p);
            colors.add(0xFFFFFF);
            maxW = Math.max(maxW, font.width(p));
        }

        int lh = font.lineHeight + 2;
        int th = lines.size() * lh;
        if (x + maxW + 4 > screenW) x = screenW / 2 - maxW - 12;

        gui.fill(x - 3, y - 2, x + maxW + 3, y + th + 2, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            gui.drawString(font, lines.get(i), x, y + i * lh, colors.get(i), true);
        }
    }
}