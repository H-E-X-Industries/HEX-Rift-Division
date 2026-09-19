import sys
import re

def rep(p, a, b):
    with open(p, 'r', encoding='utf-8') as f:
        c = f.read()
    c = c.replace(a, b)
    with open(p, 'w', encoding='utf-8') as f:
        f.write(c)

rep('src/main/java/com/trd/block/entity/industrial/energy/ConnectorBlockEntity.java', 'return super.getRenderBoundingBox();', 'return new AABB(worldPosition);')

rep('src/main/java/com/trd/block/entity/industrial/energy/EnergyNodeBlockEntity.java', '@Override\n    public void onDataPacket', 'public void onDataPacket')
rep('src/main/java/com/trd/block/entity/industrial/energy/EnergyNodeBlockEntity.java', 'load(tag);', 'loadAdditional(tag, net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup());')

rep('src/main/java/com/trd/api/energy/ServerLifecycleHandler.java', 'com.trd.api.fluids.system.FluidNetworkManager.get(level).rebuildAllNetworks();', '')

rep('src/main/java/com/trd/block/basic/industrial/ElectricFurnaceBlock.java', 'NetworkHooks.openScreen((ServerPlayer) player,', 'player.openMenu(')
rep('src/main/java/com/trd/block/basic/industrial/ElectricFurnaceBlock.java', '@Override\n    public InteractionResult use', 'public InteractionResult use')
rep('src/main/java/com/trd/block/basic/industrial/ElectricFurnaceBlock.java', '@Override\n    public InteractionResult useWithoutItem', 'public InteractionResult useWithoutItem')

rep('src/main/java/com/trd/block/basic/industrial/energy/ConverterBlock.java', '@Override\n    public InteractionResult useWithoutItem', 'public InteractionResult useWithoutItem')
rep('src/main/java/com/trd/block/basic/industrial/energy/ConverterBlock.java', '@Override\n    public InteractionResult use', 'public InteractionResult use')

rep('src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java', '@Override\n    public InteractionResult useWithoutItem', 'public InteractionResult useWithoutItem')
rep('src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java', '@Override\n    public InteractionResult use', 'public InteractionResult use')
rep('src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java', 'CompoundTag itemNbt = pStack.getTag();', 'CompoundTag itemNbt = pStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();')
rep('src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java', 'batteryBE.load(itemNbt.getCompound("BlockEntityTag"));', 'batteryBE.loadAdditional(itemNbt.getCompound("BlockEntityTag"), pLevel.registryAccess());')

rep('src/main/java/com/trd/block/basic/industrial/energy/PaintableWireBlock.java', '@Override\n    public InteractionResult useWithoutItem', 'public InteractionResult useWithoutItem')
rep('src/main/java/com/trd/block/basic/industrial/energy/PaintableWireBlock.java', '@Override\n    public InteractionResult use', 'public InteractionResult use')

rep('src/main/java/com/trd/block/basic/industrial/energy/SwitchBlock.java', '@Override\n    public InteractionResult useWithoutItem', 'public InteractionResult useWithoutItem')
rep('src/main/java/com/trd/block/basic/industrial/energy/SwitchBlock.java', '@Override\n    public InteractionResult use', 'public InteractionResult use')

rep('src/main/java/com/trd/network/packet/energy/SyncMotorRpmPacket.java', 'import com.trd.block.entity.industrial.rotation.MotorElectroBlockEntity;', '')

rep('src/main/java/com/trd/client/gecko/block/energy/MachineBatteryRenderer.java', 'public void preRender(PoseStack poseStack, MachineBatteryBlockEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour)', 'public void preRender(PoseStack poseStack, MachineBatteryBlockEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha)')
rep('src/main/java/com/trd/client/gecko/block/energy/MachineBatteryRenderer.java', 'super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);', 'super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, net.minecraft.util.FastColor.ARGB32.color((int)(alpha*255), (int)(red*255), (int)(green*255), (int)(blue*255)));')
