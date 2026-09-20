package com.trd.multiblock.industrial.vishelashivatel;

import com.trd.api.vein.FractionLayerMatrix;
import com.trd.api.vein.FractionType;
import com.trd.item.conglomerates.FractionChunkItem;
import com.trd.item.conglomerates.MetalPieceItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Динамическая логика переработки куска фракции в выщелащивателе.
 *
 * <p>Два режима:
 * <ul>
 *   <li><b>Промывка водой</b> ({@code water}): сырой (raw) кусок → очищенный (clean).
 *       OU, слои и флаг анализа сохраняются.</li>
 *   <li><b>Модульная переработка реагентом</b>: чистый/обжаренный кусок + реагент.
 *       Определяет нормальное извлечение, перепрыжок или прокрутку и выдаёт ядро + кусочки металлов.</li>
 * </ul>
 *
 * <p>Модульная логика (см. {@link FractionLayerMatrix}):
 * <ul>
 *   <li><b>Нормальное извлечение</b>: реагент совпадает со следующим (самым мелким) необработанным слоем
 *       → слой извлекается целиком, ядро продвигается дальше.</li>
 *   <li><b>Перепрыжок</b>: реагент глубже самого мелкого необработанного слоя → извлекается только этот
 *       глубокий слой в полном объёме, но все более мелкие необработанные слои сгорают безвозвратно.</li>
 *   <li><b>Прокрутка</b>: реагент не совпадает ни с одним необработанным слоем (уже обработанный/иной)
 *       → весь оставшийся в ядре объём превращается в уценённые кусочки дешёвых металлов,
 *       редкие металлы недополучаются.</li>
 * </ul>
 */
public final class FractionLeachLogic {

    private FractionLeachLogic() {
    }

    /** Жидкости, участвующие в динамической переработке кусков фракций (промывка + реагенты). */
    public static boolean isProcessingFluid(Fluid fluid) {
        if (fluid == null) return false;
        if (fluid == Fluids.WATER) return true;
        return fluid == com.trd.api.fluids.ModFluids.HYDROGEN_PEROXIDE_SOURCE.get()
                || fluid == com.trd.api.fluids.ModFluids.SULFURIC_ACID_SOURCE.get()
                || fluid == com.trd.api.fluids.ModFluids.SODIUM_HYDROXIDE_SOURCE.get()
                || fluid == com.trd.api.fluids.ModFluids.HYDROGEN_CHLORINE_SOURCE.get();
    }

    /** Тип операции переработки (null — рецепт не найден). */
    public enum OpType {
        WASH,       // промывка водой: raw -> clean
        EXTRACT     // модульная переработка реагентом
    }

    /** Описание операции. */
    public static final class Op {
        public final OpType type;
        public final Fluid requiredFluid;
        public final int fluidCost;
        public final int processTime;
        public final long minRpm;
        public final long consumedTorque;

        public Op(OpType type, Fluid requiredFluid, int fluidCost, int processTime, long minRpm, long consumedTorque) {
            this.type = type;
            this.requiredFluid = requiredFluid;
            this.fluidCost = fluidCost;
            this.processTime = processTime;
            this.minRpm = minRpm;
            this.consumedTorque = consumedTorque;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Op op)) return false;
            return type == op.type
                    && fluidCost == op.fluidCost
                    && processTime == op.processTime
                    && minRpm == op.minRpm
                    && consumedTorque == op.consumedTorque
                    && requiredFluid == op.requiredFluid;
        }

        @Override
        public int hashCode() {
            int result = type != null ? type.hashCode() : 0;
            result = 31 * result + (requiredFluid != null ? requiredFluid.hashCode() : 0);
            result = 31 * result + fluidCost;
            result = 31 * result + processTime;
            result = 31 * result + (int) (minRpm ^ (minRpm >>> 32));
            result = 31 * result + (int) (consumedTorque ^ (consumedTorque >>> 32));
            return result;
        }
    }

    private static final int WASH_COST = 100;    // мБ воды
    private static final int WASH_TIME = 60;     // 3 сек
    public static final long WASH_RPM = 100;
    public static final long WASH_TORQUE = 20;

    private static final int LEACH_COST = 250;   // мБ реагента
    private static final int LEACH_TIME = 60;    // 3 сек
    public static final long LEACH_RPM = 160;
    public static final long LEACH_TORQUE = 40;

    /** Коэффициент уценки прокрутки (сколько OU превращается в дешёвые кусочки). */
    private static final float SCROLL_DISCOUNT = 0.5f;

    /**
     * Находит операцию для входного куска фракции и жидкости в баке.
     * {@code null}, если пара «кусок+жидкость» не даёт рецепта.
     */
    @Nullable
    public static Op find(ItemStack input, FluidStack tankFluid) {
        if (input.isEmpty() || !(input.getItem() instanceof FractionChunkItem)) return null;
        if (tankFluid.isEmpty()) return null;

        Fluid fluid = tankFluid.getFluid();
        boolean washed = FractionChunkItem.isWashed(input);
        boolean roasted = FractionChunkItem.isRoasted(input);

        // Промывка водой: непромытый кусок (сырой или уже обжаренный) -> промытый.
        // Промывка сохраняет флаг обжарки (обжаренный кусок остаётся «обжаренный,
        // промытый водой»).
        if (fluid == Fluids.WATER) {
            if (!washed) {
                return new Op(OpType.WASH, fluid, WASH_COST, WASH_TIME, WASH_RPM, WASH_TORQUE);
            }
            return null;
        }

        // Модульная переработка: только промытый кусок (например «обжаренный, промытый водой»)
        if (!washed) return null;
        if (!FractionChunkItem.isAnalyzed(input)) return null;

        FractionType fraction = FractionChunkItem.getFraction(input);
        if (fraction == null) return null;
        FractionLayerMatrix matrix = FractionLayerMatrix.forFraction(fraction);
        if (fluidIndex(matrix, fluid) < 0) return null;

        Map<Integer, Map<String, Integer>> layers = FractionChunkItem.getLayerMap(input);
        if (layers.isEmpty()) return null;

        return new Op(OpType.EXTRACT, fluid, LEACH_COST, LEACH_TIME, LEACH_RPM, LEACH_TORQUE);
    }

    /** Вычисляет выход операции (иначе выдача ядра + кусочков). */
    public static List<ItemStack> computeOutputs(Op op, ItemStack input) {
        List<ItemStack> result = new ArrayList<>();
        if (op == null || input.isEmpty()) return result;

        if (op.type == OpType.WASH) {
            ItemStack clean = input.copy();
            FractionChunkItem.setWashed(clean, true);
            result.add(clean);
            return result;
        }

        // === Модульная переработка ===
        FractionType fraction = FractionChunkItem.getFraction(input);
        if (fraction == null) return result;
        FractionLayerMatrix matrix = FractionLayerMatrix.forFraction(fraction);
        int reagentIndex = fluidIndex(matrix, op.requiredFluid);
        if (reagentIndex < 0) return result;

        Map<Integer, Map<String, Integer>> layers = FractionChunkItem.getLayerMap(input);
        if (layers.isEmpty()) return result;

        int next = layers.keySet().stream().min(Integer::compareTo).orElse(-1);

        // Нормальное извлечение: реагент совпадает с самым мелким необработанным слоем
        if (reagentIndex == next) {
            Map<String, Integer> metals = layers.get(next);
            emitMetalPieces(metals, result);
            ItemStack core = input.copy();
            FractionChunkItem.setProcessedLayers(core, next + 1);
            FractionChunkItem.setWashed(core, true);
            if (!FractionChunkItem.getLayerMap(core).isEmpty()) {
                result.add(core);
            }
            return result;
        }

        // Перепрыжок: реагент глубже самого мелкого необработанного слоя
        if (reagentIndex > next) {
            Map<String, Integer> metals = layers.get(reagentIndex);
            if (metals != null) {
                emitMetalPieces(metals, result);
            }
            ItemStack core = input.copy();
            explodeUpTo(core, reagentIndex);
            FractionChunkItem.setProcessedLayers(core, reagentIndex + 1);
            FractionChunkItem.setWashed(core, true);
            if (!FractionChunkItem.getLayerMap(core).isEmpty()) {
                result.add(core);
            }
            return result;
        }

        // Прокрутка: реагент не совпадает ни с одним необработанным слоем
        // (обычно он мельче самого недоизвлечённого слоя). Вместо недоизвлечённого
        // редкого металла выпадают уценённые кусочки дешёвых металлов в меньшем количестве.
        // Бюджет уценки считается от OU именно следующего (недоизвлечённого) слоя,
        // а не от всей суммы остатка.
        Map<String, Integer> nextLayer = layers.getOrDefault(next, Map.of());
        int nextLayerOu = nextLayer.values().stream().mapToInt(Integer::intValue).sum();
        if (nextLayerOu <= 0) return result;

        // Дешёвые металлы — из уже извлечённых (более мелких) слоёв; веса из запечённого NBT
        Map<String, Integer> weights = cheapWeights(matrix, input, next);
        int budget = Math.max(1, Math.round(nextLayerOu * SCROLL_DISCOUNT));
        Map<String, Integer> outByMetal = com.trd.api.vein.DistributionMath.distribute(budget, weights);
        for (Map.Entry<String, Integer> e : outByMetal.entrySet()) {
            if (e.getValue() > 0) {
                result.add(MetalPieceItem.create(e.getKey()));
            }
        }
        // Ядро истощается полностью (прокрутка съедает остаток)
        return result;
    }

    private static int fluidIndex(FractionLayerMatrix matrix, Fluid fluid) {
        for (int i = 0; i < FractionLayerMatrix.MAX_LAYERS; i++) {
            if (matrix.getLayerFluid(i) == fluid) return i;
        }
        return -1;
    }

    /**
     * Металлы «дешёвой части» фракции: те, что в слоях мельче {@code next}.
     * Используем запечённые веса, если доступны; иначе эталонные.
     */
    private static Map<String, Integer> cheapWeights(FractionLayerMatrix matrix, ItemStack input, int next) {
        Map<String, Integer> result = new LinkedHashMap<>();
        Map<String, Integer> baked = FractionChunkItem.getWeights(input);
        for (FractionLayerMatrix.Layer layer : matrix.getLayers()) {
            if (layer.index() >= next) continue; // берём только обработанные/мелкие слои
            for (FractionLayerMatrix.MetalEntry entry : layer.metals()) {
                int w = baked.getOrDefault(entry.metal(), entry.weight());
                if (w > 0) result.put(entry.metal(), w);
            }
        }
        // Если дешёвых слоёв нет (например реагент мельче всех) — берём все металлы фракции
        if (result.isEmpty()) {
            result = matrix.getAllMetalWeights();
        }
        return result;
    }

    /** Выжигает из ядра все слои с индексом {@code <= upToIndex} (перепрыжок). */
    private static void explodeUpTo(ItemStack core, int upToIndex) {
        var tag = core.getOrCreateTag();
        if (!tag.contains(FractionChunkItem.TAG_LAYERS, net.minecraft.nbt.Tag.TAG_COMPOUND)) return;
        var layersTag = tag.getCompound(FractionChunkItem.TAG_LAYERS);
        for (int i = 0; i <= upToIndex; i++) {
            layersTag.remove(String.valueOf(i));
        }
    }

    private static void emitMetalPieces(Map<String, Integer> metals, List<ItemStack> out) {
        if (metals == null) return;
        for (Map.Entry<String, Integer> e : metals.entrySet()) {
            int count = e.getValue();
            while (count > 0) {
                ItemStack piece = MetalPieceItem.create(e.getKey());
                int stack = Math.min(count, piece.getMaxStackSize());
                piece.setCount(stack);
                out.add(piece);
                count -= stack;
            }
        }
    }
}
