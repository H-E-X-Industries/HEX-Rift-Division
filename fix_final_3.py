import sys
import re

path1 = 'src/main/java/com/trd/client/gecko/block/energy/MachineBatteryRenderer.java'
with open(path1, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, net.minecraft.util.FastColor.ARGB32.color((int)(alpha*255), (int)(red*255), (int)(green*255), (int)(blue*255)));', '')
content = content.replace('float red, float green, float blue, float alpha', 'int colour')
content = content.replace('super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);', 'super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);')
content = content.replace('@Override\n    public void preRender', 'public void preRender')
with open(path1, 'w', encoding='utf-8') as f:
    f.write(content)

path2 = 'src/main/java/com/trd/menu/industrial/ElectricFurnaceMenu.java'
with open(path2, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('net.neoforged.neoforge.capabilities.ForgeCapabilities', 'net.neoforged.neoforge.capabilities.Capabilities')
with open(path2, 'w', encoding='utf-8') as f:
    f.write(content)

path3 = 'src/main/java/com/trd/capability/ModCapabilities.java'
with open(path3, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('createVoid(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID, "energy_provider"))', 'createVoid(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID, "energy_provider"), com.trd.api.energy.IEnergyProvider.class)')
content = content.replace('createVoid(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID, "energy_receiver"))', 'createVoid(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID, "energy_receiver"), com.trd.api.energy.IEnergyReceiver.class)')
with open(path3, 'w', encoding='utf-8') as f:
    f.write(content)

path4 = 'src/main/java/com/trd/block/entity/industrial/energy/PaintableWireBlockEntity.java'
with open(path4, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('if (tag != null) this.loadAdditional(tag, provider);', 'if (tag != null) this.loadAdditional(tag, null);')
content = content.replace('@Override\n    public void onDataPacket', 'public void onDataPacket')
with open(path4, 'w', encoding='utf-8') as f:
    f.write(content)

path5 = 'src/main/java/com/trd/block/SwitchBlock.java'
import os
if os.path.exists(path5):
    with open(path5, 'r', encoding='utf-8') as f:
        content = f.read()
    content = content.replace('level.playSound(player, pos', 'level.playSound(null, pos')
    with open(path5, 'w', encoding='utf-8') as f:
        f.write(content)

path6 = 'src/main/java/com/trd/network/packet/energy/SyncMotorRpmPacket.java'
with open(path6, 'r', encoding='utf-8') as f:
    content = f.read()
content = re.sub(r'if \(net != null\) \{', '', content)
content = re.sub(r'^\s*\}\s*$', '', content, flags=re.MULTILINE)
with open(path6, 'w', encoding='utf-8') as f:
    f.write(content)

