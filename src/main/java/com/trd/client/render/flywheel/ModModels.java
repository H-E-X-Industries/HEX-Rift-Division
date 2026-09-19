package com.trd.client.render.flywheel;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class ModModels {
    public static final Map<String, PartialModel> GEAR_MODELS = new HashMap<>();
    public static final Map<String, PartialModel> SHAFT_MODELS = new HashMap<>();

    public static final PartialModel MOTOR_BASE = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/electro_motor"));
    public static final PartialModel HALF_SHAFT = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/half_shaft"));
    public static final PartialModel BEARING_INNER_RING = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/bearing_shaft"));
    public static final PartialModel BEARING = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/bearing"));
    public static final PartialModel BEVEL_GEAR = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/bevel_gear"));
    public static final PartialModel TACHOMETER = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/tachometr"));
    public static final PartialModel CLUTCH_BLOCK = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/clutch"));
    public static final Map<String, PartialModel> PULLEY_MODELS = new HashMap<>();
    public static final PartialModel BELT_SEGMENT = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/belt_segment"));

    public static final PartialModel COPPER_ROTOR = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/rotor"));
    public static final PartialModel FLYWHEEL = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/flywheel_light"));

    public static final PartialModel STATOR = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/stator"));
    public static final Map<String, PartialModel> STATOR_COILS = new HashMap<>();


    public static final PartialModel FUEL_TANK_BIG = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/fuel_tank_big"));
    public static final PartialModel FUEL_TANK_SMALL = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/fuel_tank_small"));
    public static final PartialModel BOILER = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/boiler"));

    public static final PartialModel STEAM_ENGINE_BASE = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/steam_engine_base"));
    public static final PartialModel STEAM_ENGINE_CRANKSHAFT = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/crankshaft"));
    public static final PartialModel STEAM_ENGINE_ROD = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/connecting_rod"));

    public static final PartialModel JERNOVA_BASE = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/jernova_2"));
    public static final PartialModel JERNOVA_TOP = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/jernova_1"));

    public static final PartialModel WATER_PUMP = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/water_pump"));
    public static final PartialModel HAND_CRANK = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/handle"));
    public static final PartialModel CRUSHER_BLADES = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/crusher_blades"));
    public static final PartialModel VISHELACHIVATEL_LOPASTI = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/vishelashivatel_lopasti"));

    // Станок
    public static final PartialModel STANOK_BASE             = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/stanok"));
    public static final PartialModel STANOK_PRESS_CARRIAGE   = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/press_carriage"));
    public static final PartialModel STANOK_PRESS_HEAD       = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/press_head"));
    public static final PartialModel STANOK_WIRE_CARRIAGE    = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/wire_carriage"));
    public static final PartialModel STANOK_WIRE_DRUM        = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/wire_drum"));
    public static final PartialModel STANOK_FREZA_CARRIAGE   = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/freza_carriage"));
    public static final PartialModel STANOK_FREZA_ATTACHMENT = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/freza_attachment"));
    public static final PartialModel STANOK_FREZA            = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/freza"));
    
    public static final PartialModel CENTRIFUGE_CYLINDER_LOPASTI = PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/centrifuge_cylinder_lopasti"));

    static {
        String[] materials = {"iron", "duralumin", "steel", "titanium", "tungsten_carbide"};
        int[] gearSizes = {1, 2, 3};
        for (int size : gearSizes) {
            for (String mat : materials) {
                String name = "gear" + size + "_" + mat; 
                GEAR_MODELS.put(name, PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/" + name)));
            }
        }

        String[] diameters = {"light", "medium", "heavy"};
        for (String dia : diameters) {
            for (String mat : materials) {
                String name = "shaft_" + dia + "_" + mat; 
                SHAFT_MODELS.put(name, PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/" + name)));
            }
        }

        PULLEY_MODELS.put("pulley", PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/pulley")));
        STATOR_COILS.put("copper", PartialModel.of(ResourceLocation.fromNamespaceAndPath("trd", "block/stator_coil_copper")));
    }

    public static void init() {}
}