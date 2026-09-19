package com.trd.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trd.block.entity.industrial.energy.ConnectorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class ConnectorRenderer implements BlockEntityRenderer<ConnectorBlockEntity> {
    public ConnectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ConnectorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be.getConnections().isEmpty()) return;

        Vec3 start = be.getWireAttachmentPoint();
        BlockPos startPos = be.getBlockPos();

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());

        for (BlockPos endPos : be.getConnections()) {
            // Рисуем только в одну сторону, чтобы избежать дублирования рендера линии
            if (endPos.compareTo(startPos) > 0) {
                Vec3 end = Vec3.atCenterOf(endPos);
                if (be.getLevel() != null && be.getLevel().getBlockEntity(endPos) instanceof ConnectorBlockEntity otherBe) {
                    end = otherBe.getWireAttachmentPoint();
                }

                drawWire(poseStack, vertexConsumer, start, end, startPos);
            }
        }
    }

    private void drawWire(PoseStack poseStack, VertexConsumer vertexConsumer, Vec3 start, Vec3 end, BlockPos startPos) {
        poseStack.pushPose();
        // Смещаем начало координат к блоку
        poseStack.translate(-startPos.getX(), -startPos.getY(), -startPos.getZ());
        Matrix4f pose = poseStack.last().pose();

        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        // Провисание провода зависит от расстояния
        double sag = dist * 0.15; 

        int segments = 16;
        for (int i = 0; i < segments; i++) {
            float t1 = (float) i / segments;
            float t2 = (float) (i + 1) / segments;

            Vec3 p1 = getPoint(start, dx, dy, dz, sag, t1);
            Vec3 p2 = getPoint(start, dx, dy, dz, sag, t2);

            vertexConsumer.addVertex(pose, (float) p1.x, (float) p1.y, (float) p1.z)
                    .setColor(40, 40, 40, 255)
                    .setNormal(poseStack.last(), 0, 1, 0);

            vertexConsumer.addVertex(pose, (float) p2.x, (float) p2.y, (float) p2.z)
                    .setColor(40, 40, 40, 255)
                    .setNormal(poseStack.last(), 0, 1, 0);
        }
        poseStack.popPose();
    }

    private Vec3 getPoint(Vec3 start, double dx, double dy, double dz, double sag, float t) {
        // Парабола: y = 4 * sag * t * (t - 1)
        double sagY = 4 * sag * t * (t - 1);
        return new Vec3(
                start.x + dx * t,
                start.y + dy * t + sagY,
                start.z + dz * t
        );
    }

    @Override
    public boolean shouldRenderOffScreen(ConnectorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
    
    @Override
    public AABB getRenderBoundingBox(ConnectorBlockEntity be) {
        return be.getRenderBoundingBox();
    }
}
