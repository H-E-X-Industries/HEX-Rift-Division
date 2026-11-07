package com.trd.event;

import com.trd.item.climate.MercuryThermometerItem;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Разбитие ртутного термометра при падении.
 * <p>
 * Если выброшенный термометр касается земли (твёрдых блоков), он разбивается:
 * предмет исчезает, играет звук бьющегося стекла с небольшим случайным питчем.
 */
@EventBusSubscriber(modid = "trd")
public class MercuryThermometerBreakHandler {

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity itemEntity) || itemEntity.level().isClientSide()) {
            return;
        }

        ItemStack stack = itemEntity.getItem();
        if (!(stack.getItem() instanceof MercuryThermometerItem)) {
            return;
        }

        // Разбитие при касании твёрдых блоков (пол)
        if (itemEntity.onGround()) {
            ServerLevel level = (ServerLevel) itemEntity.level();

            // Звук битья стекла с небольшим разбросом питча (0.9..1.05)
            float pitch = 0.9f + level.getRandom().nextFloat() * 0.15f;
            level.playSound(null, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0f, pitch);

            // Капли ртути: серые бусины, разлетаются и медленно оседают (стиль протекающей бочки)
            DustParticleOptions mercuryDrops = new DustParticleOptions(
                    new org.joml.Vector3f(0.82f, 0.84f, 0.86f), 0.4f);
            level.sendParticles(mercuryDrops,
                    itemEntity.getX(), itemEntity.getY() + 0.1, itemEntity.getZ(),
                    16, 0.14, 0.06, 0.14, 0.03);

            // Осколки термометра: летят кусочки его иконки
            ItemParticleOption thermoShards = new ItemParticleOption(ParticleTypes.ITEM, stack);
            level.sendParticles(thermoShards,
                    itemEntity.getX(), itemEntity.getY() + 0.1, itemEntity.getZ(),
                    12, 0.12, 0.12, 0.12, 0.08);

            // Немного осколков стекла
            BlockParticleOption glassShards = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());
            level.sendParticles(glassShards,
                    itemEntity.getX(), itemEntity.getY() + 0.1, itemEntity.getZ(),
                    8, 0.1, 0.1, 0.1, 0.05);

            itemEntity.discard();
        }
    }
}
