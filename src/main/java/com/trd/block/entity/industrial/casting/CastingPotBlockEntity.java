package com.trd.block.entity.industrial.casting;

import com.trd.api.metallurgy.system.IMetalReceiver;
import com.trd.api.metallurgy.system.Metal;
import com.trd.api.metallurgy.system.MetallurgyRegistry;
import com.trd.api.metallurgy.system.MetalUnits2;
import com.trd.api.metallurgy.system.recipe.MoldRecipe;
import com.trd.api.metallurgy.system.recipe.MoldRecipeRegistry;
import com.trd.block.basic.industrial.casting.CastingPotBlock;
import com.trd.block.entity.ModBlockEntities;
import com.trd.event.HotItemHandler;
import com.trd.event.SlagItem;
import com.trd.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CastingPotBlockEntity extends BlockEntity implements IMetalReceiver {
    public static final int CAPACITY_MOLD_INGOT = MetalUnits2.UNITS_PER_INGOT; // 9
    public static final int CAPACITY_MOLD_NUGGET = MetalUnits2.UNITS_PER_NUGGET; // 1
    public static final int CAPACITY_MOLD_BLOCK = MetalUnits2.UNITS_PER_BLOCK; // 81

    private ItemStack mold = ItemStack.EMPTY;
    private ItemStack outputItem = ItemStack.EMPTY;

    private Metal currentMetal = null;
    private int storedUnits = 0;
    private int capacity = 0;

    private float coolingTimer = 0;
    private int solidifyTimer = 0;
    private static final int SOLIDIFY_TIME = 100;
    public static final float POT_COOLING_TIME = HotItemHandler.BASE_COOLING_TIME_POT;

    private Metal blockRenderMetal = null;

    private static final int SLAG_FORMATION_TIME = 80;
    private int metalIdleTime = 0;
    private boolean isSlagged = false;
    private CompoundTag slagData = null;
    private int transferCooldown = 0;

    public CastingPotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CASTING_POT.get(), pos, state);
    }

    private final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (isSlagged && slagData != null) {
                return SlagItem.createSlagFromNBT(slagData);
            }
            return outputItem;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            outputItem = stack;
            setChanged();
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (isSlagged && slagData != null) {
                if (slagData.contains("HotTime") && slagData.getFloat("HotTime") > 0.5f) {
                    return ItemStack.EMPTY;
                }
                if (!simulate) {
                    ItemStack slag = SlagItem.createSlagFromNBT(slagData);
                    clearSlag();
                    setChanged();
                    if (level != null && !level.isClientSide) {
                        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                    }
                    return slag;
                }
                return SlagItem.createSlagFromNBT(slagData);
            }

            if (outputItem.isEmpty()) return ItemStack.EMPTY;
            if (HotItemHandler.isHot(outputItem) && HotItemHandler.getHeatRatio(outputItem) > 0.05f) {
                return ItemStack.EMPTY;
            }

            if (!simulate) {
                ItemStack drop = outputItem.copy();
                outputItem = ItemStack.EMPTY;
                coolingTimer = 0;
                setChanged();
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
                return drop;
            }
            return outputItem.copy();
        }
    };

    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    public boolean isBlockMold() {
        return mold.is(ModItems.MOLD_BLOCK.get());
    }

    public Metal getBlockRenderMetal() {
        return blockRenderMetal != null ? blockRenderMetal : currentMetal;
    }

    public void updateCapacity() {
        if (mold.isEmpty()) {
            this.capacity = 0;
            return;
        }
        MoldRecipe recipe = MoldRecipeRegistry.getRecipe(mold.getItem());
        this.capacity = recipe != null ? recipe.getRequiredUnits() : 0;
    }

    public boolean canAcceptMetal(Metal metal) {
        if (isSlagged || !outputItem.isEmpty() || coolingTimer > 0.5f || mold.isEmpty()) return false;
        updateCapacity();
        if (capacity <= 0 || storedUnits >= capacity) return false;
        if (storedUnits == 0) return true;
        return currentMetal != null && currentMetal.getId().equals(metal.getId());
    }

    public int addMetal(Metal metal, int amount) {
        if (isSlagged || coolingTimer > 0.5f || !outputItem.isEmpty() || mold.isEmpty()) return 0;
        updateCapacity();
        if (storedUnits == 0) {
            this.currentMetal = metal;
        } else if (!this.currentMetal.getId().equals(metal.getId())) {
            return 0;
        }
        int toAdd = Math.min(amount, this.capacity - this.storedUnits);
        if (toAdd > 0) {
            this.storedUnits += toAdd;
            this.metalIdleTime = 0;
            setChanged();
            if (this.level != null && !this.level.isClientSide) {
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
        return toAdd;
    }

    public int getRemainingCapacity() {
        updateCapacity();
        if (!outputItem.isEmpty() || coolingTimer > 0.5f || isSlagged || mold.isEmpty()) return 0;
        return Math.max(0, capacity - storedUnits);
    }

    public Metal getCurrentMetal() { return currentMetal; }

    public float getFillLevel() {
        if (capacity <= 0) return 0f;
        return Math.min(1.0f, (float) storedUnits / capacity);
    }

    private void formSlag() {
        if (storedUnits <= 0 || currentMetal == null) return;

        isSlagged = true;
        slagData = new CompoundTag();
        slagData.putString(SlagItem.TAG_METAL_ID, currentMetal.getId().toString());
        slagData.putInt(SlagItem.TAG_AMOUNT, storedUnits);
        slagData.putInt(SlagItem.TAG_MELTING_POINT, currentMetal.getMeltingPoint());
        slagData.putInt(SlagItem.TAG_COLOR, currentMetal.getColor());
        slagData.putFloat(SlagItem.TAG_HEAT_CONSUMPTION, currentMetal.getHeatConsumptionPerTick());

        int maxTime = HotItemHandler.BASE_COOLING_TIME_POT;
        slagData.putFloat("HotTime", (float) maxTime);
        slagData.putInt("HotTimeMax", maxTime);
        slagData.putBoolean("CooledInPot", true);

        storedUnits = 0;
        currentMetal = null;
        metalIdleTime = 0;

        if (level != null && !level.isClientSide) {
            level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.5f);
            ((ServerLevel) level).sendParticles(ParticleTypes.ASH,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                    10, 0.3, 0.1, 0.3, 0.02);
        }

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public ItemStack takeOutput() {
        if (outputItem.isEmpty()) return ItemStack.EMPTY;

        ItemStack result = outputItem.copy();
        this.outputItem = ItemStack.EMPTY;
        this.coolingTimer = 0;

        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return result;
    }

    public boolean tryInsertHotItem(ItemStack stack) {
        if (!outputItem.isEmpty() || storedUnits > 0 || mold.isEmpty() || coolingTimer > 0.5f) {
            return false;
        }
        if (!HotItemHandler.isHot(stack)) return false;

        this.outputItem = stack.copy();
        this.outputItem.setCount(1);
        HotItemHandler.setHot(this.outputItem, HotItemHandler.getMeltingPoint(stack), true);
        this.coolingTimer = HotItemHandler.BASE_COOLING_TIME_POT;

        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CastingPotBlockEntity be) {
        if (be.transferCooldown > 0) be.transferCooldown--;

        boolean hasMetal = be.storedUnits > 0;
        if (state.getValue(CastingPotBlock.HAS_METAL) != hasMetal && !level.isClientSide) {
            level.setBlock(pos, state.setValue(CastingPotBlock.HAS_METAL, hasMetal), 3);
            return;
        }

        if (!be.outputItem.isEmpty()) {
            if (HotItemHandler.isHot(be.outputItem)) {
                float hotTime = HotItemHandler.getHotTime(be.outputItem);
                int maxTime = HotItemHandler.getHotTimeMax(be.outputItem);

                if (maxTime > 0 && hotTime > 0) {
                    float heatRatio = hotTime / (float) maxTime;
                    float baseRate = (float) maxTime / 8000f;
                    if (baseRate < 0.08f) baseRate = 0.08f;

                    float quadraticMultiplier = 1.0f + (HotItemHandler.QUADRATIC_FACTOR * heatRatio * heatRatio);
                    float coolingRate = baseRate * quadraticMultiplier;
                    if (coolingRate < 0.03f) coolingRate = 0.03f;

                    float newHotTime = Math.max(0, hotTime - coolingRate);

                    if (newHotTime <= 0.5f) {
                        HotItemHandler.clearHotTags(be.outputItem);
                        be.coolingTimer = 0;
                    } else {
                        final float finalTime = newHotTime;
                        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, be.outputItem, tag -> tag.putFloat("HotTime", finalTime));
                        be.coolingTimer = newHotTime;
                    }
                }

                if ((int) be.coolingTimer % 20 == 0 || be.coolingTimer < 1.0f) {
                    be.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            } else {
                if (be.coolingTimer > 0) {
                    be.coolingTimer = 0;
                    be.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                    level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 2.0f);
                }
            }
            return;
        }

        if (be.isSlagged) {
            be.updateSlagCooling();
            return;
        }

        if (be.storedUnits > 0 && be.storedUnits < be.capacity && !be.isSlagged) {
            boolean transferred = be.tryTransferToNeighbors();
            if (!transferred) {
                be.metalIdleTime++;
                if (be.metalIdleTime >= SLAG_FORMATION_TIME) {
                    be.formSlag();
                    return;
                }
            } else {
                be.metalIdleTime = 0;
            }
        } else {
            be.metalIdleTime = 0;
        }

        if (be.mold.isEmpty()) {
            if (be.storedUnits > 0) be.clearMetal();
            return;
        }

        be.updateCapacity();

        if (be.storedUnits >= be.capacity && be.capacity > 0 && be.transferCooldown <= 0) {
            if (be.solidifyTimer < SOLIDIFY_TIME) {
                be.solidifyTimer++;
                if (be.solidifyTimer % 10 == 0) {
                    be.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            } else {
                be.createOutputItem();
                if (!level.isClientSide) {
                    ((ServerLevel) level).sendParticles(ParticleTypes.POOF,
                            pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                            8, 0.25, 0.1, 0.25, 0.03);
                    level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 2.6f);
                }
            }
        } else {
            if (be.solidifyTimer > 0) {
                be.solidifyTimer = 0;
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    private void createOutputItem() {
        if (currentMetal == null || mold.isEmpty()) return;

        MoldRecipe recipe = MoldRecipeRegistry.getRecipe(mold.getItem());
        if (recipe != null) {
            ItemStack output = recipe.createOutput(currentMetal);
            if (!output.isEmpty()) {
                HotItemHandler.setHot(output, currentMetal.getMeltingPoint(), true);
                this.outputItem = output;
                this.coolingTimer = POT_COOLING_TIME;
                this.blockRenderMetal = this.currentMetal;
                this.storedUnits = 0;
                this.currentMetal = null;
                this.solidifyTimer = 0;
                this.metalIdleTime = 0;
                setChanged();
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }
        }
    }

    private boolean tryTransferToNeighbors() {
        if (this.storedUnits <= 0 || this.currentMetal == null) return false;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(dir));
            if (neighbor instanceof CastingPotBlockEntity neighborPot) {
                if (neighborPot.canAcceptMetal(this.currentMetal) && neighborPot.getRemainingCapacity() > 0) {
                    int toTransfer = Math.min(10, this.storedUnits);
                    int accepted = neighborPot.addMetal(this.currentMetal, toTransfer);
                    if (accepted > 0) {
                        this.storedUnits -= accepted;
                        this.transferCooldown = 2;
                        if (this.storedUnits <= 0) this.currentMetal = null;
                        this.setChanged();
                        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void updateSlagCooling() {
        if (!isSlagged || slagData == null) return;

        if (slagData.contains("HotTime")) {
            float hotTime = slagData.getFloat("HotTime");
            int maxTime = slagData.getInt("HotTimeMax");

            if (maxTime > 0 && hotTime > 0.5f) {
                float heatRatio = hotTime / (float) maxTime;
                float baseRate = (float) maxTime / 8000f;
                if (baseRate < 0.08f) baseRate = 0.08f;

                float quadraticMultiplier = 1.0f + (HotItemHandler.QUADRATIC_FACTOR * heatRatio * heatRatio);
                float coolingRate = baseRate * quadraticMultiplier;

                float newHotTime = Math.max(0, hotTime - coolingRate);
                if (newHotTime <= 0.5f) {
                    slagData.remove("HotTime");
                    slagData.remove("HotTimeMax");
                } else {
                    slagData.putFloat("HotTime", newHotTime);
                }

                setChanged();
                if (level != null && !level.isClientSide && (int) newHotTime % 20 == 0) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }
        }
    }

    public ItemStack extractSlag() {
        if (!isSlagged || slagData == null) return ItemStack.EMPTY;

        ItemStack slag = SlagItem.createSlagFromNBT(slagData);
        clearSlag();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return slag;
    }

    public boolean hasSlag() { return isSlagged; }

    private void clearSlag() {
        isSlagged = false;
        slagData = null;
        metalIdleTime = 0;
    }

    public List<CastingPotBlockEntity> findNetwork() {
        List<CastingPotBlockEntity> network = new ArrayList<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(worldPosition);
        visited.add(worldPosition);

        while (!queue.isEmpty() && network.size() < 7) {
            BlockPos curr = queue.poll();
            if (level.getBlockEntity(curr) instanceof CastingPotBlockEntity pot) {
                network.add(pot);
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos next = curr.relative(d);
                    if (!visited.contains(next) && level.getBlockState(next).is(getBlockState().getBlock())) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return network;
    }

    public void clearMetal() {
        this.storedUnits = 0;
        this.currentMetal = null;
        this.solidifyTimer = 0;
        this.metalIdleTime = 0;
        setChanged();
    }

    public ItemStack getMold() { return mold; }
    public ItemStack getOutputItem() { return outputItem; }
    public int getStoredUnits() { return storedUnits; }
    public int getCapacity() { updateCapacity(); return capacity; }
    public int getSolidifyProgress() { return solidifyTimer; }
    public int getSolidifyTime() { return SOLIDIFY_TIME; }
    public float getCoolingTimer() { return coolingTimer; }

    public boolean canRemoveMold() {
        return storedUnits <= 0 && solidifyTimer <= 0 && outputItem.isEmpty() && coolingTimer <= 0.5f;
    }

    public void setMold(ItemStack stack) {
        this.mold = stack.copy();
        updateCapacity();
        if (mold.isEmpty()) {
            clearMetal();
        }
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public float getCoolingProgress() {
        if (coolingTimer <= 0) return 1.0f;
        return 1.0f - (coolingTimer / POT_COOLING_TIME);
    }

    public boolean isCooling() {
        return coolingTimer > 0.5f && !outputItem.isEmpty();
    }

    public ItemStack getSlagStack() {
        if (isSlagged && slagData != null) {
            return SlagItem.createSlagFromNBT(slagData);
        }
        return ItemStack.EMPTY;
    }

    public ItemStack getSlagStackForRender() {
        return getSlagStack();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!mold.isEmpty()) tag.put("Mold", mold.save(registries));
        if (!outputItem.isEmpty()) tag.put("Output", outputItem.save(registries));
        tag.putInt("StoredUnits", storedUnits);
        tag.putInt("SolidifyTimer", solidifyTimer);
        tag.putFloat("CoolingTimer", coolingTimer);
        tag.putInt("MetalIdleTime", metalIdleTime);
        tag.putBoolean("IsSlagged", isSlagged);

        if (isSlagged && slagData != null) {
            tag.put("SlagData", slagData);
        }

        if (currentMetal != null) {
            tag.putString("MetalId", currentMetal.getId().toString());
        }
        if (blockRenderMetal != null) {
            tag.putString("BlockRenderMetal", blockRenderMetal.getId().toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.mold = tag.contains("Mold") ? ItemStack.parse(registries, tag.getCompound("Mold")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        this.outputItem = tag.contains("Output") ? ItemStack.parse(registries, tag.getCompound("Output")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        this.storedUnits = tag.getInt("StoredUnits");
        this.solidifyTimer = tag.getInt("SolidifyTimer");
        this.coolingTimer = tag.getFloat("CoolingTimer");
        this.metalIdleTime = tag.getInt("MetalIdleTime");
        this.isSlagged = tag.getBoolean("IsSlagged");

        if (tag.contains("SlagData")) {
            this.slagData = tag.getCompound("SlagData");
        } else {
            this.slagData = null;
        }

        if (tag.contains("MetalId")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("MetalId"));
            if (id != null) MetallurgyRegistry.get(id).ifPresent(m -> this.currentMetal = m);
        } else {
            this.currentMetal = null;
        }

        if (tag.contains("BlockRenderMetal")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("BlockRenderMetal"));
            if (id != null) MetallurgyRegistry.get(id).ifPresent(m -> this.blockRenderMetal = m);
        } else {
            this.blockRenderMetal = null;
        }

        updateCapacity();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (!mold.isEmpty()) tag.put("Mold", mold.save(registries));
        if (!outputItem.isEmpty()) tag.put("Output", outputItem.save(registries));
        tag.putInt("StoredUnits", storedUnits);
        tag.putInt("SolidifyTimer", solidifyTimer);
        tag.putFloat("CoolingTimer", coolingTimer);
        tag.putBoolean("IsSlagged", isSlagged);
        if (isSlagged && slagData != null) {
            tag.put("SlagData", slagData);
        }
        tag.putInt("MetalIdleTime", metalIdleTime);

        if (currentMetal != null) {
            tag.putString("MetalId", currentMetal.getId().toString());
        }
        if (blockRenderMetal != null) {
            tag.putString("BlockRenderMetal", blockRenderMetal.getId().toString());
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            loadAdditional(tag, lookupProvider);
        }
    }
}
