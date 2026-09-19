package com.trd.api.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import java.util.List;

public class MillstoneRecipe implements Recipe<SingleRecipeInput> {

    private final Ingredient input;
    private final List<ItemStack> outputs;
    private final int grindsRequired;

    public MillstoneRecipe(Ingredient input, List<ItemStack> outputs, int grindsRequired) {
        this.input = input;
        this.outputs = outputs;
        this.grindsRequired = grindsRequired;
    }

    public Ingredient getInput() {
        return input;
    }

    public List<ItemStack> getOutputs() {
        return outputs;
    }

    public int getGrindsRequired() {
        return grindsRequired;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return this.input.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(this.input);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.MILLSTONE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.MILLSTONE_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<MillstoneRecipe> {
        public static final MapCodec<MillstoneRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(MillstoneRecipe::getInput),
                ItemStack.STRICT_CODEC.listOf().fieldOf("outputs").forGetter(MillstoneRecipe::getOutputs),
                Codec.INT.fieldOf("grinds").forGetter(MillstoneRecipe::getGrindsRequired)
        ).apply(inst, MillstoneRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, MillstoneRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, MillstoneRecipe::getInput,
                ItemStack.LIST_STREAM_CODEC, MillstoneRecipe::getOutputs,
                ByteBufCodecs.INT, MillstoneRecipe::getGrindsRequired,
                MillstoneRecipe::new
        );

        @Override
        public MapCodec<MillstoneRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MillstoneRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
