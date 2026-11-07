package com.trd.client.gltf.blockentity.drilling;

import com.hex.hexrender.client.BlockEntityModelDescriptor;
import com.hex.hexrender.core.ModelRegistry;
import com.hex.hexrender.loader.GltfModelAsset;
import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.drilling.MudCrossBlockEntity;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Описание модели буровой крестовины (Mud Cross) для блочного glTF-рендерера.
 */
public class MudCrossModelDescriptor implements BlockEntityModelDescriptor<MudCrossBlockEntity> {

    public static final ResourceLocation MODEL_ID =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/block/mud_cross.gltf");

    @Override
    public @Nullable GltfModelAsset model(MudCrossBlockEntity blockEntity) {
        return ModelRegistry.obtain(MODEL_ID);
    }
}
