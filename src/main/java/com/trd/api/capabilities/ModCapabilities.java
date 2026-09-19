package com.trd.api.capabilities;

import com.trd.main.MainRegistry;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Универсальная система Capabilities для 1.21.1
 * (Замена старой системе LazyOptional из 1.20.1)
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModCapabilities {

    // =========================================================
    // СОЗДАНИЕ КАСТОМНЫХ КАПАБИЛИТИ
    // =========================================================

    // Пример кастомного Capability для вашей собственной системы Энергии (если не используется IEnergyStorage от NeoForge)
    // Тип <Object, Direction> означает: интерфейс капабилити = Object (замените на ваш IEnergyProvider), контекст = Direction (сторона)
    public static final BlockCapability<Object, @Nullable Direction> HEX_ENERGY_PROVIDER = 
            BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "hex_energy_provider"), Object.class);

    public static final BlockCapability<Object, @Nullable Direction> HEX_ENERGY_RECEIVER = 
            BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "hex_energy_receiver"), Object.class);

    // Пример кастомного Capability для Кинетической системы
    public static final BlockCapability<Object, @Nullable Direction> KINETIC_HANDLER = 
            BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "kinetic_handler"), Object.class);

    // =========================================================
    // РЕГИСТРАЦИЯ ВСЕХ КАПАБИЛИТИ К БЛОК-ЭНТИТИ
    // =========================================================
    
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Здесь мы привязываем Capability к конкретным BlockEntity
        // В 1.21.1 больше не нужно делать getCapability() внутри самого BlockEntity и возвращать LazyOptional!

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.MILLSTONE.get(),
                (blockEntity, side) -> blockEntity.getItemHandler(side)
        );
    }
}
