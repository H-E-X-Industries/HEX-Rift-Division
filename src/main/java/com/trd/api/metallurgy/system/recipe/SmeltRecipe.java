package com.trd.api.metallurgy.system.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.trd.api.metallurgy.system.Metal;
import com.trd.api.metallurgy.system.MetallurgyRegistry;
import com.trd.api.recipe.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

public class SmeltRecipe implements Recipe<SingleRecipeInput> {
    private final Ingredient ingredient;
    private final int inputCount;
    private final ResourceLocation metalId;
    private final int outputUnits;
    private final int minTemp;
    private final float heatConsumption;
    private final int smeltTimeTicks;

    public SmeltRecipe(Ingredient ingredient, int inputCount, ResourceLocation metalId,
                       int outputUnits, int minTemp, float heatConsumption, int smeltTimeTicks) {
        this.ingredient = ingredient;
        this.inputCount = inputCount > 0 ? inputCount : 1;
        this.metalId = metalId;
        this.outputUnits = outputUnits;
        this.minTemp = minTemp;
        this.heatConsumption = heatConsumption;
        this.smeltTimeTicks = smeltTimeTicks;
    }

    public Ingredient getIngredient() { return ingredient; }
    public int getInputCount() { return inputCount; }
    public ResourceLocation getMetalId() { return metalId; }
    public int getOutputUnits() { return outputUnits; }

    public int getRawMinTemp() { return minTemp; }
    public float getRawHeatConsumption() { return heatConsumption; }
    public int getRawSmeltTimeTicks() { return smeltTimeTicks; }

    public int getMinTemp() {
        if (minTemp > 0) return minTemp;
        Metal metal = getMetal();
        return metal != null ? metal.getMeltingPoint() : 1000;
    }

    public float getHeatConsumption() {
        if (heatConsumption > 0f) return heatConsumption;
        Metal metal = getMetal();
        return metal != null ? metal.getHeatConsumptionPerTick() : 0.8f;
    }

    public int getSmeltTimeTicks() {
        if (smeltTimeTicks > 0) return smeltTimeTicks;
        Metal metal = getMetal();
        return metal != null ? metal.calculateSmeltTimeForUnits(outputUnits) : 60;
    }

    public Metal getMetal() {
        return MetallurgyRegistry.get(metalId).orElse(null);
    }

    public float getTotalHeatConsumption() {
        return getHeatConsumption() * getSmeltTimeTicks();
    }

    public float heatConsumption() { return getHeatConsumption(); }
    public int minTemp() { return getMinTemp(); }
    public int outputUnits() { return getOutputUnits(); }
    public int inputCount() { return getInputCount(); }
    public Metal output() { return getMetal(); }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        ItemStack stack = input.item();
        if (stack.isEmpty() || stack.getCount() < inputCount) return false;
        return this.ingredient.test(stack);
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(this.ingredient);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.SMELTER_MELTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.SMELTER_MELTING_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<SmeltRecipe> {
        public static final MapCodec<SmeltRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(SmeltRecipe::getIngredient),
                Codec.INT.optionalFieldOf("count").forGetter(r -> java.util.Optional.of(r.getInputCount())),
                Codec.INT.optionalFieldOf("input_count").forGetter(r -> java.util.Optional.of(r.getInputCount())),
                ResourceLocation.CODEC.fieldOf("metal").forGetter(SmeltRecipe::getMetalId),
                Codec.INT.fieldOf("units").forGetter(SmeltRecipe::getOutputUnits),
                Codec.INT.optionalFieldOf("min_temp", -1).forGetter(SmeltRecipe::getRawMinTemp),
                Codec.FLOAT.optionalFieldOf("heat_consumption", -1.0f).forGetter(SmeltRecipe::getRawHeatConsumption),
                Codec.INT.optionalFieldOf("smelt_time", -1).forGetter(SmeltRecipe::getRawSmeltTimeTicks)
        ).apply(inst, (ing, c1, c2, metal, units, minT, heatC, sTime) -> {
            int count = c1.orElse(c2.orElse(1));
            return new SmeltRecipe(ing, count, metal, units, minT, heatC, sTime);
        }));

        public static final StreamCodec<RegistryFriendlyByteBuf, SmeltRecipe> STREAM_CODEC = StreamCodec.of(
                (buf, recipe) -> {
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.getIngredient());
                    buf.writeInt(recipe.getInputCount());
                    ResourceLocation.STREAM_CODEC.encode(buf, recipe.getMetalId());
                    buf.writeInt(recipe.getOutputUnits());
                    buf.writeInt(recipe.getRawMinTemp());
                    buf.writeFloat(recipe.getRawHeatConsumption());
                    buf.writeInt(recipe.getRawSmeltTimeTicks());
                },
                buf -> {
                    Ingredient ing = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
                    int count = buf.readInt();
                    ResourceLocation metal = ResourceLocation.STREAM_CODEC.decode(buf);
                    int units = buf.readInt();
                    int minTemp = buf.readInt();
                    float heat = buf.readFloat();
                    int time = buf.readInt();
                    return new SmeltRecipe(ing, count, metal, units, minTemp, heat, time);
                }
        );

        @Override
        public MapCodec<SmeltRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SmeltRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
