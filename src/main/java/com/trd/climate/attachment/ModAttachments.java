package com.trd.climate.attachment;

import com.trd.climate.data.PlayerTemperatureData;
import com.trd.main.MainRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Регистрация Data Attachments для климатической системы.
 */
public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MainRegistry.MOD_ID);

    /**
     * Attachment для хранения температурных данных игрока.
     * copyOnDeath() сохраняет состояние при телепортациях и переходах измерений.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerTemperatureData>> PLAYER_TEMPERATURE =
            ATTACHMENT_TYPES.register("player_temperature", () ->
                    AttachmentType.builder(() -> new PlayerTemperatureData())
                            .serialize(PlayerTemperatureData.CODEC)
                            .copyOnDeath()
                            .build()
            );

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
