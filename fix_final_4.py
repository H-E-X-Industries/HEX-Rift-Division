import sys
import re

path1 = 'src/main/java/com/trd/item/industrial/energy/WireCoilItem.java'
with open(path1, 'r', encoding='utf-8') as f:
    content = f.read()
if 'import net.minecraft.world.level.Level;' not in content:
    content = content.replace('import net.minecraft.world.item.Item;', 'import net.minecraft.world.item.Item;\nimport net.minecraft.world.level.Level;')
with open(path1, 'w', encoding='utf-8') as f:
    f.write(content)

path2 = 'src/main/java/com/trd/block/basic/industrial/energy/WireBlock.java'
with open(path2, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('currentPos', 'pos').replace('level.getCapability', 'level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, pos, be.getBlockState(), be, sideFromNeighbor) != null ? true : false')
with open(path2, 'w', encoding='utf-8') as f:
    f.write(content)

path3 = 'src/main/java/com/trd/block/basic/industrial/energy/PaintableWireBlock.java'
with open(path3, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('SoundEvents.UI_BUTTON_CLICK', 'SoundEvents.UI_BUTTON_CLICK.value()')
content = content.replace('@Override\n    public InteractionResult useWithoutItem', 'public InteractionResult useWithoutItem')
with open(path3, 'w', encoding='utf-8') as f:
    f.write(content)

path4 = 'src/main/java/com/trd/block/basic/industrial/energy/SwitchBlock.java'
with open(path4, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('level.playSound(player, pos', 'level.playSound(null, pos')
content = content.replace('@Override\n    public InteractionResult useWithoutItem', 'public InteractionResult useWithoutItem')
with open(path4, 'w', encoding='utf-8') as f:
    f.write(content)

path5 = 'src/main/java/com/trd/client/gecko/block/energy/MachineBatteryRenderer.java'
with open(path5, 'r', encoding='utf-8') as f:
    content = f.read()
content = re.sub(r'float red, float green, float blue, float alpha', 'int colour', content)
content = re.sub(r'super\.preRender\([^;]+red, green, blue, alpha\);', 'super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);', content)
with open(path5, 'w', encoding='utf-8') as f:
    f.write(content)

path6 = 'src/main/java/com/trd/block/entity/industrial/energy/PaintableWireBlockEntity.java'
with open(path6, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('@Override\n    public void onDataPacket', 'public void onDataPacket')
with open(path6, 'w', encoding='utf-8') as f:
    f.write(content)

