package com.trd.event;

import com.trd.api.metallurgy.system.Metal;
import com.trd.api.metallurgy.system.MetallurgyRegistry;
import com.trd.api.metallurgy.system.recipe.MoldRecipe;
import com.trd.api.metallurgy.system.recipe.MoldRecipeRegistry;
import com.trd.item.ModItems;
import com.trd.item.industrial.fluids.HammerItem;
import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public class AnvilForgeEventHandler {

    public static final String ANVIL_ITEM_TAG = "trd_anvil_item";

    public static boolean isAnvilBlock(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(BlockTags.ANVIL) || state.getBlock() instanceof AnvilBlock;
    }

    public static Display.ItemDisplay findAnvilDisplay(Level level, BlockPos pos) {
        List<Display.ItemDisplay> displays = level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(pos).inflate(1.0));
        for (Display.ItemDisplay d : displays) {
            if (d.getTags().contains(ANVIL_ITEM_TAG) && !d.isRemoved()) {
                return d;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        if (isAnvilBlock(level, pos)) {
            ItemStack heldItem = event.getItemStack();
            InteractionHand hand = event.getHand();

            Display.ItemDisplay display = findAnvilDisplay(level, pos);

            if (display == null) {
                // Если нет предмета на наковальне
                if (!heldItem.isEmpty() && !(heldItem.getItem() instanceof HammerItem)) {
                    if (getMetalFromIngot(heldItem) != null) {
                        event.setCanceled(true);
                        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                        if (level.isClientSide) return;

                        tryPlaceItem(level, pos, heldItem);
                    }
                }
            } else {
                // Если есть предмет на наковальне
                if (!heldItem.isEmpty() && heldItem.getItem() instanceof HammerItem) {
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                    if (level.isClientSide) return;

                    tryHammerHit(level, pos, display, heldItem, event.getEntity(), hand);
                } else if (heldItem.isEmpty()) {
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                    if (level.isClientSide) return;

                    tryPickup(level, pos, display, event.getEntity());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Display.ItemDisplay display && display.getTags().contains(ANVIL_ITEM_TAG) && !display.isRemoved()) {
            Level level = event.getLevel();
            BlockPos pos = display.blockPosition().below();
            if (!isAnvilBlock(level, pos)) {
                pos = display.blockPosition();
            }

            ItemStack heldItem = event.getItemStack();
            InteractionHand hand = event.getHand();

            if (!heldItem.isEmpty() && heldItem.getItem() instanceof HammerItem) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                if (level.isClientSide) return;

                tryHammerHit(level, pos, display, heldItem, event.getEntity(), hand);
            } else if (heldItem.isEmpty()) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                if (level.isClientSide) return;

                tryPickup(level, pos, display, event.getEntity());
            }
        }
    }

    private static boolean tryPlaceItem(Level level, BlockPos pos, ItemStack heldItem) {
        Metal metal = getMetalFromIngot(heldItem);
        if (metal == null) return false;

        ItemStack placedItem = heldItem.copy();
        placedItem.setCount(1);
        heldItem.shrink(1);

        Display.ItemDisplay newDisplay = EntityType.ITEM_DISPLAY.create(level);
        if (newDisplay != null) {
            newDisplay.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);

            CompoundTag tag = new CompoundTag();
            newDisplay.saveWithoutId(tag);

            tag.putString("item_display", "fixed");

            CompoundTag transform = new CompoundTag();

            ListTag leftRot = new ListTag();
            leftRot.add(FloatTag.valueOf(0.7071068f));
            leftRot.add(FloatTag.valueOf(0.0f));
            leftRot.add(FloatTag.valueOf(0.0f));
            leftRot.add(FloatTag.valueOf(0.7071068f));
            transform.put("left_rotation", leftRot);

            ListTag translation = new ListTag();
            translation.add(FloatTag.valueOf(0.0f));
            translation.add(FloatTag.valueOf(0.0f));
            translation.add(FloatTag.valueOf(0.0f));
            transform.put("translation", translation);

            ListTag scale = new ListTag();
            scale.add(FloatTag.valueOf(0.66f));
            scale.add(FloatTag.valueOf(0.66f));
            scale.add(FloatTag.valueOf(0.66f));
            transform.put("scale", scale);

            ListTag rightRot = new ListTag();
            rightRot.add(FloatTag.valueOf(0.0f));
            rightRot.add(FloatTag.valueOf(0.0f));
            rightRot.add(FloatTag.valueOf(0.7071068f));
            rightRot.add(FloatTag.valueOf(0.7071068f));
            transform.put("right_rotation", rightRot);

            tag.put("transformation", transform);

            newDisplay.load(tag);
            newDisplay.getSlot(0).set(placedItem);
            newDisplay.addTag(ANVIL_ITEM_TAG);
            level.addFreshEntity(newDisplay);
            return true;
        }
        return false;
    }

    private static boolean tryHammerHit(Level level, BlockPos pos, Display.ItemDisplay display, ItemStack heldItem, LivingEntity entity, InteractionHand hand) {
        ItemStack ingotStack = display.getSlot(0).get();
        if (ingotStack.isEmpty()) return false;
        Metal metal = getMetalFromIngot(ingotStack);

        if (metal != null) {
            float temp = HotItemHandler.isHot(ingotStack) ? HotItemHandler.getTemperature(ingotStack) : HotItemHandler.ROOM_TEMP;
            float requiredTemp = metal.getMeltingPoint() * 0.15f;

            if (temp >= requiredTemp) {
                MoldRecipe plateRecipe = MoldRecipeRegistry.getRecipe(ModItems.MOLD_PLATE.get());
                if (plateRecipe != null) {
                    ItemStack plateStack = plateRecipe.createOutput(metal);
                    if (!plateStack.isEmpty()) {
                        if (HotItemHandler.isHot(ingotStack)) {
                            float hotTime = HotItemHandler.getHotTime(ingotStack);
                            int maxTime = HotItemHandler.getHotTimeMax(ingotStack);
                            int meltingPoint = HotItemHandler.getMeltingPoint(ingotStack);
                            boolean inPot = HotItemHandler.wasCooledInPot(ingotStack);
                            CustomData.update(DataComponents.CUSTOM_DATA, plateStack, tag -> {
                                tag.putFloat("HotTime", hotTime);
                                tag.putInt("HotTimeMax", maxTime);
                                tag.putInt("MeltingPoint", meltingPoint);
                                tag.putBoolean("CooledInPot", inPot);
                            });
                        }

                        display.getSlot(0).set(plateStack);
                        heldItem.hurtAndBreak(1, entity, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.1F + 0.9F);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean tryPickup(Level level, BlockPos pos, Display.ItemDisplay display, Player player) {
        ItemStack storedItem = display.getSlot(0).get();
        if (!storedItem.isEmpty()) {
            if (!player.getInventory().add(storedItem)) {
                ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, storedItem);
                level.addFreshEntity(itemEntity);
            }
        }
        display.discard();
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
        return true;
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockPos pos = event.getPos();
        Level level = (Level) event.getLevel();

        if (isAnvilBlock(level, pos)) {
            Display.ItemDisplay display = findAnvilDisplay(level, pos);
            if (display != null) {
                ItemStack storedItem = display.getSlot(0).get();
                if (!storedItem.isEmpty()) {
                    ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, storedItem);
                    level.addFreshEntity(itemEntity);
                }
                display.discard();
            }
        }
    }

    private static Metal getMetalFromIngot(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Metal metal : MetallurgyRegistry.getAllMetals()) {
            if (metal.getIngot() != null && stack.is(metal.getIngot())) {
                return metal;
            }
        }
        return null;
    }
}
