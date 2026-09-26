package com.trd.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.trd.block.basic.industrial.rotation.StatorBlock;
import com.trd.block.entity.industrial.rotation.StatorBlockEntity;
import com.trd.client.render.flywheel.ModModels;
import com.trd.item.industrial.energy.StatorCoilItem;
import com.trd.main.MainRegistry;
import com.trd.multiblock.system.MultiblockPartBlock;
import com.trd.multiblock.system.MultiblockStructureHelper;
import com.trd.multiblock.system.roles.IMultiblockPart;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class ClientHighlightEvent {

    @SubscribeEvent
    public static void onRenderHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        ItemStack heldStack = player.getMainHandItem();
        if (!(heldStack.getItem() instanceof StatorCoilItem)) {
            heldStack = player.getOffhandItem();
        }
        if (!(heldStack.getItem() instanceof StatorCoilItem coilItem)) {
            return;
        }

        BlockHitResult hit = event.getTarget();
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        BlockPos ctrlPos = pos;

        if (state.getBlock() instanceof MultiblockPartBlock) {
            var be = mc.level.getBlockEntity(pos);
            if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
                ctrlPos = part.getControllerPos();
                state = mc.level.getBlockState(ctrlPos);
            }
        }

        if (!(state.getBlock() instanceof StatorBlock statorBlock)) return;

        var be = mc.level.getBlockEntity(ctrlPos);
        if (!(be instanceof StatorBlockEntity statorBe)) return;

        Direction facing = state.getValue(StatorBlock.FACING);
        Direction.Axis axis = state.getValue(StatorBlock.AXIS);
        Vec3 holeCenter = StatorBlock.getHoleCenter(ctrlPos, facing, axis);

        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int slot = statorBlock.calculateSlot(state, ctrlPos, player, hit, partialTick);

        if (!statorBe.getCoilsInventory().getStackInSlot(slot).isEmpty()) {
            return;
        }

        PartialModel partialModel = ModModels.STATOR_COILS.get(coilItem.getMaterialName());
        if (partialModel == null) {
            partialModel = ModModels.STATOR_COILS.get("copper");
        }
        if (partialModel == null) return;
        BakedModel bakedModel = partialModel.get();
        if (bakedModel == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(holeCenter.x - camPos.x, holeCenter.y - camPos.y, holeCenter.z - camPos.z);

        if (axis == Direction.Axis.X) {
            poseStack.mulPose(Axis.YP.rotationDegrees(90));
        } else if (axis == Direction.Axis.Y) {
            if (facing == Direction.NORTH) {
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
            } else if (facing == Direction.EAST) {
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
            } else if (facing == Direction.WEST) {
                poseStack.mulPose(Axis.YP.rotationDegrees(270));
            }
            poseStack.mulPose(Axis.XP.rotationDegrees(-90));
        }

        poseStack.mulPose(Axis.ZP.rotationDegrees(slot * 30));
        poseStack.translate(0, 1.1, 0);

        poseStack.scale(0.99f, 0.99f, 0.99f);

        long time = mc.level.getGameTime() * 50 + (long) (partialTick * 50);
        float alpha = 0.1f + 0.4f * (float) ((Math.sin(time / 200.0) + 1.0) / 2.0);

        poseStack.translate(-0.5, -0.5, -0.5);

        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        VertexConsumer pulsingConsumer = new PulsingVertexConsumer(buffer, alpha);

        mc.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                pulsingConsumer,
                null,
                bakedModel,
                1.0F, 1.0F, 1.0F,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();
    }

    private static class PulsingVertexConsumer implements VertexConsumer {
        private final VertexConsumer original;
        private final float alpha;

        public PulsingVertexConsumer(VertexConsumer original, float alpha) {
            this.original = original;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            original.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            original.setColor(r, g, b, (int) (a * alpha));
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            int a = (argb >> 24) & 0xFF;
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            original.setColor(r, g, b, (int) (a * alpha));
            return this;
        }

        @Override
        public VertexConsumer setColor(float r, float g, float b, float a) {
            original.setColor(r, g, b, a * alpha);
            return this;
        }

        @Override
        public VertexConsumer setWhiteAlpha(int a) {
            original.setWhiteAlpha((int) (a * alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            original.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            original.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            original.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            original.setNormal(x, y, z);
            return this;
        }

        @Override
        public void putBulkData(PoseStack.Pose pose, net.minecraft.client.renderer.block.model.BakedQuad quad, float red, float green, float blue, float a, int packedLight, int packedOverlay) {
            original.putBulkData(pose, quad, red, green, blue, a * alpha, packedLight, packedOverlay);
        }

        @Override
        public void putBulkData(PoseStack.Pose pose, net.minecraft.client.renderer.block.model.BakedQuad quad, float[] brightness, float red, float green, float blue, float a, int[] lightmap, int packedOverlay, boolean readExistingColor) {
            original.putBulkData(pose, quad, brightness, red, green, blue, a * alpha, lightmap, packedOverlay, readExistingColor);
        }

        @Override
        public void putBulkData(PoseStack.Pose pose, net.minecraft.client.renderer.block.model.BakedQuad quad, float red, float green, float blue, float a, int packedLight, int packedOverlay, boolean readExistingColor) {
            original.putBulkData(pose, quad, red, green, blue, a * alpha, packedLight, packedOverlay, readExistingColor);
        }

        @Override
        public VertexConsumer misc(com.mojang.blaze3d.vertex.VertexFormatElement element, int... values) {
            original.misc(element, values);
            return this;
        }

        @Override
        public int applyBakedLighting(int packedLight, java.nio.ByteBuffer data) {
            return original.applyBakedLighting(packedLight, data);
        }

        @Override
        public void applyBakedNormals(org.joml.Vector3f normal, java.nio.ByteBuffer data, org.joml.Matrix3f pose) {
            original.applyBakedNormals(normal, data, pose);
        }
    }
}
