package com.trd.event;

import com.trd.item.tools.PokerItem;
import com.trd.main.MainRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public class HotItemHandler {

    public static final int BASE_COOLING_TIME_HANDS = 150;
    public static final int BASE_COOLING_TIME_POT = 80;
    public static final float QUADRATIC_FACTOR = 4.0f;
    private static final int DAMAGE_COOLDOWN_TICKS = 10;
    public static final int ROOM_TEMP = 20;

    public static final int TEMP_EXTREME = 800;
    public static final int TEMP_HOT = 400;
    public static final int TEMP_WARM = 100;

    private static final Map<UUID, Integer> damageCooldown = new HashMap<>();

    public static void setHot(ItemStack stack, int meltingPoint, boolean isInPot) {
        int baseTime = isInPot ? BASE_COOLING_TIME_POT : BASE_COOLING_TIME_HANDS;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putFloat("HotTime", baseTime);
            tag.putInt("HotTimeMax", baseTime);
            tag.putInt("MeltingPoint", meltingPoint);
            tag.putBoolean("CooledInPot", isInPot);
        });
    }

    public static int getTemperature(ItemStack stack) {
        if (!isHot(stack)) return ROOM_TEMP;

        float heatRatio = getHeatRatio(stack);
        int meltingPoint = getMeltingPoint(stack);
        return ROOM_TEMP + (int) (heatRatio * (meltingPoint - ROOM_TEMP));
    }

    public static float getHeatRatio(ItemStack stack) {
        if (!isHot(stack)) return 0f;

        float hotTime = getHotTime(stack);
        int maxTime = getHotTimeMax(stack);
        if (maxTime <= 0) maxTime = BASE_COOLING_TIME_HANDS;

        return Math.max(0f, Math.min(1f, hotTime / (float) maxTime));
    }

    public static float getCoolingRatio(ItemStack stack) {
        return 1.0f - getHeatRatio(stack);
    }

    public static float getHotTime(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains("HotTime")) {
            return tag.getFloat("HotTime");
        }
        return 0;
    }

    public static int getHotTimeMax(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getInt("HotTimeMax");
    }

    public static int getMeltingPoint(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("MeltingPoint")) return 1000;
        return tag.getInt("MeltingPoint");
    }

    public static boolean wasCooledInPot(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getBoolean("CooledInPot");
    }

    public static HeatStatus getHeatStatus(int temperature) {
        if (temperature >= TEMP_EXTREME) return HeatStatus.EXTREME;
        if (temperature >= TEMP_HOT) return HeatStatus.HOT;
        if (temperature >= TEMP_WARM) return HeatStatus.WARM;
        return HeatStatus.COOLING;
    }

    public enum HeatStatus {
        EXTREME(ChatFormatting.DARK_RED, "РАСКАЛЁННЫЙ", " §c§o Бросай и беги!"),
        HOT(ChatFormatting.RED, "ПЕРЕГРЕТЫЙ", ""),
        WARM(ChatFormatting.GOLD, "ГОРЯЧИЙ", ""),
        COOLING(ChatFormatting.YELLOW, "НАГРЕТЫЙ", "");

        public final ChatFormatting color;
        public final String label;
        public final String warning;

        HeatStatus(ChatFormatting color, String label, String warning) {
            this.color = color;
            this.label = label;
            this.warning = warning;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        UUID playerId = player.getUUID();
        boolean hasHotItem = false;
        float maxHeatRatio = 0f;
        int maxTemp = ROOM_TEMP;
        boolean inventoryChanged = false;
        boolean shouldCool = (player.level().getGameTime() % 10 == 0);
        float deltaTicks = 10f;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isHot(stack)) continue;

            float hotTime = getHotTime(stack);
            int maxTime = getHotTimeMax(stack);
            int meltingPoint = getMeltingPoint(stack);

            if (maxTime <= 0) maxTime = BASE_COOLING_TIME_HANDS;
            if (meltingPoint <= 0) meltingPoint = 1000;

            if (hotTime > 0) {
                float heatRatio = hotTime / (float) maxTime;

                if (shouldCool) {
                    float baseRate = (float) maxTime / 10000f;
                    if (baseRate < 0.05f) baseRate = 0.05f;

                    float quadraticMultiplier = 1.0f + (QUADRATIC_FACTOR * heatRatio * heatRatio);
                    float coolingRate = baseRate * quadraticMultiplier;
                    if (coolingRate < 0.02f) coolingRate = 0.02f;

                    float newHotTime = Math.max(0, hotTime - (coolingRate * deltaTicks));

                    if (newHotTime <= 0.5f) {
                        clearHotTags(stack);
                    } else {
                        final float finalTime = newHotTime;
                        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putFloat("HotTime", finalTime));
                    }
                    inventoryChanged = true;
                }

                hasHotItem = true;
                maxHeatRatio = Math.max(maxHeatRatio, heatRatio);
                int currentTemp = getTemperature(stack);
                maxTemp = Math.max(maxTemp, currentTemp);
            } else {
                clearHotTags(stack);
                inventoryChanged = true;
            }
        }

        boolean hasPoker = player.getOffhandItem().getItem() instanceof PokerItem
                || player.getMainHandItem().getItem() instanceof PokerItem;
        if (hasHotItem && maxHeatRatio > 0.15f && !hasPoker) {
            int fireSeconds = (int) (maxHeatRatio * 5);
            float damageAmount = (maxTemp / 1200f) * maxHeatRatio * 1.5f;

            if (fireSeconds > 0) {
                player.igniteForSeconds(fireSeconds);
            }

            int currentCooldown = damageCooldown.getOrDefault(playerId, 0);
            if (currentCooldown <= 0 && damageAmount >= 0.5f) {
                player.hurt(player.damageSources().onFire(), damageAmount);
                damageCooldown.put(playerId, DAMAGE_COOLDOWN_TICKS);
            } else {
                damageCooldown.put(playerId, Math.max(0, currentCooldown - 1));
            }
        } else {
            damageCooldown.remove(playerId);
        }

        if (inventoryChanged) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() != null && !event.getPlayer().level().isClientSide) {
            event.getPlayer().getInventory().setChanged();
            event.getPlayer().containerMenu.broadcastChanges();
        }
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() != null && !event.getPlayer().level().isClientSide) {
            ItemStack stack = event.getItemEntity().getItem();
            if (isHot(stack)) {
                event.getPlayer().getInventory().setChanged();
                event.getPlayer().containerMenu.broadcastChanges();
            }
        }
    }

    public static void clearHotTags(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove("HotTime");
            tag.remove("HotTimeMax");
            tag.remove("MeltingPoint");
            tag.remove("CooledInPot");
        });
    }

    public static boolean isHot(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("HotTime")) return false;
        return tag.getFloat("HotTime") > 0.5f;
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!isHot(stack)) return;

        if (stack.getItem() instanceof SlagItem) return;

        int meltingPoint = getMeltingPoint(stack);
        if (meltingPoint <= 0) meltingPoint = 1000;

        int temperature = getTemperature(stack);
        float heatRatio = getHeatRatio(stack);
        int percent = (int) (heatRatio * 100);
        boolean cooledInPot = wasCooledInPot(stack);

        HeatStatus status = getHeatStatus(temperature);
        String source = cooledInPot ? " §8[Охл.]" : "";

        event.getToolTip().add(Component.literal(""));
        event.getToolTip().add(Component.literal("")
                .append(Component.literal("||").withStyle(status.color))
                .append(Component.literal(status.label).withStyle(status.color, ChatFormatting.BOLD))
                .append(Component.literal("||").withStyle(status.color))
                .append(Component.literal(source)));

        event.getToolTip().add(Component.literal(String.format("  §c%d°C §7/ §c%d°C §7(%d%%)",
                temperature, meltingPoint, percent)));

        if (!status.warning.isEmpty()) {
            event.getToolTip().add(Component.literal(status.warning));
        }
    }
}
