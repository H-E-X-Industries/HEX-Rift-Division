package com.trd.block.entity.industrial.rotation;

import com.trd.block.entity.ModBlockEntities;
import com.trd.item.ModItems;
import com.trd.api.recipe.MillstoneRecipe;
import com.trd.api.recipe.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MillstoneBlockEntity extends BlockEntity {

    public static final int GRIND_COOLDOWN = 10;
    private static final float ROTATION_SPEED = 20.0f;

    public final ItemStackHandler itemHandler = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == 0) return true;
            return false;
        }
    };

    private int currentGrinds = 0;
    private int requiredGrinds = 0;
    private boolean isProcessing = false;

    private int cooldownTicks = 0;
    private float rotationAngle = 0.0f;
    private boolean isGrinding = false;

    public MillstoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MILLSTONE.get(), pos, state);
    }

    public IItemHandler getItemHandler(@Nullable Direction side) {
        return itemHandler;
    }

    public ItemStack getInputStack() { return itemHandler.getStackInSlot(0); }

    public List<ItemStack> getResultStacks() {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 1; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) list.add(stack);
        }
        return list;
    }

    public boolean isOutputEmpty() {
        for (int i = 1; i < itemHandler.getSlots(); i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }

    public boolean isProcessing() { return isProcessing; }
    public int getCurrentGrinds() { return currentGrinds; }
    public int getRequiredGrinds() { return requiredGrinds; }
    public int getRemainingGrinds() { return Math.max(0, requiredGrinds - currentGrinds); }
    public boolean canGrind() { return isProcessing && getRemainingGrinds() > 0 && cooldownTicks <= 0; }

    public float getRotationAngle() { return rotationAngle; }
    public boolean isGrinding() { return isGrinding; }
    public int getCooldownProgress() {
        return cooldownTicks > 0 ? (GRIND_COOLDOWN - cooldownTicks) : GRIND_COOLDOWN;
    }

    public int getComparatorSignal() {
        for (int i = 1; i < itemHandler.getSlots(); i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) return 15;
        }
        if (isProcessing && requiredGrinds > 0) {
            return 1 + (int)((14.0f * currentGrinds) / requiredGrinds);
        }
        return 0;
    }

    private Optional<RecipeHolder<MillstoneRecipe>> getCurrentRecipe() {
        if (level == null) return Optional.empty();
        ItemStack stack = itemHandler.getStackInSlot(0);
        if (stack.isEmpty()) return Optional.empty();
        return level.getRecipeManager().getRecipeFor(ModRecipes.MILLSTONE_TYPE.get(), new SingleRecipeInput(stack), level);
    }

    public ItemStack insertItem(ItemStack stack) {
        if (level == null) return stack;
        
        Optional<RecipeHolder<MillstoneRecipe>> recipe = level.getRecipeManager().getRecipeFor(ModRecipes.MILLSTONE_TYPE.get(), new SingleRecipeInput(stack), level);
        if (recipe.isEmpty()) return stack;
        if (!itemHandler.getStackInSlot(0).isEmpty() || !isOutputEmpty()) {
            return stack;
        }

        ItemStack single = stack.split(1);
        itemHandler.setStackInSlot(0, single);

        currentGrinds = 0;
        requiredGrinds = recipe.get().value().getGrindsRequired();
        isProcessing = true;
        cooldownTicks = 0;
        rotationAngle = 0;

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

        return stack;
    }

    public boolean doGrind() {
        if (!canGrind()) return false;

        cooldownTicks = GRIND_COOLDOWN;
        isGrinding = true;
        currentGrinds++;

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    private void finishGrind() {
        Optional<RecipeHolder<MillstoneRecipe>> recipeOpt = getCurrentRecipe();

        if (recipeOpt.isPresent()) {
            itemHandler.setStackInSlot(0, ItemStack.EMPTY);
            List<ItemStack> outputs = recipeOpt.get().value().getOutputs();
            for (int i = 0; i < outputs.size() && i < itemHandler.getSlots() - 1; i++) {
                itemHandler.setStackInSlot(i + 1, outputs.get(i).copy());
            }
        }

        isProcessing = false;
        isGrinding = false;
        currentGrinds = 0;
        requiredGrinds = 0;
        cooldownTicks = 0;
    }

    public ItemStack extractResult() {
        for (int i = 1; i < itemHandler.getSlots(); i++) {
            ItemStack result = itemHandler.getStackInSlot(i);
            if (!result.isEmpty()) {
                itemHandler.setStackInSlot(i, ItemStack.EMPTY);
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    public List<ItemStack> extractAllResults() {
        List<ItemStack> results = new ArrayList<>();
        for (int i = 1; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                results.add(stack);
                itemHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        if (!results.isEmpty()) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return results;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MillstoneBlockEntity be) {
        if (!level.isClientSide) {
            if (!be.isProcessing && !be.itemHandler.getStackInSlot(0).isEmpty() && be.isOutputEmpty()) {
                Optional<RecipeHolder<MillstoneRecipe>> recipeOpt = be.getCurrentRecipe();
                if (recipeOpt.isPresent()) {
                    be.currentGrinds = 0;
                    be.requiredGrinds = recipeOpt.get().value().getGrindsRequired();
                    be.isProcessing = true;
                    be.cooldownTicks = 0;
                    be.rotationAngle = 0;
                    be.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            }
        }

        if (be.cooldownTicks > 0) {
            be.cooldownTicks--;
            if (be.isGrinding) {
                be.rotationAngle += ROTATION_SPEED;
                if (be.rotationAngle >= 360.0f) {
                    be.rotationAngle -= 360.0f;
                }
            }
            if (be.cooldownTicks <= 0) {
                be.isGrinding = false;
                if (!level.isClientSide && be.isProcessing && be.currentGrinds >= be.requiredGrinds && be.requiredGrinds > 0) {
                    be.finishGrind();
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            }
            be.setChanged();
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        itemHandler.deserializeNBT(registries, tag.getCompound("Inventory"));
        currentGrinds = tag.getInt("CurrentGrinds");
        requiredGrinds = tag.getInt("RequiredGrinds");
        isProcessing = tag.getBoolean("IsProcessing");
        cooldownTicks = tag.getInt("CooldownTicks");
        rotationAngle = tag.getFloat("RotationAngle");
        isGrinding = tag.getBoolean("IsGrinding");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", itemHandler.serializeNBT(registries));
        tag.putInt("CurrentGrinds", currentGrinds);
        tag.putInt("RequiredGrinds", requiredGrinds);
        tag.putBoolean("IsProcessing", isProcessing);
        tag.putInt("CooldownTicks", cooldownTicks);
        tag.putFloat("RotationAngle", rotationAngle);
        tag.putBoolean("IsGrinding", isGrinding);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void dropContents() {
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), itemHandler.getStackInSlot(i));
        }
    }
}
