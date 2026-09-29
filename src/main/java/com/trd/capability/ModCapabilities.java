package com.trd.capability;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import com.trd.api.energy.IEnergyConnector;
import com.trd.api.energy.IEnergyProvider;
import com.trd.api.energy.IEnergyReceiver;
import com.trd.main.MainRegistry;

@net.neoforged.fml.common.EventBusSubscriber(modid = com.trd.main.MainRegistry.MOD_ID)
public class ModCapabilities {
    public static final net.neoforged.neoforge.capabilities.ItemCapability<com.trd.api.energy.IEnergyProvider, Void> ENERGY_PROVIDER_ITEM = net.neoforged.neoforge.capabilities.ItemCapability.createVoid(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID, "energy_provider"), com.trd.api.energy.IEnergyProvider.class);
    public static final net.neoforged.neoforge.capabilities.ItemCapability<com.trd.api.energy.IEnergyReceiver, Void> ENERGY_RECEIVER_ITEM = net.neoforged.neoforge.capabilities.ItemCapability.createVoid(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID, "energy_receiver"), com.trd.api.energy.IEnergyReceiver.class);

    public static final BlockCapability<IEnergyProvider, Direction> ENERGY_PROVIDER = 
        BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "energy_provider"), IEnergyProvider.class);
        
    public static final BlockCapability<IEnergyReceiver, Direction> ENERGY_RECEIVER = 
        BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "energy_receiver"), IEnergyReceiver.class);
        
    public static final BlockCapability<IEnergyConnector, Direction> ENERGY_CONNECTOR = 
        BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "energy_connector"), IEnergyConnector.class);

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        // IEnergyProvider
        event.registerBlockEntity(
                ENERGY_PROVIDER,
                com.trd.block.entity.ModBlockEntities.MACHINE_BATTERY_BE.get(),
                (be, side) -> be.getEnergyProvider(side)
        );
        event.registerBlockEntity(
                ENERGY_PROVIDER,
                com.trd.block.entity.ModBlockEntities.CONVERTER_BE.get(),
                (be, side) -> be.getEnergyProvider(side)
        );
        event.registerBlockEntity(
                ENERGY_PROVIDER,
                com.trd.block.entity.ModBlockEntities.STATOR_BE.get(),
                (be, side) -> be.getEnergyProvider(side)
        );
        event.registerBlockEntity(
                ENERGY_PROVIDER,
                com.trd.block.entity.ModBlockEntities.MULTIBLOCK_PART.get(),
                (be, side) -> {
                    if (be.getControllerPos() != null && be.getLevel() != null) {
                        com.trd.multiblock.system.PartRole role = be.getPartRole();
                        if (role == com.trd.multiblock.system.PartRole.ENERGY_CONNECTOR || role == com.trd.multiblock.system.PartRole.UNIVERSAL_CONNECTOR) {
                            return be.getLevel().getCapability(ENERGY_PROVIDER, be.getControllerPos(), side);
                        }
                    }
                    return null;
                }
        );

        // IEnergyReceiver
        event.registerBlockEntity(
                ENERGY_RECEIVER,
                com.trd.block.entity.ModBlockEntities.MACHINE_BATTERY_BE.get(),
                (be, side) -> be.getEnergyReceiver(side)
        );
        event.registerBlockEntity(
                ENERGY_RECEIVER,
                com.trd.block.entity.ModBlockEntities.ELECTRIC_FURNACE_BE.get(),
                (be, side) -> be.getEnergyReceiver(side)
        );
        event.registerBlockEntity(
                ENERGY_RECEIVER,
                com.trd.block.entity.ModBlockEntities.CONVERTER_BE.get(),
                (be, side) -> be.getEnergyReceiver(side)
        );
        event.registerBlockEntity(
                ENERGY_RECEIVER,
                com.trd.block.entity.ModBlockEntities.MOTOR_ELECTRO_BE.get(),
                (be, side) -> be.getEnergyReceiver(side)
        );
        event.registerBlockEntity(
                ENERGY_RECEIVER,
                com.trd.block.entity.ModBlockEntities.CENTRIFUGE_MOTOR_BE.get(),
                (be, side) -> be.getEnergyReceiver(side)
        );
        event.registerBlockEntity(
                ENERGY_RECEIVER,
                com.trd.block.entity.ModBlockEntities.CENTRIFUGE_CONUS_BE.get(),
                (be, side) -> {
                    if (be.getLevel() != null) {
                        net.minecraft.world.level.block.entity.BlockEntity motor = be.getLevel().getBlockEntity(be.getBlockPos().below());
                        if (motor instanceof com.trd.multiblock.industrial.centrifuge.CentrifugeMotorBlockEntity m) {
                            return m.getEnergyReceiver(side);
                        }
                    }
                    return null;
                }
        );
        event.registerBlockEntity(
                ENERGY_RECEIVER,
                com.trd.block.entity.ModBlockEntities.CENTRIFUGE_CYLINDER_BE.get(),
                (be, side) -> {
                    if (be.getLevel() != null) {
                        net.minecraft.world.level.block.entity.BlockEntity motor = be.getLevel().getBlockEntity(be.getBlockPos().below());
                        if (motor instanceof com.trd.multiblock.industrial.centrifuge.CentrifugeMotorBlockEntity m) {
                            return m.getEnergyReceiver(side);
                        }
                    }
                    return null;
                }
        );
        event.registerBlockEntity(
                ENERGY_RECEIVER,
                com.trd.block.entity.ModBlockEntities.MULTIBLOCK_PART.get(),
                (be, side) -> {
                    if (be.getControllerPos() != null && be.getLevel() != null) {
                        com.trd.multiblock.system.PartRole role = be.getPartRole();
                        if (role == com.trd.multiblock.system.PartRole.ENERGY_CONNECTOR || role == com.trd.multiblock.system.PartRole.UNIVERSAL_CONNECTOR || role == com.trd.multiblock.system.PartRole.DEFAULT) {
                            com.trd.api.energy.IEnergyReceiver rec = be.getLevel().getCapability(ENERGY_RECEIVER, be.getControllerPos(), side);
                            if (rec != null) return rec;
                            net.minecraft.world.level.block.entity.BlockEntity motor = be.getLevel().getBlockEntity(be.getControllerPos().below());
                            if (motor instanceof com.trd.multiblock.industrial.centrifuge.CentrifugeMotorBlockEntity m) {
                                return m.getEnergyReceiver(side);
                            }
                        }
                    }
                    return null;
                }
        );
        event.registerBlockEntity(
                ENERGY_RECEIVER,
                com.trd.block.entity.ModBlockEntities.CHEMICAL_PLANT_HEATER_BE.get(),
                (be, side) -> be.getEnergyReceiver(side)
        );

        // IEnergyConnector
        event.registerBlockEntity(
                ENERGY_CONNECTOR,
                com.trd.block.entity.ModBlockEntities.MACHINE_BATTERY_BE.get(),
                (be, side) -> be.getEnergyConnector(side)
        );
        event.registerBlockEntity(
                ENERGY_CONNECTOR,
                com.trd.block.entity.ModBlockEntities.ELECTRIC_FURNACE_BE.get(),
                (be, side) -> be.getEnergyConnector(side)
        );
        event.registerBlockEntity(
                ENERGY_CONNECTOR,
                com.trd.block.entity.ModBlockEntities.CONVERTER_BE.get(),
                (be, side) -> be.getEnergyConnector(side)
        );
        event.registerBlockEntity(
                ENERGY_CONNECTOR,
                com.trd.block.entity.ModBlockEntities.WIRE_BE.get(),
                (be, side) -> be.getEnergyConnector(side)
        );
        event.registerBlockEntity(
                ENERGY_CONNECTOR,
                com.trd.block.entity.ModBlockEntities.CONNECTOR_BE.get(),
                (be, side) -> be.getEnergyConnector(side)
        );
        event.registerBlockEntity(
                ENERGY_CONNECTOR,
                com.trd.block.entity.ModBlockEntities.SWITCH_BE.get(),
                (be, side) -> be.getEnergyConnector(side)
        );
        event.registerBlockEntity(
                ENERGY_CONNECTOR,
                com.trd.block.entity.ModBlockEntities.MOTOR_ELECTRO_BE.get(),
                (be, side) -> be.getEnergyConnector(side)
        );
        event.registerBlockEntity(
                ENERGY_CONNECTOR,
                com.trd.block.entity.ModBlockEntities.STATOR_BE.get(),
                (be, side) -> be.getEnergyConnector(side)
        );
        event.registerBlockEntity(
                ENERGY_CONNECTOR,
                com.trd.block.entity.ModBlockEntities.CENTRIFUGE_MOTOR_BE.get(),
                (be, side) -> be.getEnergyConnector(side)
        );
        event.registerBlockEntity(
                ENERGY_CONNECTOR,
                com.trd.block.entity.ModBlockEntities.CENTRIFUGE_CONUS_BE.get(),
                (be, side) -> {
                    if (be.getLevel() != null) {
                        net.minecraft.world.level.block.entity.BlockEntity motor = be.getLevel().getBlockEntity(be.getBlockPos().below());
                        if (motor instanceof com.trd.multiblock.industrial.centrifuge.CentrifugeMotorBlockEntity m) {
                            return m.getEnergyConnector(side);
                        }
                    }
                    return null;
                }
        );
        event.registerBlockEntity(
                ENERGY_CONNECTOR,
                com.trd.block.entity.ModBlockEntities.CENTRIFUGE_CYLINDER_BE.get(),
                (be, side) -> {
                    if (be.getLevel() != null) {
                        net.minecraft.world.level.block.entity.BlockEntity motor = be.getLevel().getBlockEntity(be.getBlockPos().below());
                        if (motor instanceof com.trd.multiblock.industrial.centrifuge.CentrifugeMotorBlockEntity m) {
                            return m.getEnergyConnector(side);
                        }
                    }
                    return null;
                }
        );
        event.registerBlockEntity(
                ENERGY_CONNECTOR,
                com.trd.block.entity.ModBlockEntities.MULTIBLOCK_PART.get(),
                (be, side) -> {
                    if (be.getControllerPos() != null && be.getLevel() != null) {
                        com.trd.multiblock.system.PartRole role = be.getPartRole();
                        if (role == com.trd.multiblock.system.PartRole.ENERGY_CONNECTOR || role == com.trd.multiblock.system.PartRole.UNIVERSAL_CONNECTOR || role == com.trd.multiblock.system.PartRole.DEFAULT) {
                            com.trd.api.energy.IEnergyConnector conn = be.getLevel().getCapability(ENERGY_CONNECTOR, be.getControllerPos(), side);
                            if (conn != null) return conn;
                            net.minecraft.world.level.block.entity.BlockEntity motor = be.getLevel().getBlockEntity(be.getControllerPos().below());
                            if (motor instanceof com.trd.multiblock.industrial.centrifuge.CentrifugeMotorBlockEntity m) {
                                return m.getEnergyConnector(side);
                            }
                        }
                    }
                    return null;
                }
        );
        event.registerBlockEntity(
                ENERGY_CONNECTOR,
                com.trd.block.entity.ModBlockEntities.CHEMICAL_PLANT_HEATER_BE.get(),
                (be, side) -> be.getEnergyConnector(side)
        );

        // Forge Energy Block Capability
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                com.trd.block.entity.ModBlockEntities.MACHINE_BATTERY_BE.get(),
                (be, side) -> be.getFeCapabilityProvider().getCapability(side)
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                com.trd.block.entity.ModBlockEntities.CONVERTER_BE.get(),
                (be, side) -> be.getFeCapabilityProvider().getCapability(side)
        );

        // Forge Item Handler Block Capability
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.MISSILE_TURRET_BE.get(),
                (be, side) -> be.getItemHandler()
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.TURRET_LIGHT_PLACER_BE.get(),
                (be, side) -> be.getItemHandler()
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.MACHINE_BATTERY_BE.get(),
                (be, side) -> be.getItemHandler()
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.ELECTRIC_FURNACE_BE.get(),
                (be, side) -> be.getItemHandler(side)
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.HEATER_BE.get(),
                (be, side) -> be.getItemHandler(side)
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.STATOR_BE.get(),
                (be, side) -> be.getCoilsInventory()
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.SMELTER_BE.get(),
                (be, side) -> be.getItemHandler(side)
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.CC_MACHINE_BE.get(),
                (be, side) -> be.getAutomationItemHandler()
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.CASTING_POT.get(),
                (be, side) -> be.getItemHandler()
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.CENTRIFUGE_MOTOR_BE.get(),
                (be, side) -> be.getItemHandler(side)
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.DROBITEL_BE.get(),
                (be, side) -> side == null ? be.getInventory() : null
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.VISHELASHIVATEL_BE.get(),
                (be, side) -> side == null ? be.getSelfHandler() : null
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.MULTIBLOCK_PART.get(),
                (be, side) -> {
                    if (be.getControllerPos() != null && be.getLevel() != null) {
                        net.minecraft.world.level.block.entity.BlockEntity core = be.getLevel().getBlockEntity(be.getControllerPos());
                        if (core instanceof com.trd.multiblock.industrial.smelter.SmelterBlockEntity smelter) {
                            return smelter.getItemHandler(side);
                        }
                        if (core instanceof com.trd.multiblock.industrial.ccmachine.CCMachineBlockEntity ccm) {
                            if (be.getPartRole() == com.trd.multiblock.system.PartRole.UNIVERSAL_CONNECTOR) {
                                return ccm.getAutomationItemHandler();
                            }
                        }
                        if (core instanceof com.trd.multiblock.industrial.drobitel.DrobitelBlockEntity drobitel) {
                            if (be.getPartRole() == com.trd.multiblock.system.PartRole.CARGO_PORT) {
                                net.minecraft.core.Direction facing = drobitel.getBlockState().getValue(com.trd.multiblock.industrial.drobitel.DrobitelBlock.FACING);
                                if (side != null && side.getAxis() == facing.getAxis()) {
                                    return drobitel.getCargoPortCapability();
                                }
                                return null;
                            }
                        }
                        if (core instanceof com.trd.multiblock.industrial.vishelashivatel.VishelashivatelBlockEntity leacher) {
                            if (be.getPartRole() == com.trd.multiblock.system.PartRole.FLUID_INPUT) {
                                return leacher.getItemPortCapability();
                            }
                        }
                        if (core instanceof com.trd.multiblock.industrial.stanok.StanokBlockEntity stanok) {
                            if (be.getPartRole() == com.trd.multiblock.system.PartRole.CARGO_PORT) {
                                net.minecraft.core.Direction facing = stanok.getBlockState().getValue(com.trd.multiblock.industrial.stanok.StanokBlock.FACING);
                                if (side == facing.getClockWise() || side == facing.getCounterClockWise()) {
                                    return stanok.getCargoPortCapability();
                                }
                                return null;
                            }
                        }
                        if (core instanceof com.trd.multiblock.industrial.steel_storage.SteelStorageBlockEntity steelStorage) {
                            return steelStorage.getInventory();
                        }
                    }
                    return null;
                }
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.STEEL_STORAGE_BE.get(),
                (be, side) -> be.getInventory()
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.CHEMICAL_PLANT_REACTION_CHAMBER_BE.get(),
                (be, side) -> be.getItemHandler()
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.CHEMICAL_PLANT_PORT_BE.get(),
                (be, side) -> be.getItemHandler(side)
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.COCCER_OVEN_BE.get(),
                (be, side) -> be.getInventory()
        );
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.trd.block.entity.ModBlockEntities.STANOK_BE.get(),
                (be, side) -> null
        );
    // Item Capabilities
    event.registerItem(ENERGY_PROVIDER_ITEM, (stack, ctx) -> new com.trd.api.energy.ItemEnergyStorage(stack, 5000, 100, 100), com.trd.item.ModItems.BATTERY.get());
    event.registerItem(ENERGY_RECEIVER_ITEM, (stack, ctx) -> new com.trd.api.energy.ItemEnergyStorage(stack, 5000, 100, 100), com.trd.item.ModItems.BATTERY.get());
    event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new com.trd.api.energy.ForgeItemWrapper(new com.trd.api.energy.ItemEnergyStorage(stack, 5000, 100, 100)), com.trd.item.ModItems.BATTERY.get());

    event.registerItem(ENERGY_PROVIDER_ITEM, (stack, ctx) -> new com.trd.api.energy.ItemEnergyStorage(stack, 20000, 500, 500), com.trd.item.ModItems.BATTERY_ADVANCED.get());
    event.registerItem(ENERGY_RECEIVER_ITEM, (stack, ctx) -> new com.trd.api.energy.ItemEnergyStorage(stack, 20000, 500, 500), com.trd.item.ModItems.BATTERY_ADVANCED.get());
    event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new com.trd.api.energy.ForgeItemWrapper(new com.trd.api.energy.ItemEnergyStorage(stack, 20000, 500, 500)), com.trd.item.ModItems.BATTERY_ADVANCED.get());

    event.registerItem(ENERGY_PROVIDER_ITEM, (stack, ctx) -> new com.trd.api.energy.ItemEnergyStorage(stack, 250000, 1000, 1000), com.trd.item.ModItems.BATTERY_LITHIUM.get());
    event.registerItem(ENERGY_RECEIVER_ITEM, (stack, ctx) -> new com.trd.api.energy.ItemEnergyStorage(stack, 250000, 1000, 1000), com.trd.item.ModItems.BATTERY_LITHIUM.get());
    event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new com.trd.api.energy.ForgeItemWrapper(new com.trd.api.energy.ItemEnergyStorage(stack, 250000, 1000, 1000)), com.trd.item.ModItems.BATTERY_LITHIUM.get());

    event.registerItem(ENERGY_PROVIDER_ITEM, (stack, ctx) -> new com.trd.api.energy.ItemEnergyStorage(stack, 5000000, 40000, 200000), com.trd.item.ModItems.BATTERY_TRIXITE.get());
    event.registerItem(ENERGY_RECEIVER_ITEM, (stack, ctx) -> new com.trd.api.energy.ItemEnergyStorage(stack, 5000000, 40000, 200000), com.trd.item.ModItems.BATTERY_TRIXITE.get());
    event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new com.trd.api.energy.ForgeItemWrapper(new com.trd.api.energy.ItemEnergyStorage(stack, 5000000, 40000, 200000)), com.trd.item.ModItems.BATTERY_TRIXITE.get());

    var creativeStorage = new com.trd.api.energy.ItemEnergyStorage(net.minecraft.world.item.ItemStack.EMPTY, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE) {
        @Override public long getEnergyStored() { return Long.MAX_VALUE / 2; } // Using half max to prevent overflow issues
        @Override public void setEnergyStored(long energy) {}
        @Override public long extractEnergy(long maxExtract, boolean simulate) { return maxExtract; }
        @Override public long receiveEnergy(long maxReceive, boolean simulate) { return 0; }
        @Override public boolean canReceive() { return false; }
        @Override public boolean canExtract() { return true; }
    };
    
    event.registerItem(ENERGY_PROVIDER_ITEM, (stack, ctx) -> creativeStorage, com.trd.item.ModItems.CREATIVE_BATTERY.get());
    event.registerItem(ENERGY_RECEIVER_ITEM, (stack, ctx) -> creativeStorage, com.trd.item.ModItems.CREATIVE_BATTERY.get());
    event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new com.trd.api.energy.ForgeItemWrapper(creativeStorage), com.trd.item.ModItems.CREATIVE_BATTERY.get());

    event.registerItem(ENERGY_PROVIDER_ITEM, (stack, ctx) -> new com.trd.api.energy.ItemEnergyStorage(stack, 1000000, 5000, 5000), com.trd.item.ModItems.ENERGY_CELL.get());
    event.registerItem(ENERGY_RECEIVER_ITEM, (stack, ctx) -> new com.trd.api.energy.ItemEnergyStorage(stack, 1000000, 5000, 5000), com.trd.item.ModItems.ENERGY_CELL.get());
    event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new com.trd.api.energy.ForgeItemWrapper(new com.trd.api.energy.ItemEnergyStorage(stack, 1000000, 5000, 5000)), com.trd.item.ModItems.ENERGY_CELL.get());

    // Fluid Capabilities
    event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            com.trd.block.entity.ModBlockEntities.MULTIBLOCK_PART.get(),
            (be, side) -> {
                if (be.getControllerPos() != null && be.getLevel() != null) {
                    com.trd.multiblock.system.PartRole role = be.getPartRole();
                    if (role == com.trd.multiblock.system.PartRole.FLUID_CONNECTOR || 
                        role == com.trd.multiblock.system.PartRole.UNIVERSAL_CONNECTOR || 
                        role == com.trd.multiblock.system.PartRole.FLUID_INPUT || 
                        role == com.trd.multiblock.system.PartRole.FLUID_OUTPUT || 
                        role == com.trd.multiblock.system.PartRole.FLUID_LADDER) {
                        
                        net.minecraft.world.level.block.entity.BlockEntity core = be.getLevel().getBlockEntity(be.getControllerPos());
                        if (core instanceof com.trd.multiblock.industrial.ccmachine.CCMachineBlockEntity ccm) {
                            return ccm.getPortFluidHandler(be.getBlockPos(), side);
                        } else if (core instanceof com.trd.multiblock.system.IMultiblockFluidHandler handler) {
                            return handler.getCapabilityForPart(side, role);
                        } else if (core instanceof com.trd.multiblock.system.IFluidTankProvider provider) {
                            return provider.getFluidHandlerCapability();
                        }
                    }
                }
                return null;
            }
    );

    event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            com.trd.block.entity.ModBlockEntities.CC_MACHINE_BE.get(),
            (be, side) -> be.getFluidHandler()
    );

    event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            com.trd.block.entity.ModBlockEntities.FUEL_TANK_BE.get(),
            (be, side) -> be.getFluidHandlerCapability()
    );

    event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            com.trd.block.entity.ModBlockEntities.FUEL_TANK_SMALL_BE.get(),
            (be, side) -> be.getFluidHandlerCapability()
    );

    event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            com.trd.block.entity.ModBlockEntities.FLUID_BARREL_BE.get(),
            (be, side) -> be.networkFluidHandler
    );

    event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            com.trd.block.entity.ModBlockEntities.LOW_PRESSURE_STEAM_CONDENSER_BE.get(),
            (be, side) -> be.getFluidHandler(side)
    );

    event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            com.trd.block.entity.ModBlockEntities.WATER_PUMP_BE.get(),
            (be, side) -> be.getFluidHandler(side)
    );

    event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            com.trd.block.entity.ModBlockEntities.CENTRIFUGE_MOTOR_BE.get(),
            (be, side) -> be.getFluidHandler(side)
    );

    event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            com.trd.block.entity.ModBlockEntities.CHEMICAL_PLANT_PORT_BE.get(),
            (be, side) -> be.getFluidHandler(side)
    );

    event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            com.trd.block.entity.ModBlockEntities.COCCER_OVEN_BE.get(),
            (be, side) -> be.getFluidHandlerCapability()
    );

    // Item Fluid Capabilities
    net.neoforged.neoforge.capabilities.ICapabilityProvider<net.minecraft.world.item.ItemStack, Void, net.neoforged.neoforge.fluids.capability.IFluidHandlerItem> fluidContainerProvider =
        (stack, ctx) -> new com.trd.item.industrial.fluids.FluidContainerItem.FluidHandler(stack, (com.trd.item.industrial.fluids.FluidContainerItem) stack.getItem());

    event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM, fluidContainerProvider,
        com.trd.item.ModItems.PIPETTE.get(),
        com.trd.item.ModItems.PIPETTE_IDUSTRIAL.get(),
        com.trd.item.ModItems.FLUID_TANK_IRON.get()
    );
}
}
