package com.trd.item.energy;

import com.trd.api.energy.ModRecipes;
import com.trd.item.ModItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Намотка катушки провода: катушка + N слотов с промышленными медными проводами
 * -> та же катушка с +N проводами (максимум {@link WireCoilItem#MAX_WIRES}).
 *
 * Кастомный рецепт: учитывается только количество СЛОТОВ с проводом, а не
 * количество предметов в слоте. Ваниль при заборе результата списывает ровно
 * по 1 предмету с каждого занятого слота, поэтому N занятых слотов дают
 * ровно +N проводов и расходуют ровно N проводов — накрутить полную катушку
 * пачками по 64 нельзя.
 */
public class WireCoilWindingRecipe implements CraftingRecipe {

    private final ResourceLocation id;

    public WireCoilWindingRecipe(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        int coils = 0;
        int wireSlots = 0;
        for (ItemStack stack : inv.getItems()) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof WireCoilItem) {
                coils++;
            } else if (stack.is(ModItems.INDUSTRIAL_COPPER_WIRE.get())) {
                // Важен только сам факт занятого слота, а не размер стака
                wireSlots++;
            } else {
                return false; // посторонний предмет — рецепт не подходит
            }
        }
        return coils == 1 && wireSlots >= 1;
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess registryAccess) {
        ItemStack coil = ItemStack.EMPTY;
        int wireSlots = 0;
        for (ItemStack stack : inv.getItems()) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof WireCoilItem) {
                coil = stack.copy();
                coil.setCount(1);
            } else {
                // Каждый занятый слот с проводом даёт ровно 1 провод
                // (ваниль списывает со слота ровно 1 предмет)
                wireSlots++;
            }
        }
        if (coil.isEmpty()) return ItemStack.EMPTY;

        int total = Math.min(WireCoilItem.MAX_WIRES, WireCoilItem.getWires(coil) + wireSlots);
        WireCoilItem.setWires(coil, total);
        return coil;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        // Для превью показываем полностью заряженную катушку
        ItemStack preview = new ItemStack(ModItems.WIRE_COIL.get());
        WireCoilItem.setWires(preview, WireCoilItem.MAX_WIRES);
        return preview;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.WIRE_COIL_WINDING.get();
    }

    @Override
    public net.minecraft.world.item.crafting.RecipeType<?> getType() {
        return net.minecraft.world.item.crafting.RecipeType.CRAFTING;
    }

    @Override
    public net.minecraft.world.item.crafting.CraftingBookCategory category() {
        return net.minecraft.world.item.crafting.CraftingBookCategory.MISC;
    }

    public static class Serializer implements RecipeSerializer<WireCoilWindingRecipe> {
        @Override
        public WireCoilWindingRecipe fromJson(ResourceLocation id, com.google.gson.JsonObject json) {
            return new WireCoilWindingRecipe(id);
        }

        @Nullable
        @Override
        public WireCoilWindingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new WireCoilWindingRecipe(id);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, WireCoilWindingRecipe recipe) {
            // Данных у рецепта нет
        }
    }
}
