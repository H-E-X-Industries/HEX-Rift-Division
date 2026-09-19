import sys
import re
import os

path1 = 'src/main/java/com/trd/item/industrial/energy/WireCoilItem.java'
with open(path1, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('BlockPos pos = context.getClickedPos();', 'BlockPos pos = context.getClickedPos();\n        net.minecraft.world.level.Level level = context.getLevel();')
with open(path1, 'w', encoding='utf-8') as f:
    f.write(content)

path2 = 'src/main/java/com/trd/item/industrial/energy/EnergyCellItem.java'
with open(path2, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltip, TooltipFlag pFlag)', 'public void appendHoverText(ItemStack pStack, TooltipContext context, List<Component> pTooltip, TooltipFlag pFlag)')
content = content.replace('super.appendHoverText(pStack, pLevel, pTooltip, pFlag);', 'super.appendHoverText(pStack, context, pTooltip, pFlag);')
with open(path2, 'w', encoding='utf-8') as f:
    f.write(content)

path3 = 'src/main/java/com/trd/client/gecko/block/energy/MachineBatteryRenderer.java'
with open(path3, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);', 'super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, net.minecraft.util.FastColor.ARGB32.color((int)(alpha*255), (int)(red*255), (int)(green*255), (int)(blue*255)));')
content = content.replace('public void preRender(PoseStack poseStack, MachineBatteryBlockEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha)', 'public void preRender(PoseStack poseStack, MachineBatteryBlockEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour)')
content = content.replace('super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, net.minecraft.util.FastColor.ARGB32.color((int)(alpha*255), (int)(red*255), (int)(green*255), (int)(blue*255)));', 'super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);')
with open(path3, 'w', encoding='utf-8') as f:
    f.write(content)

path4 = 'src/main/java/com/trd/client/gecko/item/energy/MachineBatteryItemModel.java'
with open(path4, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('new ResourceLocation(', 'ResourceLocation.fromNamespaceAndPath(')
with open(path4, 'w', encoding='utf-8') as f:
    f.write(content)

path5 = 'src/main/java/com/trd/network/packet/energy/SyncMotorRpmPacket.java'
with open(path5, 'r', encoding='utf-8') as f:
    content = f.read()
content = re.sub(r'motor\.setTargetRpm\(rpm\);', '', content)
content = re.sub(r'motor\.setChanged\(\);', '', content)
content = re.sub(r'net\.requestRecalculation\(\);', '', content)
with open(path5, 'w', encoding='utf-8') as f:
    f.write(content)

