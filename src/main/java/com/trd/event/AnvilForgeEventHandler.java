package com.trd.event;

import com.trd.api.metallurgy.system.Metal;
import com.trd.api.metallurgy.system.MetallurgyRegistry;
import com.trd.api.metallurgy.system.recipe.MoldRecipe;
import com.trd.api.metallurgy.system.recipe.MoldRecipeRegistry;
import com.trd.item.industrial.fluids.HammerItem;
import com.trd.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.FloatTag;

import java.util.List;

@Mod.EventBusSubscriber(modid = "trd", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AnvilForgeEventHandler {

    public static final String ANVIL_ITEM_TAG = "trd_anvil_item";

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        if (level.getBlockState(pos).is(Blocks.ANVIL)) {
            ItemStack heldItem = event.getItemStack();
            InteractionHand hand = event.getHand();

            // Поиск ItemDisplay сущности на наковальне с увеличенным AABB (inflate), чтобы точно захватить энтити сверху
            List<Display.ItemDisplay> displays = level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(pos).inflate(1.0));
            Display.ItemDisplay display = null;
            for (Display.ItemDisplay d : displays) {
                if (d.getTags().contains(ANVIL_ITEM_TAG)) {
                    display = d;
                    break;
                }
            }

            if (display == null) {
                // Если нет предмета на наковальне
                if (!heldItem.isEmpty() && !(heldItem.getItem() instanceof HammerItem)) {
                    // Пытаемся найти металл по слитку, чтобы убедиться, что это допустимый слиток
                    Metal metal = getMetalFromIngot(heldItem);
                    if (metal != null) {
                        event.setCanceled(true);
                        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                        if (level.isClientSide) return;

                        // Размещаем предмет на наковальне
                        ItemStack placedItem = heldItem.copy();
                        placedItem.setCount(1);
                        heldItem.shrink(1);

                        Display.ItemDisplay newDisplay = EntityType.ITEM_DISPLAY.create(level);
                        if (newDisplay != null) {
                            // Высота наковальни 1 блок, спавним чуть выше
                            newDisplay.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                            
                            CompoundTag tag = new CompoundTag();
                            newDisplay.saveWithoutId(tag);
                            
                            tag.put("item", placedItem.save(new CompoundTag()));
                            tag.putString("item_display", "fixed"); // Отображаем как зафиксированный предмет
                            
                            // Трансформация (поворачиваем на 90 градусов по оси X, чтобы предмет лежал плашмя)
                            CompoundTag transform = new CompoundTag();
                            
                            ListTag leftRot = new ListTag();
                            leftRot.add(FloatTag.valueOf(0.7071068f)); // X
                            leftRot.add(FloatTag.valueOf(0.0f));       // Y
                            leftRot.add(FloatTag.valueOf(0.0f));       // Z
                            leftRot.add(FloatTag.valueOf(0.7071068f)); // W
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
                            rightRot.add(FloatTag.valueOf(0.7071068f)); // Z
                            rightRot.add(FloatTag.valueOf(0.7071068f)); // W
                            transform.put("right_rotation", rightRot);

                            tag.put("transformation", transform);
                            
                            newDisplay.load(tag);
                            newDisplay.addTag(ANVIL_ITEM_TAG);
                            level.addFreshEntity(newDisplay);
                        }
                        
                        return;
                    }
                }
            } else {
                // Если есть предмет на наковальне
                if (!heldItem.isEmpty() && heldItem.getItem() instanceof HammerItem) {
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                    if (level.isClientSide) return;

                    // Ковка
                    // Достаем предмет через NBT
                    CompoundTag displayTag = new CompoundTag();
                    display.saveWithoutId(displayTag);
                    ItemStack ingotStack = ItemStack.of(displayTag.getCompound("item"));
                    Metal metal = getMetalFromIngot(ingotStack);

                    if (metal != null) {
                        float temp = HotItemHandler.isHot(ingotStack) ? HotItemHandler.getTemperature(ingotStack) : HotItemHandler.ROOM_TEMP;
                        float requiredTemp = metal.getMeltingPoint() * 0.15f;

                        if (temp >= requiredTemp) {
                            // Ищем рецепт пластины
                            MoldRecipe plateRecipe = MoldRecipeRegistry.getRecipe(ModItems.MOLD_PLATE.get());
                            if (plateRecipe != null) {
                                ItemStack plateStack = plateRecipe.createOutput(metal);
                                if (!plateStack.isEmpty()) {
                                    // Превращаем в пластину, точно копируя теги нагрева
                                    if (HotItemHandler.isHot(ingotStack) && ingotStack.hasTag()) {
                                        CompoundTag plateTag = plateStack.getOrCreateTag();
                                        CompoundTag ingotTag = ingotStack.getTag();
                                        plateTag.putFloat("HotTime", ingotTag.getFloat("HotTime"));
                                        plateTag.putInt("HotTimeMax", ingotTag.getInt("HotTimeMax"));
                                        plateTag.putInt("MeltingPoint", ingotTag.getInt("MeltingPoint"));
                                        plateTag.putBoolean("CooledInPot", ingotTag.getBoolean("CooledInPot"));
                                    }

                                    // Сохраняем новую пластину в Display
                                    displayTag.put("item", plateStack.save(new CompoundTag()));
                                    display.load(displayTag);
                                    heldItem.hurtAndBreak(1, event.getEntity(), (player) -> player.broadcastBreakEvent(hand));
                                    level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.1F + 0.9F);
                                    
                                    return;
                                }
                            }
                        }
                    }
                } else if (heldItem.isEmpty()) {
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                    if (level.isClientSide) return;

                    CompoundTag displayTag = new CompoundTag();
                    display.saveWithoutId(displayTag);
                    ItemStack storedItem = ItemStack.of(displayTag.getCompound("item"));
                    
                    if (!storedItem.isEmpty()) {
                        if (!event.getEntity().getInventory().add(storedItem)) {
                            ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, storedItem);
                            level.addFreshEntity(itemEntity);
                        }
                    }
                    display.discard();
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockPos pos = event.getPos();
        Level level = (Level) event.getLevel();

        if (level.getBlockState(pos).is(Blocks.ANVIL)) {
            List<Display.ItemDisplay> displays = level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(pos).inflate(1.0));
            for (Display.ItemDisplay d : displays) {
                if (d.getTags().contains(ANVIL_ITEM_TAG)) {
                    CompoundTag displayTag = new CompoundTag();
                    d.saveWithoutId(displayTag);
                    ItemStack storedItem = ItemStack.of(displayTag.getCompound("item"));
                    
                    if (!storedItem.isEmpty()) {
                        ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, storedItem);
                        level.addFreshEntity(itemEntity);
                    }
                    d.discard();
                }
            }
        }
    }

    private static Metal getMetalFromIngot(ItemStack stack) {
        for (Metal metal : MetallurgyRegistry.getAllMetals()) {
            if (metal.getIngot() != null && stack.is(metal.getIngot())) {
                return metal;
            }
        }
        return null;
    }
}
