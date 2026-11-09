package com.trd.client.gltf.item.drilling;

import com.hex.hexrender.client.ItemModelDescriptor;
import com.hex.hexrender.core.ModelRegistry;
import com.hex.hexrender.loader.GltfModelAsset;
import com.trd.client.gltf.blockentity.drilling.MudCrossModelDescriptor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Облик предмета буровой крестовины (Mud Cross) в руке и инвентаре на glTF.
 */
public class MudCrossItemAppearance implements ItemModelDescriptor {

    @Override
    public @Nullable GltfModelAsset model(ItemStack stack, ItemDisplayContext context) {
        return ModelRegistry.obtain(MudCrossModelDescriptor.MODEL_ID);
    }
}
