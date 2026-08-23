package com.trd.menu;

import com.trd.main.MainRegistry;
import com.trd.menu.industrial.*;
import com.trd.menu.rotation.MotorElectroMenu;
import com.trd.menu.turrets.TromboneMenu;
import com.trd.menu.turrets.TurretLightMenu;
import com.trd.multiblock.industrial.steel_storage.SteelStorageBlockEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MainRegistry.MOD_ID);


    public static final RegistryObject<MenuType<MachineBatteryMenu>> MACHINE_BATTERY_MENU =
            MENUS.register("machine_battery_menu", () -> IForgeMenuType.create(MachineBatteryMenu::new));

//    public static final RegistryObject<MenuType<MotorElectroMenu>> MOTOR_ELECTRO_MENU =
//            MENUS.register("motor_electro_menu", () -> IForgeMenuType.create(MotorElectroMenu::new));

    public static final RegistryObject<MenuType<MotorElectroMenu>> MOTOR_ELECTRO_MENU =
            MENUS.register("motor_electro_menu", () -> IForgeMenuType.create(MotorElectroMenu::new));

    public static final RegistryObject<MenuType<TurretLightMenu>> TURRET_AMMO_MENU =
            MENUS.register("turret_ammo", () -> IForgeMenuType.create((windowId, inv, data) -> {
                // Вызываем конструктор: TurretLightMenu(int, Inventory, FriendlyByteBuf)
                return new TurretLightMenu(windowId, inv, data);
            }));

    public static final RegistryObject<MenuType<ChemicalPlantReactionChamberMenu>> CHEMICAL_PLANT_REACTION_CHAMBER_MENU =
            registerMenuType("chemical_plant_reaction_chamber_menu",
                    (IContainerFactory<ChemicalPlantReactionChamberMenu>) ChemicalPlantReactionChamberMenu::new);
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<com.trd.menu.industrial.CoccerOvenMenu>> COCCER_OVEN_MENU =
            MENUS.register("coccer_oven",
                    () -> new net.minecraft.world.inventory.MenuType<>((net.minecraftforge.network.IContainerFactory<com.trd.menu.industrial.CoccerOvenMenu>) (windowId, inv, data) -> {
                        net.minecraft.core.BlockPos pos = data.readBlockPos();
                        if (inv.player.level().getBlockEntity(pos) instanceof com.trd.multiblock.industrial.coccer.CoccerOvenBlockEntity be) {
                            return new com.trd.menu.industrial.CoccerOvenMenu(windowId, inv, be, be.getContainerData());
                        }
                        return null;
                    }, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<MenuType<ChemicalPlantPortMenu>> CHEMICAL_PLANT_PORT_MENU =
            registerMenuType("chemical_plant_port_menu",
                    (IContainerFactory<ChemicalPlantPortMenu>) ChemicalPlantPortMenu::new);

    public static final RegistryObject<MenuType<DrobitelMenu>> DROBITEL_MENU =
            MENUS.register("drobitel_menu", () -> IForgeMenuType.create(DrobitelMenu::new));

    public static final RegistryObject<MenuType<VishelashivatelMenu>> VISHELASHIVATEL_MENU =
            MENUS.register("vishelashivatel_menu", () -> IForgeMenuType.create(VishelashivatelMenu::new));

    public static final RegistryObject<MenuType<CentrifugeMenu>> CENTRIFUGE_MENU =
            MENUS.register("centrifuge_menu", () -> IForgeMenuType.create(CentrifugeMenu::new));

    public static final RegistryObject<MenuType<StanokMenu>> STANOK_MENU =
            MENUS.register("stanok_menu", () -> IForgeMenuType.create(StanokMenu::new));

    public static final RegistryObject<MenuType<FuelTankMenu>> FUEL_TANK_MENU =
            MENUS.register("fuel_tank_big",
                    () -> IForgeMenuType.create((windowId, inv, data) -> new FuelTankMenu(windowId, inv, data)));

    public static final RegistryObject<MenuType<ElectricFurnaceMenu>> ELECTRIC_FURNACE_MENU =
            MENUS.register("electric_furnace",
                    () -> IForgeMenuType.create((windowId, inv, data) -> new ElectricFurnaceMenu(windowId, inv, data)));
    public static final RegistryObject<MenuType<SmallSmelterMenu>> SMALL_SMELTER_MENU =
            MENUS.register("small_smelter_menu", () -> IForgeMenuType.create(SmallSmelterMenu::create));

    public static final RegistryObject<MenuType<ConveyorBufferMenu>> CONVEYOR_BUFFER =
            MENUS.register("conveyor_buffer", () -> net.minecraftforge.common.extensions.IForgeMenuType.create(ConveyorBufferMenu::new));

    public static final RegistryObject<MenuType<SortirovshikMenu>> SORTIROVSHIK_MENU =
            MENUS.register("sortirovshik_menu", () -> IForgeMenuType.create(SortirovshikMenu::new));

    public static final RegistryObject<MenuType<SteelStorageMenu>> STEEL_STORAGE_MENU =
            MENUS.register("steel_storage_menu", () -> net.minecraftforge.common.extensions.IForgeMenuType.create(
                    (windowId, inv, data) -> {
                        net.minecraft.core.BlockPos pos = data.readBlockPos();
                        if (inv.player.level().getBlockEntity(pos) instanceof SteelStorageBlockEntity be) {
                            return new SteelStorageMenu(windowId, inv, be);
                        }
                        return null;
                    }
            ));

    public static final RegistryObject<MenuType<HeaterMenu>> HEATER_MENU =
            MENUS.register("heater_menu", () -> IForgeMenuType.create(HeaterMenu::create));

//    public static final RegistryObject<MenuType<ShaftPlacerMenu>> SHAFT_PLACER_MENU =
//            MENUS.register("shaft_placer_menu",
//                    () -> IForgeMenuType.create(ShaftPlacerMenu::new));

    public static final RegistryObject<MenuType<SmelterMenu>> SMELTER_MENU =
            MENUS.register("smelter_menu", () -> IForgeMenuType.create(SmelterMenu::create));

//    public static final RegistryObject<MenuType<MiningPortMenu>> MINING_PORT_MENU =
//            MENUS.register("mining_port_menu",
//                    () -> IForgeMenuType.create(MiningPortMenu::new));

    public static final RegistryObject<MenuType<TromboneMenu>> TROMBONE_MENU =
            MENUS.register("trombone_menu",
                    () -> IForgeMenuType.create((windowId, inv, data) -> new TromboneMenu(windowId, inv, data)));

    public static final RegistryObject<MenuType<FluidBarrelMenu>> FLUID_BARREL_MENU =
            MENUS.register("fluid_barrel_menu",
                    () -> IForgeMenuType.create(FluidBarrelMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
    private static <T extends AbstractContainerMenu> RegistryObject<MenuType<T>> registerMenuType(String name, IContainerFactory<T> factory) {
        return MENUS.register(name, () -> IForgeMenuType.create(factory));
    }
}