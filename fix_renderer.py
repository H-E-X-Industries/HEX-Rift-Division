import sys

path = 'src/main/java/com/trd/client/gecko/block/energy/MachineBatteryRenderer.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('public void preRender(PoseStack poseStack, MachineBatteryBlockEntity animatable,\n                          BakedGeoModel model, MultiBufferSource bufferSource,\n                          VertexConsumer buffer, boolean isReRender, float partialTick,\n                          int packedLight, int packedOverlay, float red, float green,\n                          float blue, float alpha) {', 
'''@Override
    public void preRender(PoseStack poseStack, MachineBatteryBlockEntity animatable,
                          BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay, int colour) {''')

content = content.replace('super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, net.minecraft.util.FastColor.ARGB32.color((int)(alpha*255), (int)(red*255), (int)(green*255), (int)(blue*255)));', 'super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
