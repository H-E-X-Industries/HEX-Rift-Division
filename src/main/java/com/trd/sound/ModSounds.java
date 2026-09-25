package com.trd.sound;

import com.trd.main.MainRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MainRegistry.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> CRATE_BREAK = registerSoundEvents("crate_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> BULLET_GROUND = registerSoundEvents("bullet_ground");
    public static final DeferredHolder<SoundEvent, SoundEvent> BULLET_IMPACT = registerSoundEvents("bullet_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> TURRET_FIRE = registerSoundEvents("turret_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> TURRET_LOCK = registerSoundEvents("turret_lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRY_FIRE = registerSoundEvents("dry_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUNPULL = registerSoundEvents("gunpull");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_GUNCLICK = registerSoundEvents("heavy_gunclick");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUNCLICK = registerSoundEvents("gunclick");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUTTON1 = registerSoundEvents("button1");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVER1 = registerSoundEvents("lever1");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVER2 = registerSoundEvents("lever2");
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILE_LAUNCH2 = registerSoundEvents("missile_launch2");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOOL_TECH_BLEEP = registerSoundEvents("techbleep");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOOL_TECH_BOOP = registerSoundEvents("techboop");
    public static final DeferredHolder<SoundEvent, SoundEvent> PICKAXE_HIT = registerSoundEvents("pickaxe_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUKE_EXPLOSION = registerSoundEvents("mukeexplosion");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOUNCE_RANDOM = registerSoundEvents("item.bounce_random");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOILER = registerSoundEvents("boiler");
    public static final DeferredHolder<SoundEvent, SoundEvent> STANOK_PRESS = registerSoundEvents("stanok_press");
    public static final DeferredHolder<SoundEvent, SoundEvent> STANOK_WIRE = registerSoundEvents("stanok_wire");
    public static final DeferredHolder<SoundEvent, SoundEvent> STANOK_FREZA = registerSoundEvents("stanok_freza");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTOR_ELECTRO_START = registerSoundEvents("motor_electro_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTOR_ELECTRO_LOOP = registerSoundEvents("motor_electro_loop");

    private static DeferredHolder<SoundEvent, SoundEvent> registerSoundEvents(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
