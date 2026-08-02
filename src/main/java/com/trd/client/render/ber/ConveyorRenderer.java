package com.trd.client.render.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.trd.block.basic.industrial.ConveyorBlock;
import com.trd.block.entity.industrial.ConveyorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ConveyorRenderer implements BlockEntityRenderer<ConveyorBlockEntity> {

    private final ItemRenderer itemRenderer;

    public ConveyorRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(ConveyorBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {

        com.trd.api.conveyor.client.ClientConveyorManager.ClientNetworkData netData = com.trd.api.conveyor.client.ClientConveyorManager.getNetworkFor(be.getBlockPos());
        if (netData == null || netData.items.isEmpty()) return;

        double blockIndex = netData.getIndexFor(be.getBlockPos());
        if (blockIndex < 0) return;

        Direction facing = be.getBlockState().getValue(ConveyorBlock.FACING);
        BlockPos currentPos = be.getBlockPos();
        
        BlockPos prevPos = null;
        if (blockIndex > 0) {
            prevPos = netData.path.get((int) blockIndex - 1);
        } else {
            // Пытаемся найти конвейер, который смотрит в нас, чтобы построить правильную дугу
            for (Direction d : Direction.values()) {
                if (d.getAxis().isVertical()) continue;
                BlockPos p = currentPos.relative(d);
                BlockState s = be.getLevel().getBlockState(p);
                if (s.getBlock() instanceof ConveyorBlock && s.getValue(ConveyorBlock.FACING) == d.getOpposite()) {
                    prevPos = p;
                    break;
                }
            }
            if (prevPos == null) {
                prevPos = currentPos.relative(facing.getOpposite());
            }
        }
        
        BlockPos nextPos = blockIndex < netData.path.size() - 1 ? netData.path.get((int) blockIndex + 1) : currentPos.relative(facing);

        for (com.trd.api.conveyor.ConveyorItem item : netData.items) {
            double globalProgress = item.getProgress() + (com.trd.api.conveyor.ConveyorNetwork.SPEED * partialTick);
            globalProgress = Math.min(globalProgress, netData.path.size() - 0.01);
            
            // Если предмет находится на текущем блоке (от index до index + 1)
            if (globalProgress >= blockIndex && globalProgress < blockIndex + 1) {
                double localProgress = globalProgress - blockIndex;
                ItemStack stack = item.getStack();
                if (stack.isEmpty()) continue;

                double[] pose = com.trd.api.conveyor.PathMath.calculatePathPoint(prevPos, currentPos, nextPos, localProgress);

                poseStack.pushPose();
                
                // pose = [x (абсолютный), y, z, rotY]
                // PathMath.center.y равен Y + 0.5. Опускаем предметы на 2 пикселя (0.125) по просьбе.
                // 0.05 - 0.125 = -0.075
                poseStack.translate(pose[0] - currentPos.getX(), (pose[1] - currentPos.getY()) - 0.075, pose[2] - currentPos.getZ());

                poseStack.mulPose(Axis.YP.rotationDegrees((float) -pose[3]));

                boolean isBlock = item.getStack().getItem() instanceof net.minecraft.world.item.BlockItem;
                if (!isBlock) {
                    poseStack.mulPose(Axis.XP.rotationDegrees(90));
                }

                float scale = 0.75f;
                poseStack.scale(scale, scale, scale);

                BakedModel model = itemRenderer.getModel(stack, be.getLevel(), null, 0);
                itemRenderer.render(stack, ItemDisplayContext.FIXED, false, poseStack, buffer,
                        packedLight, packedOverlay, model);

                poseStack.popPose();
            }
        }
    }
}