package com.trd.multiblock.industrial.smelter;

import com.trd.api.metallurgy.system.ISmelter;
import com.trd.api.metallurgy.system.Metal;
import com.trd.api.metallurgy.system.MetalUnits2;
import com.trd.api.metallurgy.system.MetallurgyRegistry;
import com.trd.api.metallurgy.system.recipe.AlloyRecipe;
import com.trd.api.metallurgy.system.recipe.AlloySlot;
import com.trd.api.metallurgy.system.recipe.SmeltRecipe;
import com.trd.block.entity.ModBlockEntities;
import com.trd.event.HotItemHandler;
import com.trd.event.SlagItem;
import com.trd.menu.industrial.SmelterMenu;
import com.trd.multiblock.industrial.heaters.HeaterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SmelterBlockEntity extends BlockEntity implements MenuProvider, ISmelter {
    public static final int MAX_TEMP = 1600;
    /** Ранний старт плавки: при температуре печи от этой доли точки плавления процесс уже идёт */
    public static final float EARLY_MELT_RATIO = 0.85f;
    /** Скорость плавки при пороге EARLY_MELT_RATIO (линейно растёт до 1.0 к точке плавления) */
    public static final float EARLY_MELT_MIN_SPEED = 0.6f;
    public static final int BLOCK_CAPACITY = 4;
    public static final int TANK_CAPACITY = BLOCK_CAPACITY * MetalUnits2.UNITS_PER_BLOCK;
    /** Сколько тиков дым идёт после окончания плавки (~5 секунд) */
    public static final int SMOKE_TAIL_TICKS = 100;
    private static final float BURN_MIN_TEMP = 300.0F;

    /**
     * Скорость плавки при заданной температуре печи. Ниже EARLY_MELT_RATIO печь
     * «не берёт» материал (0), от порога до точки плавления скорость растёт линейно
     * от EARLY_MELT_MIN_SPEED до 1.0, выше точки — полная скорость.
     */
    public static float meltSpeedFactor(float furnaceTemp, int meltingPoint) {
        if (meltingPoint <= 0 || furnaceTemp >= meltingPoint) return 1.0f;
        float ratio = furnaceTemp / meltingPoint;
        if (ratio < EARLY_MELT_RATIO) return 0f;
        float t = (ratio - EARLY_MELT_RATIO) / (1.0f - EARLY_MELT_RATIO);
        return EARLY_MELT_MIN_SPEED + (1.0f - EARLY_MELT_MIN_SPEED) * t;
    }

    private final ItemStackHandler inventory = new ItemStackHandler(8) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (slot < 4) {
                // Верхний ряд — только ингредиенты сплавов; позиция не важна,
                // т.к. матчинг рецептов агрегирует по типу предмета
                for (AlloyRecipe recipe : MetallurgyRegistry.getAllAlloyRecipes()) {
                    for (AlloySlot slotReq : recipe.getSlots()) {
                        if (slotReq.item() != null && slotReq.accepts(stack.getItem())) return true;
                    }
                }
                return false;
            }
            if (stack.getItem() instanceof SlagItem) return true;
            return MetallurgyRegistry.getSmeltRecipe(level, stack) != null;
        }
    };

    @Override
    public BlockEntity asBlockEntity() {
        return this;
    }

    @Override
    public int getSmelterCapacity() {
        return TANK_CAPACITY;
    }

    // === НАГРЕВ ПРЕДМЕТОВ ===
    private final float[] slotTemperatures = new float[8];
    private static final float HEAT_RATE = 5.0f;

    private static class SlagSlotData {
        Metal metal;
        int amount;
        int requiredTemp;
        float heatConsumption;
    }

    private final Map<Metal, Integer> metalTank = new LinkedHashMap<>();
    private int totalMetalAmount = 0;
    private float temperature = 0;
    private int smokeTicks = 0;

    private int lastTopHash = 0;
    private int lastBottomHash = 0;

    // Верхний ряд (сплавы)
    private float topProgress = 0;
    private float topMaxProgress = 0;
    private boolean topSmelting = false;
    private float topHeatConsumption = 0;
    private AlloyRecipe currentAlloyRecipe = null;
    private int requiredTempTop = 0;

    // Нижний ряд (обычная плавка)
    private float bottomProgress = 0;
    private float bottomMaxProgress = 0;
    private boolean bottomSmelting = false;
    private float bottomHeatConsumption = 0;
    private final Map<SmeltRecipe, Float> currentBottomRecipes = new HashMap<>();
    private int requiredTempBottom = 0;

    // Поля для синхронной плавки
    private float sharedBottomProgress = 0;
    private float sharedBottomMaxProgress = 0;
    private boolean allBottomSlotsReady = false;
    private final int[] slotIndividualProgress = new int[4];

    // Синхронная плавка верхнего ряда
    private float sharedTopProgress = 0;
    private float sharedTopMaxProgress = 0;
    private boolean allTopSlotsReady = false;
    private final ItemStack[] previousTopStacks = new ItemStack[4];

    private final InsertOnlyHandler upHandler;
    private final InsertOnlyHandler sideHandler;

    private final ContainerData data = new SimpleContainerData(14) {
        @Override
        public void set(int index, int value) {
            super.set(index, value);
        }
    };

    private static class BottomSlotData {
        SmeltRecipe recipe;
        SlagSlotData slagData;
        float progress;
        float maxProgress;
        float heatConsumption;
        boolean active;
        float itemTemperature;
        int targetTemperature;
    }

    private final BottomSlotData[] bottomSlots = new BottomSlotData[4];
    private final ItemStack[] previousBottomStacks = new ItemStack[4];

    public SmelterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMELTER_BE.get(), pos, state);
        for (int i = 0; i < 4; i++) {
            previousBottomStacks[i] = ItemStack.EMPTY;
            previousTopStacks[i] = ItemStack.EMPTY;
        }
        Arrays.fill(slotTemperatures, 20);
        this.upHandler   = new InsertOnlyHandler(inventory, 0, 1, 2, 3);
        this.sideHandler = new InsertOnlyHandler(inventory, 4, 5, 6, 7);
    }

    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == null) return inventory;
        return switch (side) {
            case UP -> upHandler;
            case DOWN -> null;
            default -> sideHandler;
        };
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmelterBlockEntity be) {
        be.pickupThrownItems(level, pos);
        be.burnEntitiesInRecess(level, pos);

        // Только верхний ряд: слоты сплавов позиционно-эквивалентны, свободное место
        // надо возвращать в каноничный слот. Нижний ряд НЕ мёржим — игрок осознанно
        // распределяет стак по 4 слотам для параллельных партий.
        be.mergeRowStacks(0, 3);

        int currentBottomHash = be.calculateBottomHash();
        if (currentBottomHash != be.lastBottomHash) {
            be.lastBottomHash = currentBottomHash;
            be.resetBottomSmelting();
        }

        // === ТЕПЛООБМЕН ===
        float baseCooling = (be.temperature * be.temperature) / 512000f;
        if (baseCooling < 0.1f && be.temperature > 0) baseCooling = 0.1f;
        int thermalNoise = (be.temperature > 200 && baseCooling > 1) ? level.random.nextInt(5) - 2 : 0;
        float cooling = Math.max(0.1f, baseCooling + thermalNoise);

        BlockEntity below = level.getBlockEntity(pos.below());
        if (below instanceof HeaterBlockEntity heater && heater.getTemperature() > be.temperature) {
            float transfer = (heater.getTemperature() - be.temperature) / 10f + 0.5f;
            be.temperature = Math.min(MAX_TEMP, be.temperature + transfer);
        } else if (be.temperature > 0) {
            be.temperature = Math.max(0, be.temperature - cooling);
        }

        be.processItemHeatExchange();

        be.tickTopRow();
        be.tickBottomRow();

        if (be.topSmelting || be.bottomSmelting) {
            be.smokeTicks = SMOKE_TAIL_TICKS;
        } else if (be.smokeTicks > 0) {
            be.smokeTicks--;
        }

        // Синхронизация данных для GUI
        be.data.set(0, (int) be.temperature);
        be.data.set(1, (int) be.topProgress);
        be.data.set(2, (int) be.topMaxProgress);
        be.data.set(3, be.topSmelting ? 1 : 0);
        be.data.set(4, be.requiredTempTop);
        be.data.set(5, (int) be.bottomProgress);
        be.data.set(6, (int) be.bottomMaxProgress);
        be.data.set(7, be.bottomSmelting ? 1 : 0);
        be.data.set(8, be.requiredTempBottom);
        be.data.set(9, be.currentAlloyRecipe != null ? 1 : 0);
        be.data.set(10, be.bottomSmelting ? 1 : 0);
        be.data.set(11, (int) be.topHeatConsumption);
        be.data.set(12, (int) be.bottomHeatConsumption);
        be.data.set(13, be.isTankFull() ? 1 : 0);

        if (be.topSmelting || be.bottomSmelting || be.temperature > 0 || be.smokeTicks > 0) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private void pickupThrownItems(Level level, BlockPos pos) {
        AABB area = new AABB(pos.above()).inflate(0.125D, 0.0D, 0.125D);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, area);
        for (ItemEntity itemEntity : items) {
            if (itemEntity.getY() > pos.getY() + 1.5D) continue;
            if (Math.abs(itemEntity.getX() - (pos.getX() + 0.5D)) >= 0.625D
                    || Math.abs(itemEntity.getZ() - (pos.getZ() + 0.5D)) >= 0.625D) continue;

            ItemStack remainder = ItemHandlerHelper.insertItemStacked(upHandler, itemEntity.getItem(), false);
            if (remainder.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(remainder);
            }
        }
    }

    private void burnEntitiesInRecess(Level level, BlockPos pos) {
        if (temperature < BURN_MIN_TEMP || level.getGameTime() % 20L != 0L) return;

        AABB area = new AABB(pos.above()).inflate(0.125D, 0.0D, 0.125D);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (living instanceof Player player && (player.isCreative() || player.isSpectator())) continue;
            living.igniteForSeconds(2);
            living.hurt(level.damageSources().inFire(), 2.0F);
        }
    }

    // ==================== ВЕРХНИЙ РЯД (СПЛАВЫ) ====================
    private void tickTopRow() {
        if (totalMetalAmount >= TANK_CAPACITY) {
            if (requiredTempTop != 0) requiredTempTop = 0;
            return;
        }

        ItemStack[] topSlotsStacks = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            topSlotsStacks[i] = inventory.getStackInSlot(i);
        }

        AlloyRecipe recipe = findMatchingAlloyRecipe(topSlotsStacks);
        if (recipe == null) {
            if (topSmelting || currentAlloyRecipe != null) {
                resetTopSmelting();
            }
            return;
        }

        int currentTopHash = calculateTopHash();
        if (currentTopHash != lastTopHash) {
            lastTopHash = currentTopHash;
            if (!recipe.matches(topSlotsStacks)) {
                resetTopSmelting();
                for (int i = 0; i < 4; i++) {
                    previousTopStacks[i] = topSlotsStacks[i].copy();
                }
                return;
            }
        }

        for (int i = 0; i < 4; i++) {
            previousTopStacks[i] = topSlotsStacks[i].copy();
        }

        currentAlloyRecipe = recipe;
        int outputMelt = recipe.getOutputMetal().getMeltingPoint();
        requiredTempTop = outputMelt;

        AlloySlot[] slots = recipe.getSlots();

        // Готовность предметов: номинал — 95% точки плавления, но не выше текущей
        // температуры печи (иначе при "раннем старте" предметы не прогреются никогда)
        float itemReadyTemp = Math.min(outputMelt * 0.95f, temperature);

        boolean allMeltablesHeated = true;

        // Агрегатный матчинг: греем ингредиенты в любом из верхних слотов
        for (int i = 0; i < 4; i++) {
            ItemStack stack = topSlotsStacks[i];
            if (stack.isEmpty()) continue;

            boolean isIngredient = false;
            for (AlloySlot slotReq : slots) {
                if (slotReq.item() != null && slotReq.accepts(stack.getItem())) {
                    isIngredient = true;
                    break;
                }
            }
            // Сюда попадает и "немелкий" ингредиент (уголь для стали) — греется мягко в else
            if (!isIngredient) continue;

            if (isMeltableItem(stack)) {
                float currentItemTemp = getItemTemperature(stack);

                if (currentItemTemp < itemReadyTemp) {
                    allMeltablesHeated = false;
                    if (temperature > currentItemTemp) {
                        float heatNeeded = itemReadyTemp - currentItemTemp;
                        float heatTransfer = Math.min(HEAT_RATE * 2, heatNeeded);
                        heatTransfer = Math.min(heatTransfer, temperature * 0.08f);

                        float newTemp = currentItemTemp + heatTransfer;
                        setItemTemperature(stack, newTemp);
                        slotTemperatures[i] = newTemp;
                        temperature -= heatTransfer * 0.3f;
                    }
                } else {
                    slotTemperatures[i] = currentItemTemp;
                }
            } else {
                slotTemperatures[i] = Math.min(300, slotTemperatures[i] + HEAT_RATE);
            }
        }

        if (sharedTopMaxProgress <= 0) {
            sharedTopMaxProgress = recipe.getTotalHeatConsumption();
        }
        topMaxProgress = (int) sharedTopMaxProgress;
        topHeatConsumption = recipe.heatConsumption();

        // Ранний старт: при ≥85% точки плавления плавка идёт, но медленнее
        float speedFactor = meltSpeedFactor(temperature, outputMelt);
        if (!allMeltablesHeated || speedFactor <= 0f) {
            topSmelting = false;
            allTopSlotsReady = false;
            topProgress = (int) sharedTopProgress;
            return;
        }

        allTopSlotsReady = true;
        topSmelting = true;

        float availableHeat = Math.min(topHeatConsumption, temperature);
        float heatToApply = Math.min(availableHeat, sharedTopMaxProgress - sharedTopProgress) * speedFactor;

        sharedTopProgress += heatToApply;
        temperature = Math.max(0, temperature - heatToApply);
        topProgress = (int) sharedTopProgress;

        if (sharedTopProgress >= sharedTopMaxProgress * 0.999f) {
            completeAlloyRecipeSynchronized(recipe);
            resetTopSmelting();
        }
    }

    /**
     * Слияние стопок в ряду слотов [from, to]. Сравнение игнорирует тепловые теги
     * (HotTime и др.), поэтому нагретый стак принимает свежие партии того же предмета.
     * При слиянии сохраняется более горячее состояние партии.
     */
    private void mergeRowStacks(int from, int to) {
        for (int i = from; i <= to; i++) {
            ItemStack source = inventory.getStackInSlot(i);
            if (source.isEmpty()) continue;

            for (int j = i + 1; j <= to; j++) {
                ItemStack target = inventory.getStackInSlot(j);
                if (target.isEmpty()) continue;

                if (areItemsSameIgnoreHeat(source, target)) {
                    // Смешиваем тепло пропорционально массе: докинутые предметы
                    // не наследуют жар партии бесплатно
                    int spaceInSource = Math.min(source.getMaxStackSize(), inventory.getSlotLimit(i)) - source.getCount();
                    int toMove = Math.min(spaceInSource, target.getCount());
                    if (toMove > 0) {
                        blendHeat(source, target, toMove);
                        source.grow(toMove);
                        target.shrink(toMove);
                        if (target.isEmpty()) {
                            inventory.setStackInSlot(j, ItemStack.EMPTY);
                            slotTemperatures[j] = 20;
                        }
                        setChanged();
                    }
                }
            }
        }
    }

    private static float stackTemp(ItemStack stack) {
        if (!HotItemHandler.isHot(stack)) return HotItemHandler.ROOM_TEMP;
        return HotItemHandler.getTemperature(stack);
    }

    /**
     * Точка плавления предмета по его тегам (одинакова у обоих, т.к. предмет один).
     */
    private static int meltPointOf(ItemStack a, ItemStack b) {
        CompoundTag tagA = a.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag tagB = b.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int meltingPoint = tagA.contains("MeltingPoint") ? tagA.getInt("MeltingPoint")
                : tagB.contains("MeltingPoint") ? tagB.getInt("MeltingPoint") : 1000;
        return meltingPoint <= HotItemHandler.ROOM_TEMP ? 1000 : meltingPoint;
    }

    /**
     * Физика смешивания при слиянии стопок одного предмета: присоединяемая масса
     * отбирает температуру пропорционально количеству. Так нельзя нагреть один
     * предмет и мгновенно «прогреть» его теплом весь докинутый стек — после
     * догрузки партия требует нового прогрева, как будто грузилась целиком холодной.
     *
     * @param into        стак, в который вливают (получает смешанную температуру)
     * @param donorStack  стак-источник (не меняется)
     * @param donorCount  сколько предметов реально переходит из донора
     */
    private static void blendHeat(ItemStack into, ItemStack donorStack, int donorCount) {
        int intoCount = into.getCount();
        if (donorCount <= 0 || intoCount <= 0) return;

        float intoTemp = stackTemp(into);
        float donorTemp = stackTemp(donorStack);
        if (Math.abs(intoTemp - donorTemp) < 0.01f) return;

        float blended = (intoTemp * intoCount + donorTemp * donorCount) / (intoCount + donorCount);

        if (blended <= HotItemHandler.ROOM_TEMP + 0.01f) {
            if (HotItemHandler.isHot(into)) HotItemHandler.clearHotTags(into);
            return;
        }

        int meltingPoint = meltPointOf(into, donorStack);
        float ratio = Math.max(0, Math.min(1.2f,
                (blended - HotItemHandler.ROOM_TEMP) / (meltingPoint - HotItemHandler.ROOM_TEMP)));

        // Сохраняем исходный масштаб охлаждения партии (руки/тигель)
        final int maxTime;
        if (HotItemHandler.isHot(into) && HotItemHandler.getHotTimeMax(into) > 0) {
            maxTime = HotItemHandler.getHotTimeMax(into);
        } else {
            maxTime = HotItemHandler.BASE_COOLING_TIME_HANDS;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, into, tag -> {
            tag.putFloat("HotTime", ratio * maxTime);
            tag.putInt("HotTimeMax", maxTime);
            tag.putInt("MeltingPoint", meltingPoint);
        });
    }

    private boolean isMeltableItem(ItemStack stack) {
        if (stack.is(net.minecraft.world.item.Items.COAL) ||
                stack.is(net.minecraft.world.item.Items.CHARCOAL)) {
            return false;
        }
        return true;
    }

    private void resetTopSmelting() {
        topSmelting = false;
        currentAlloyRecipe = null;
        topProgress = 0;
        topMaxProgress = 0;
        requiredTempTop = 0;
        sharedTopProgress = 0;
        sharedTopMaxProgress = 0;
        allTopSlotsReady = false;
        topHeatConsumption = 0;
    }

    private void completeAlloyRecipeSynchronized(AlloyRecipe recipe) {
        // Требования агрегированы по предмету: потребляем с любого слота (с последнего,
        // чтобы крупных "горячих" партий в ранних слотах касаться реже)
        Map<Item, Integer> needed = new LinkedHashMap<>(recipe.getItemRequirements());

        for (int i = 3; i >= 0 && !needed.isEmpty(); i--) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            Integer req = needed.get(stack.getItem());
            if (req == null) continue;

            int toTake = Math.min(req, stack.getCount());
            stack.shrink(toTake);
            if (stack.getCount() <= 0) {
                slotTemperatures[i] = 20;
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
            if (req == toTake) {
                needed.remove(stack.getItem());
            } else {
                needed.put(stack.getItem(), req - toTake);
            }
        }
        addMetal(recipe.getOutputMetal(), recipe.getOutputUnits());
        setChanged();
    }

    private AlloyRecipe findMatchingAlloyRecipe(ItemStack[] slots) {
        for (AlloyRecipe recipe : MetallurgyRegistry.getAllAlloyRecipes()) {
            if (recipe.matches(slots)) {
                return recipe;
            }
        }
        return null;
    }

    // ==================== НИЖНИЙ РЯД (ПЛАВКА + ШЛАК) ====================
    private void tickBottomRow() {
        for (int i = 0; i < 4; i++) {
            ItemStack current = inventory.getStackInSlot(4 + i);
            ItemStack prev = previousBottomStacks[i];

            if (!areItemsSameIgnoreHeat(current, prev)) {
                if (bottomSlots[i] != null && bottomSlots[i].active) {
                    if (sharedBottomMaxProgress > 0) {
                        float slotShare = bottomSlots[i].maxProgress / sharedBottomMaxProgress;
                        sharedBottomMaxProgress -= bottomSlots[i].maxProgress;
                        sharedBottomProgress = Math.max(0, sharedBottomProgress * (1 - slotShare * 0.5f));
                    }
                }
                bottomSlots[i] = null;
                previousBottomStacks[i] = current.copy();
                allBottomSlotsReady = false;
            }
        }

        boolean hasAnyRecipe = false;
        boolean allHeated = true;
        float totalHeatConsumption = 0;
        float totalMaxProgress = 0;
        int activeSlotsCount = 0;

        for (int i = 0; i < 4; i++) {
            ItemStack stack = inventory.getStackInSlot(4 + i);
            int slotIndex = 4 + i;

            if (stack.isEmpty()) continue;

            hasAnyRecipe = true;

            if (bottomSlots[i] == null) {
                bottomSlots[i] = new BottomSlotData();
                bottomSlots[i].progress = 0;
                bottomSlots[i].active = false;

                if (stack.getItem() instanceof SlagItem) {
                    Metal slagMetal = SlagItem.getMetal(stack);
                    int slagAmount = SlagItem.getAmount(stack);

                    if (slagMetal != null && slagAmount > 0) {
                        bottomSlots[i].slagData = new SlagSlotData();
                        bottomSlots[i].slagData.metal = slagMetal;
                        bottomSlots[i].slagData.amount = slagAmount;
                        bottomSlots[i].slagData.requiredTemp = slagMetal.getMeltingPoint();
                        bottomSlots[i].slagData.heatConsumption = slagMetal.getHeatConsumptionPerTick();

                        int smeltTime = slagMetal.calculateSmeltTimeForUnits(slagAmount);
                        bottomSlots[i].maxProgress = slagMetal.getHeatConsumptionPerTick() * smeltTime;
                        bottomSlots[i].heatConsumption = slagMetal.getHeatConsumptionPerTick();
                        bottomSlots[i].targetTemperature = slagMetal.getMeltingPoint();
                    } else {
                        bottomSlots[i] = null;
                        continue;
                    }
                } else {
                    SmeltRecipe recipe = MetallurgyRegistry.getSmeltRecipe(level, stack);
                    if (recipe != null) {
                        bottomSlots[i].recipe = recipe;
                        bottomSlots[i].maxProgress = recipe.getTotalHeatConsumption();
                        bottomSlots[i].heatConsumption = recipe.heatConsumption();
                        bottomSlots[i].targetTemperature = recipe.minTemp();
                    } else {
                        bottomSlots[i] = null;
                        continue;
                    }
                }
                allBottomSlotsReady = false;
            }

            BottomSlotData slot = bottomSlots[i];
            float itemTemp = getItemTemperature(stack);

            // Готовность предмета: 95% точки плавления, но не выше температуры печи
            float itemReadyTemp = Math.min(slot.targetTemperature * 0.95f, temperature);

            if (itemTemp < itemReadyTemp) {
                allHeated = false;
                slot.active = false;

                if (temperature > itemTemp) {
                    float heatNeeded = itemReadyTemp - itemTemp;
                    float heatTransfer = Math.min(HEAT_RATE * 3, heatNeeded);
                    heatTransfer = Math.min(heatTransfer, temperature * 0.1f);

                    float newTemp = itemTemp + heatTransfer;
                    setItemTemperature(stack, newTemp);
                    slotTemperatures[slotIndex] = newTemp;
                    temperature -= heatTransfer * 0.5f;
                }
            } else {
                int outputAmount = (slot.recipe != null) ? slot.recipe.outputUnits() : slot.slagData.amount;
                boolean hasEnoughItems = (slot.recipe == null) || stack.getCount() >= slot.recipe.inputCount();
                if (!hasSpaceFor(outputAmount) || !hasEnoughItems) {
                    slot.active = false;
                } else {
                    slot.active = true;
                }
            }

            slot.itemTemperature = getItemTemperature(stack);

            if (slot.active) {
                totalHeatConsumption += slot.heatConsumption;
                totalMaxProgress += slot.maxProgress;
                activeSlotsCount++;
            }
        }

        requiredTempBottom = hasAnyRecipe ? calculateMaxTempForBottomRow() : 0;

        if (!hasAnyRecipe || activeSlotsCount == 0) {
            resetBottomSmelting();
            return;
        }

        // Ранний старт: плавка идёт от 85% точки плавления, скорость растёт
        // линейно до полной при достижении точки плавления
        float speedFactor = meltSpeedFactor(temperature, requiredTempBottom);

        if (!allHeated || speedFactor <= 0f) {
            bottomSmelting = false;
            allBottomSlotsReady = false;
            if (!allHeated) {
                sharedBottomProgress = 0;
            }
            updateBottomGUI(activeSlotsCount, totalHeatConsumption);
            return;
        }

        if (!allBottomSlotsReady) {
            allBottomSlotsReady = true;
            if (sharedBottomMaxProgress <= 0) {
                sharedBottomMaxProgress = totalMaxProgress;
            }
        }

        bottomSmelting = true;

        float availableHeat = Math.min(totalHeatConsumption, temperature);
        float heatToApply = Math.min(availableHeat, sharedBottomMaxProgress - sharedBottomProgress) * speedFactor;

        sharedBottomProgress += heatToApply;
        temperature = Math.max(0, temperature - heatToApply);

        float progressRatio = sharedBottomMaxProgress > 0 ? sharedBottomProgress / sharedBottomMaxProgress : 0;
        boolean allCompleted = true;

        for (int i = 0; i < 4; i++) {
            if (bottomSlots[i] == null || !bottomSlots[i].active) continue;

            BottomSlotData slot = bottomSlots[i];
            slot.progress = progressRatio * slot.maxProgress;

            if (slot.progress >= slot.maxProgress * 0.999f) {
                completeBottomSlot(i);
            } else {
                allCompleted = false;
            }
        }

        bottomProgress = (int) sharedBottomProgress;
        bottomMaxProgress = (int) sharedBottomMaxProgress;
        bottomHeatConsumption = (int) totalHeatConsumption;

        if (allCompleted || sharedBottomProgress >= sharedBottomMaxProgress * 0.999f) {
            resetBottomSmelting();
        }
    }

    /** Сравнение предметов без учёта тепловых тегов. static — используется и тепло-слепой вставкой. */
    static boolean areItemsSameIgnoreHeat(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;

        CompoundTag tagA = a.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag tagB = b.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        tagA.remove("HotTime");
        tagA.remove("HotTimeMax");
        tagA.remove("MeltingPoint");
        tagA.remove("CooledInPot");

        tagB.remove("HotTime");
        tagB.remove("HotTimeMax");
        tagB.remove("MeltingPoint");
        tagB.remove("CooledInPot");

        return tagA.equals(tagB);
    }

    private int calculateMaxTempForBottomRow() {
        int maxTemp = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack stack = inventory.getStackInSlot(4 + i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof SlagItem) {
                Metal metal = SlagItem.getMetal(stack);
                if (metal != null) maxTemp = Math.max(maxTemp, metal.getMeltingPoint());
            } else {
                SmeltRecipe recipe = MetallurgyRegistry.getSmeltRecipe(level, stack);
                if (recipe != null) maxTemp = Math.max(maxTemp, recipe.minTemp());
            }
        }
        return maxTemp;
    }

    private void updateBottomGUI(int activeCount, float totalHeat) {
        bottomProgress = (int) sharedBottomProgress;
        bottomMaxProgress = (int) sharedBottomMaxProgress;
        bottomHeatConsumption = (int) totalHeat;
    }

    private void completeBottomSlot(int slotIndex) {
        ItemStack stack = inventory.getStackInSlot(4 + slotIndex);
        BottomSlotData slot = bottomSlots[slotIndex];
        if (slot == null) return;

        if (slot.recipe != null) {
            stack.shrink(slot.recipe.inputCount());
            addMetal(slot.recipe.output(), slot.recipe.outputUnits());
        } else if (slot.slagData != null) {
            stack.shrink(1);
            addMetal(slot.slagData.metal, slot.slagData.amount);
        } else {
            return;
        }

        slotTemperatures[4 + slotIndex] = 20;
        bottomSlots[slotIndex] = null;

        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void resetBottomSmelting() {
        if (bottomSmelting || !currentBottomRecipes.isEmpty() || sharedBottomProgress > 0) {
            bottomSmelting = false;
            currentBottomRecipes.clear();
            Arrays.fill(bottomSlots, null);
            bottomProgress = 0;
            bottomMaxProgress = 0;
            requiredTempBottom = 0;
            sharedBottomProgress = 0;
            sharedBottomMaxProgress = 0;
            allBottomSlotsReady = false;
        }
    }

    private void processItemHeatExchange() {
        float heatTransferRate = 3.0f;

        for (int i = 4; i < 8; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                slotTemperatures[i] = Math.max(20, slotTemperatures[i] - 1);
                continue;
            }

            float itemTemp = getItemTemperature(stack);
            float furnaceTemp = this.temperature;

            if (Math.abs(itemTemp - furnaceTemp) > 1.0f) {
                float diff = furnaceTemp - itemTemp;
                float transfer = Math.signum(diff) * Math.min(Math.abs(diff) * 0.15f, heatTransferRate);

                float newTemp = itemTemp + transfer;
                setItemTemperature(stack, newTemp);
                slotTemperatures[i] = newTemp;

                float furnaceInfluence = -transfer * 0.02f;
                this.temperature = Math.max(0, this.temperature + furnaceInfluence);

                if ((int) newTemp % 5 == 0) {
                    setChanged();
                }
            }
        }
    }

    private int getMeltingPointForItem(ItemStack stack) {
        SmeltRecipe recipe = MetallurgyRegistry.getSmeltRecipe(level, stack);
        if (recipe != null) {
            return recipe.minTemp();
        }

        if (stack.getItem() instanceof SlagItem) {
            return SlagItem.getMeltingPoint(stack);
        }

        for (AlloyRecipe alloy : MetallurgyRegistry.getAllAlloyRecipes()) {
            for (AlloySlot slot : alloy.getSlots()) {
                if (slot.accepts(stack.getItem())) {
                    return alloy.getOutputMetal().getMeltingPoint();
                }
            }
        }

        if (stack.is(net.minecraft.world.item.Items.COAL) ||
                stack.is(net.minecraft.world.item.Items.CHARCOAL)) {
            return 300;
        }

        return 800;
    }

    private float getItemTemperature(ItemStack stack) {
        if (HotItemHandler.isHot(stack)) {
            return HotItemHandler.getTemperature(stack);
        }
        return HotItemHandler.ROOM_TEMP;
    }

    private void setItemTemperature(ItemStack stack, float temp) {
        if (temp <= HotItemHandler.ROOM_TEMP) {
            if (HotItemHandler.isHot(stack)) {
                HotItemHandler.clearHotTags(stack);
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }
            return;
        }

        int mp = getMeltingPointForItem(stack);
        final int meltingPoint = mp <= 0 ? 1000 : mp;

        float heatRatio = (temp - HotItemHandler.ROOM_TEMP) / (meltingPoint - HotItemHandler.ROOM_TEMP);
        heatRatio = Math.max(0, Math.min(1.2f, heatRatio));

        int maxTime = HotItemHandler.BASE_COOLING_TIME_HANDS;
        float hotTime = heatRatio * maxTime;

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putFloat("HotTime", hotTime);
            tag.putInt("HotTimeMax", maxTime);
            tag.putInt("MeltingPoint", meltingPoint);
            tag.putBoolean("CooledInPot", false);
        });

        if (level != null && !level.isClientSide && (int) hotTime % 10 == 0) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Хэш строки как суммарного состава по типам предметов (порядок слотов и
     * разбиение по слотам не важны). Так мердж и догрузка того же предмета
     * не сбрасывают плавку, а добавление нового типа предмета — сбрасывает.
     */
    private int calculateRowHash(int from, int to) {
        Map<Item, Integer> aggregated = new LinkedHashMap<>();
        for (int i = from; i <= to; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            aggregated.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        return aggregated.hashCode();
    }

    private int calculateTopHash() {
        return calculateRowHash(0, 3);
    }

    private int calculateBottomHash() {
        return calculateRowHash(4, 7);
    }

    private void addMetal(Metal metal, int units) {
        if (units <= 0 || metal == null) return;
        if (totalMetalAmount + units > TANK_CAPACITY) {
            units = TANK_CAPACITY - totalMetalAmount;
            if (units <= 0) return;
        }
        metalTank.merge(metal, units, Integer::sum);
        recalculateTotal();
    }

    private void recalculateTotal() {
        totalMetalAmount = metalTank.values().stream().mapToInt(Integer::intValue).sum();
        if (totalMetalAmount > TANK_CAPACITY) {
            int excess = totalMetalAmount - TANK_CAPACITY;
            for (var entry : metalTank.entrySet()) {
                if (entry.getValue() >= excess) {
                    entry.setValue(entry.getValue() - excess);
                    break;
                }
            }
            recalculateTotal();
        }
    }

    @Override
    public int extractMetal(Metal metal, int maxUnits) {
        Integer current = metalTank.get(metal);
        if (current == null || current <= 0) return 0;
        int toExtract = Math.min(maxUnits, current);
        if (toExtract <= 0) return 0;
        if (current <= toExtract) {
            metalTank.remove(metal);
        } else {
            metalTank.put(metal, current - toExtract);
        }
        recalculateTotal();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
        return toExtract;
    }

    public List<ItemStack> dumpMetalAsSlag() {
        List<ItemStack> slagItems = new ArrayList<>();

        metalTank.forEach((metal, amount) -> {
            if (amount > 0) {
                ItemStack slag = SlagItem.createSlag(metal, amount);
                if (!HotItemHandler.isHot(slag)) {
                    CustomData.update(DataComponents.CUSTOM_DATA, slag, tag -> {
                        tag.putFloat("HotTime", SlagItem.BASE_COOLING_TIME);
                        tag.putInt("HotTimeMax", SlagItem.BASE_COOLING_TIME);
                    });
                }
                slagItems.add(slag);
            }
        });

        metalTank.clear();
        totalMetalAmount = 0;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }

        return slagItems;
    }

    @Override
    public boolean hasMetal() {
        return totalMetalAmount > 0;
    }

    public boolean isTankFull() {
        return totalMetalAmount >= TANK_CAPACITY * 0.95f;
    }

    public boolean hasSpaceFor(int units) {
        return totalMetalAmount + units <= TANK_CAPACITY;
    }

    public Metal getBottomMetal() { return getMetalForCasting(null); }
    public ItemStackHandler getInventory() { return inventory; }
    public ContainerData getData() { return data; }
    public float getTemperature() { return temperature; }
    public int getSmokeTicks() { return smokeTicks; }
    public Map<Metal, Integer> getMetalTank() { return Collections.unmodifiableMap(metalTank); }
    public int getTotalMetalAmount() { return totalMetalAmount; }
    public int getBlockCapacity() { return BLOCK_CAPACITY; }
    public int getRequiredTempTop() { return requiredTempTop; }
    public int getRequiredTempBottom() { return requiredTempBottom; }
    public float getTopProgress() { return topProgress; }
    public float getTopMaxProgress() { return topMaxProgress; }
    public boolean isTopSmelting() { return topSmelting; }
    public float getBottomProgress() { return bottomProgress; }
    public float getBottomMaxProgress() { return bottomMaxProgress; }
    public boolean isBottomSmelting() { return bottomSmelting; }

    public float getSlotTemperature(int slot) {
        return slot >= 0 && slot < 8 ? slotTemperatures[slot] : 20;
    }

    public List<MetalStack> getMetalStacks() {
        List<MetalStack> list = new ArrayList<>();
        metalTank.forEach((metal, amount) -> {
            if (amount > 0) list.add(new MetalStack(metal, amount));
        });
        return list;
    }

    @Override
    public Metal getMetalForCasting(List<Metal> preferredMetals) {
        if (metalTank.isEmpty()) return null;

        if (preferredMetals != null && !preferredMetals.isEmpty()) {
            for (Metal preferred : preferredMetals) {
                if (metalTank.containsKey(preferred) && metalTank.get(preferred) > 0) {
                    return preferred;
                }
            }
        }

        return metalTank.keySet().iterator().next();
    }

    public static class MetalStack {
        public final Metal metal;
        public final int amount;
        public MetalStack(Metal metal, int amount) { this.metal = metal; this.amount = amount; }
        public String getFormattedAmount() {
            return MetalUnits2.convertFromUnits(amount).totalUnits() + " ед.";
        }
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putFloat("Temperature", temperature);
        tag.putInt("SmokeTicks", smokeTicks);
        tag.putFloat("TopProgress", topProgress);
        tag.putFloat("TopMaxProgress", topMaxProgress);
        tag.putFloat("BottomProgress", bottomProgress);
        tag.putFloat("BottomMaxProgress", bottomMaxProgress);
        tag.putInt("RequiredTempTop", requiredTempTop);
        tag.putInt("RequiredTempBottom", requiredTempBottom);
        tag.putFloat("SharedBottomProgress", sharedBottomProgress);
        tag.putFloat("SharedBottomMaxProgress", sharedBottomMaxProgress);
        tag.putBoolean("AllBottomSlotsReady", allBottomSlotsReady);
        tag.putFloat("SharedTopProgress", sharedTopProgress);
        tag.putFloat("SharedTopMaxProgress", sharedTopMaxProgress);
        tag.putBoolean("AllTopSlotsReady", allTopSlotsReady);
        tag.putFloat("TopHeatConsumption", topHeatConsumption);

        ListTag tempsTag = new ListTag();
        for (float temp : slotTemperatures) {
            CompoundTag t = new CompoundTag();
            t.putFloat("Temp", temp);
            tempsTag.add(t);
        }
        tag.put("SlotTemperatures", tempsTag);

        ListTag bottomSlotsTag = new ListTag();
        for (int i = 0; i < 4; i++) {
            CompoundTag slotTag = new CompoundTag();
            if (bottomSlots[i] != null) {
                slotTag.putBoolean("HasData", true);
                slotTag.putFloat("Progress", bottomSlots[i].progress);
                slotTag.putFloat("MaxProgress", bottomSlots[i].maxProgress);
                slotTag.putFloat("HeatConsumption", bottomSlots[i].heatConsumption);
                slotTag.putBoolean("Active", bottomSlots[i].active);
                slotTag.putFloat("ItemTemperature", bottomSlots[i].itemTemperature);
                slotTag.putInt("TargetTemperature", bottomSlots[i].targetTemperature);

                if (bottomSlots[i].recipe != null) {
                    ItemStack slotStack = inventory.getStackInSlot(4 + i);
                    if (!slotStack.isEmpty()) {
                        slotTag.putString("RecipeItem", BuiltInRegistries.ITEM.getKey(slotStack.getItem()).toString());
                    }
                } else if (bottomSlots[i].slagData != null) {
                    slotTag.putBoolean("IsSlag", true);
                    slotTag.putString("SlagMetal", bottomSlots[i].slagData.metal.getId().toString());
                    slotTag.putInt("SlagAmount", bottomSlots[i].slagData.amount);
                    slotTag.putInt("SlagRequiredTemp", bottomSlots[i].slagData.requiredTemp);
                    slotTag.putFloat("SlagHeatConsumption", bottomSlots[i].slagData.heatConsumption);
                }
            } else {
                slotTag.putBoolean("HasData", false);
            }
            bottomSlotsTag.add(slotTag);
        }
        tag.put("BottomSlots", bottomSlotsTag);

        tag.putInt("LastTopHash", lastTopHash);
        tag.putInt("LastBottomHash", lastBottomHash);

        ListTag metals = new ListTag();
        metalTank.forEach((metal, amount) -> {
            if (amount > 0) {
                CompoundTag mt = new CompoundTag();
                mt.putString("Metal", metal.getId().toString());
                mt.putInt("Amount", amount);
                metals.add(mt);
            }
        });
        tag.put("Metals", metals);
        tag.putInt("TotalMetal", totalMetalAmount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
        temperature = tag.getFloat("Temperature");
        smokeTicks = tag.getInt("SmokeTicks");
        topProgress = tag.getFloat("TopProgress");
        topMaxProgress = tag.getFloat("TopMaxProgress");
        bottomProgress = tag.getFloat("BottomProgress");
        bottomMaxProgress = tag.getFloat("BottomMaxProgress");
        requiredTempTop = tag.getInt("RequiredTempTop");
        requiredTempBottom = tag.getInt("RequiredTempBottom");
        sharedBottomProgress = tag.getFloat("SharedBottomProgress");
        sharedBottomMaxProgress = tag.getFloat("SharedBottomMaxProgress");
        allBottomSlotsReady = tag.getBoolean("AllBottomSlotsReady");
        sharedTopProgress = tag.getFloat("SharedTopProgress");
        sharedTopMaxProgress = tag.getFloat("SharedTopMaxProgress");
        allTopSlotsReady = tag.getBoolean("AllTopSlotsReady");
        topHeatConsumption = tag.getFloat("TopHeatConsumption");

        if (tag.contains("SlotTemperatures")) {
            ListTag tempsTag = tag.getList("SlotTemperatures", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(8, tempsTag.size()); i++) {
                slotTemperatures[i] = tempsTag.getCompound(i).getFloat("Temp");
            }
        }

        if (tag.contains("BottomSlots")) {
            ListTag bottomSlotsTag = tag.getList("BottomSlots", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(4, bottomSlotsTag.size()); i++) {
                CompoundTag slotTag = bottomSlotsTag.getCompound(i);
                if (slotTag.getBoolean("HasData")) {
                    bottomSlots[i] = new BottomSlotData();
                    bottomSlots[i].progress = slotTag.getFloat("Progress");
                    bottomSlots[i].maxProgress = slotTag.getFloat("MaxProgress");
                    bottomSlots[i].heatConsumption = slotTag.getFloat("HeatConsumption");
                    bottomSlots[i].active = slotTag.getBoolean("Active");
                    bottomSlots[i].itemTemperature = slotTag.getFloat("ItemTemperature");
                    bottomSlots[i].targetTemperature = slotTag.getInt("TargetTemperature");

                    if (slotTag.getBoolean("IsSlag")) {
                        bottomSlots[i].slagData = new SlagSlotData();
                        ResourceLocation metalId = ResourceLocation.parse(slotTag.getString("SlagMetal"));
                        int index = i;
                        MetallurgyRegistry.get(metalId).ifPresent(metal -> bottomSlots[index].slagData.metal = metal);
                        bottomSlots[i].slagData.amount = slotTag.getInt("SlagAmount");
                        bottomSlots[i].slagData.requiredTemp = slotTag.getInt("SlagRequiredTemp");
                        bottomSlots[i].slagData.heatConsumption = slotTag.getFloat("SlagHeatConsumption");
                    } else if (slotTag.contains("RecipeItem")) {
                        ResourceLocation itemId = ResourceLocation.parse(slotTag.getString("RecipeItem"));
                        Item item = BuiltInRegistries.ITEM.get(itemId);
                        if (item != null) {
                            ItemStack saved = inventory.getStackInSlot(4 + i);
                            SmeltRecipe recipe = MetallurgyRegistry.getSmeltRecipe(level, saved);
                            bottomSlots[i].recipe = recipe != null ? recipe
                                    : MetallurgyRegistry.getSmeltRecipe(level, new ItemStack(item));
                        }
                    }
                } else {
                    bottomSlots[i] = null;
                }
            }
        }

        lastTopHash = tag.getInt("LastTopHash");
        lastBottomHash = tag.getInt("LastBottomHash");

        metalTank.clear();
        ListTag metals = tag.getList("Metals", Tag.TAG_COMPOUND);
        for (int i = 0; i < metals.size(); i++) {
            CompoundTag mt = metals.getCompound(i);
            ResourceLocation id = ResourceLocation.parse(mt.getString("Metal"));
            int amt = mt.getInt("Amount");
            MetallurgyRegistry.get(id).ifPresent(metal -> metalTank.put(metal, amt));
        }
        recalculateTotal();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        loadAdditional(tag, registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        if (pkt.getTag() != null) handleUpdateTag(pkt.getTag(), registries);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.trd.smelter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SmelterMenu(id, inv, this, data);
    }

    public static class InsertOnlyHandler implements IItemHandler {
        private final ItemStackHandler inventory;
        private final int[] slots;

        public InsertOnlyHandler(ItemStackHandler inventory, int... slots) {
            this.inventory = inventory;
            this.slots = slots;
        }

        @Override public int getSlots() { return slots.length; }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= slots.length) return ItemStack.EMPTY;
            return inventory.getStackInSlot(slots[slot]);
        }

        /**
         * Вставка, нечувствительная к тепловым тегам: сначала докладывает стак в
         * существующие стопки того же предмета (HotTime и др. не сравниваются), и
         * только затем раскладывает остаток по пустым слотам. При докладывании тепло
         * партии смешивается пропорционально массе (blendHeat): холодная подача
         * охлаждает партию и требует повторного прогрева, так что «нагреть один
         * предмет и закинуть остальные» не даёт выгоды.
         */
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= slots.length) return stack;

            ItemStack remaining = stack.copy();
            if (remaining.isEmpty()) return ItemStack.EMPTY;

            // Этап 1: докладываем в существующие стопки того же предмета
            for (int s : slots) {
                if (remaining.isEmpty()) return ItemStack.EMPTY;

                ItemStack existing = inventory.getStackInSlot(s);
                if (existing.isEmpty() || !SmelterBlockEntity.areItemsSameIgnoreHeat(existing, remaining)) continue;
                if (!inventory.isItemValid(s, remaining)) continue;

                int limit = Math.min(existing.getMaxStackSize(), inventory.getSlotLimit(s));
                int space = limit - existing.getCount();
                if (space <= 0) continue;

                int toMove = Math.min(space, remaining.getCount());
                if (!simulate) {
                    SmelterBlockEntity.blendHeat(existing, remaining, toMove);
                    existing.grow(toMove);
                    inventory.setStackInSlot(s, existing);
                }
                remaining.shrink(toMove);
            }
            if (remaining.isEmpty()) return ItemStack.EMPTY;

            // Этап 2: остаток — штатной логикой по пустым слотам
            for (int s : slots) {
                if (remaining.isEmpty()) return ItemStack.EMPTY;
                if (!inventory.getStackInSlot(s).isEmpty()) continue;

                int before = remaining.getCount();
                remaining = inventory.insertItem(s, remaining, simulate);
                if (remaining.getCount() == before) break; // слот не принимает этот предмет
            }
            return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot < 0 || slot >= slots.length) return 0;
            return inventory.getSlotLimit(slots[slot]);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 0 || slot >= slots.length) return false;
            return inventory.isItemValid(slots[slot], stack);
        }
    }
}
