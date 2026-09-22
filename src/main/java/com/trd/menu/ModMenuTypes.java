package com.trd.menu;

import com.trd.main.MainRegistry;
import com.trd.menu.industrial.MachineBatteryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenuTypes {
    
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MainRegistry.MOD_ID);
    public static final java.util.function.Supplier<MenuType<com.trd.menu.industrial.ElectricFurnaceMenu>> ELECTRIC_FURNACE_MENU = MENUS.register("electric_furnace_menu", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id, inv, data) -> new com.trd.menu.industrial.ElectricFurnaceMenu(id, inv, data)));

    

    public static final java.util.function.Supplier<net.minecraft.world.inventory.MenuType<com.trd.menu.industrial.FluidBarrelMenu>> FLUID_BARREL_MENU = 
            MENUS.register("fluid_barrel_menu", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.trd.menu.industrial.FluidBarrelMenu::new));
            
    public static final java.util.function.Supplier<net.minecraft.world.inventory.MenuType<com.trd.menu.industrial.FuelTankMenu>> FUEL_TANK_MENU = 
            MENUS.register("fuel_tank_menu", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.trd.menu.industrial.FuelTankMenu::new));
    public static final Supplier<MenuType<MachineBatteryMenu>> MACHINE_BATTERY_MENU =
            MENUS.register("machine_battery_menu", () -> IMenuTypeExtension.create(MachineBatteryMenu::new));
    public static final java.util.function.Supplier<MenuType<com.trd.menu.industrial.ConveyorBufferMenu>> CONVEYOR_BUFFER_MENU =
            MENUS.register("conveyor_buffer", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.trd.menu.industrial.ConveyorBufferMenu::new));
    public static final java.util.function.Supplier<MenuType<com.trd.menu.industrial.SortirovshikMenu>> SORTIROVSHIK_MENU =
            MENUS.register("sortirovshik_menu", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.trd.menu.industrial.SortirovshikMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
