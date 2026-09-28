package com.trd.multiblock.industrial.coccer;

import com.trd.item.conglomerates.FractionChunkItem;
import com.trd.item.conglomerates.MetalPieceItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

/**
 * Логика обжарки в коксовой печи кусков фракций и кусочков металлов.
 * Обжарить можно один раз; за каждый обжаренный кусок выпадает 50 мБ красного шлама.
 */
public final class CoccerRoastLogic {

    private CoccerRoastLogic() {
    }

    public static final int ROAST_TEMP = 500;
    public static final int ROAST_TIME = 100;   // 5 секунд
    public static final int SLUDGE_PER_ROAST = 50; // мБ красного шлама

    /** Можно ли обжарить данный предмет (и не был ли он уже обжарен). */
    public static boolean isRoastable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof MetalPieceItem) {
            return !MetalPieceItem.isRoasted(stack);
        }
        if (stack.getItem() instanceof FractionChunkItem) {
            // Обжарить можно ТОЛЬКО сырой кусок фракции (не промытый водой и не обжаренный).
            // Промытые нельзя — иначе их можно бесконечно обжаривать.
            return FractionChunkItem.isRaw(stack);
        }
        return false;
    }

    /** Предмет, получаемый после обжарки (тот же кусок, но обжаренный). */
    public static ItemStack getRoastedOutput(ItemStack input) {
        if (input.getItem() instanceof MetalPieceItem) {
            ItemStack out = input.copy();
            MetalPieceItem.setRoasted(out, true);
            return out;
        }
        if (input.getItem() instanceof FractionChunkItem) {
            ItemStack out = input.copy();
            FractionChunkItem.setRoasted(out, true);
            return out;
        }
        return ItemStack.EMPTY;
    }

    /** Красный шлам, выпадающий при обжарке (или пусто). */
    public static FluidStack getSludge() {
        return new FluidStack(com.trd.api.fluids.ModFluids.RED_SLUDGE_SOURCE.get(), SLUDGE_PER_ROAST);
    }
}
