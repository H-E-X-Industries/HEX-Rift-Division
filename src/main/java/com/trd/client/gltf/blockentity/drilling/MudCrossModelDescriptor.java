package com.trd.client.gltf.blockentity.drilling;

import com.hex.hexrender.client.BlockEntityModelDescriptor;
import com.hex.hexrender.core.ModelRegistry;
import com.hex.hexrender.loader.GltfModelAsset;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.main.MainRegistry;
import com.trd.multiblock.industrial.drilling.MudCrossBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
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

    @Override
    public void transform(MudCrossBlockEntity blockEntity, float partialTick, PoseStack poseStack) {
        // GltfBlockEntityRenderer поворачивает на -toYRot(), что отличается от MultiblockStructureHelper на 180°.
        // Дополнительно сдвигаем из точки контроллера @ (0,0,+1) в центр отверстия (0,0,0).
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
        poseStack.translate(0.0f, 0.0f, -1.0f);
    }

    @Override
    public double boundingRadius(MudCrossBlockEntity blockEntity) {
        return 12.0;
    }

    @Override
    public AABB renderBoundingBox(MudCrossBlockEntity blockEntity) {
        BlockPos well = blockEntity.getWellheadCenterPos();
        return new AABB(
                well.getX() - 5.0, well.getY() - 1.0, well.getZ() - 5.0,
                well.getX() + 6.0, well.getY() + 4.0, well.getZ() + 6.0
        );
    }
}
