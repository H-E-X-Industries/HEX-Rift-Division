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
            registerAmmo(ModItems.AMMO_TURRET.get());
            registerAmmo(ModItems.AMMO_TURRET_PIERCING.get());
            registerAmmo(ModItems.AMMO_TURRET_HOLLOW.get());
            registerAmmo(ModItems.AMMO_TURRET_FIRE.get());
            registerAmmo(ModItems.AMMO_TURRET_RADIO.get());
        });
    }

    private static void registerAmmo(Item item) {
        if (item instanceof AmmoTurretItem ammoItem) {
            AmmoRegistry.register(
                    item,
                    ammoItem.getCaliber(),
                    ammoItem.getDamage(),
                    ammoItem.getSpeed(),
                    ammoItem.isPiercing()
            );
        }
    }
}
