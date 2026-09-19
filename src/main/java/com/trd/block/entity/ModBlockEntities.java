package com.trd.block.entity;

import com.trd.block.basic.ModBlocks;
import com.trd.block.entity.industrial.rotation.MillstoneBlockEntity;
import com.trd.main.MainRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MainRegistry.MOD_ID);

    public static final Supplier<BlockEntityType<MillstoneBlockEntity>> MILLSTONE = BLOCK_ENTITIES.register("millstone",
            () -> BlockEntityType.Builder.of(MillstoneBlockEntity::new, ModBlocks.MILLSTONE.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
