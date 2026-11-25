package com.trd.api.nature.climate.system.room;

import com.trd.api.nature.climate.system.HeatSourceDef;
import com.trd.api.nature.climate.system.HeatSourceRegistry;
import com.trd.main.MainRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Перенос запаса эффективности вместе с блоком.
 * <p>
 * Сломанный физичный источник записывает остаток запаса в компонент {@code CUSTOM_DATA}
 * своих дропов; при установке блок читает его обратно и продолжает с того же места.
 * Блок, который никогда не был в комнате, тега не имеет и остаётся «свежим» (запас 1).
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public final class ThermalSourceTransferHandler {

    /** Ключ в CUSTOM_DATA предмета. */
    private static final String RESERVE_TAG = "TRD_ThermalReserve";

    private ThermalSourceTransferHandler() {
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        HeatSourceDef def = HeatSourceRegistry.resolveState(event.getState());
        if (def == null || !def.isPassive()) {
            return;
        }

        ThermalStateStore.ThermalState state = ThermalStateStore.get(level).get(event.getPos());
        if (state == null || state.reserve() >= 1.0f) {
            return;
        }

        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            CustomData.update(DataComponents.CUSTOM_DATA, stack,
                    tag -> tag.putFloat(RESERVE_TAG, state.reserve()));
        }
        ThermalStateStore.get(level).remove(event.getPos());
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof Player player)) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        HeatSourceDef def = HeatSourceRegistry.resolveState(placed);
        if (def == null || !def.isPassive()) {
            return;
        }

        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.isEmpty() || !stack.has(DataComponents.CUSTOM_DATA)) {
                continue;
            }
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (!tag.contains(RESERVE_TAG)) {
                continue;
            }
            float reserve = tag.getFloat(RESERVE_TAG);
            // Новый блок пока нефизичный; контроллер комнаты активирует его при скане.
            ThermalStateStore.get(level).set(event.getPos(),
                    new ThermalStateStore.ThermalState(reserve, false));
            // Тег одноразовый: одна половинка льда не должна размножить свой запас.
            CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.remove(RESERVE_TAG));
            break;
        }
    }
}
