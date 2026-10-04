package com.trd.item.weapons.ammo;

import com.trd.item.ModItems;
import com.trd.main.MainRegistry;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Прокидывает патроны в {@link AmmoRegistry}, чтобы турели и пулемёт подбирали
 * их по калибру. Регистрация идёт в common setup, потому что реестр читается
 * уже во время игры, а не при загрузке модов.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class AmmoBootstrap {

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Порядок неважен для реестра, но важен для списка патронов калибра:
            // AmmoRegistry#register складывает их в один список по порядку, и по
            // нему турель выбирает боеприпас. Сначала обычные, потом трассирующие
            // того же типа — чтобы пустая турель по умолчанию брала обычный
            // патрон, а не трассер.
            registerAmmo(ModItems.TURRET_AMMO.get());
            registerAmmo(ModItems.TURRET_AMMO_AP.get());
            registerAmmo(ModItems.TURRET_AMMO_HOLLOW.get());
            registerAmmo(ModItems.TURRET_AMMO_FIRE.get());
            registerAmmo(ModItems.TURRET_AMMO_RADIO.get());

            registerAmmo(ModItems.TURRET_AMMO_TRACER.get());
            registerAmmo(ModItems.TURRET_AMMO_AP_TRACER.get());
            registerAmmo(ModItems.TURRET_AMMO_HOLLOW_TRACER.get());
            registerAmmo(ModItems.TURRET_AMMO_FIRE_TRACER.get());
            registerAmmo(ModItems.TURRET_AMMO_RADIO_TRACER.get());

            registerAmmo(ModItems.TURRET_AMMO_HE.get());
            registerAmmo(ModItems.TURRET_AMMO_HE_TRACER.get());
            registerAmmo(ModItems.TURRET_AMMO_HE_SAVE.get());
            registerAmmo(ModItems.TURRET_AMMO_HE_SAVE_TRACER.get());
        });
    }

    private static void registerAmmo(Item item) {
        if (item instanceof AmmoTurretItem ammoItem) {
            AmmoRegistry.register(
                    item,
                    ammoItem.getCaliber(),
                    ammoItem.getDamage(),
                    ammoItem.getSpeed(),
                    ammoItem.isPiercing(),
                    ammoItem.isTracer()
            );
        }
    }
}
