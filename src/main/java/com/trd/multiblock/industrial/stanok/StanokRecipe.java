package com.trd.multiblock.industrial.stanok;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Описывает один рецепт для станка (stanok).
 * Каждый рецепт привязан к типу насадки (CarriageType),
 * требует определённой скорости (rpm) и потребляет определённый момент (Nm).
 */
public class StanokRecipe {

    private final ResourceLocation id;
    private final CarriageType carriageType;
    private final List<ItemStack> inputs;
    private final List<ItemStack> outputs;

    /** Целевая скорость в RPM (допуск ±25%) */
    private final long requiredRpm;

    /** Потребляемый крутящий момент в Нм */
    private final long consumedTorque;

    /**
     * Длительность одной операции в тиках (20 тиков = 1 секунда).
     * Для пресса это начальное значение; реальная скорость регулируется логикой разгона.
     */
    private final int processTicks;

    public StanokRecipe(ResourceLocation id, CarriageType carriageType,
                        List<ItemStack> inputs, List<ItemStack> outputs,
                        long requiredRpm, long consumedTorque, int processTicks) {
        this.id = id;
        this.carriageType = carriageType;
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.requiredRpm = requiredRpm;
        this.consumedTorque = consumedTorque;
        this.processTicks = processTicks;
    }

    public ResourceLocation getId() { return id; }
    public CarriageType getCarriageType() { return carriageType; }
    public List<ItemStack> getInputs() { return inputs; }
    public List<ItemStack> getOutputs() { return outputs; }
    public long getRequiredRpm() { return requiredRpm; }
    public long getConsumedTorque() { return consumedTorque; }
    public int getProcessTicks() { return processTicks; }
}
