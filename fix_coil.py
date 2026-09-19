import os

with open('src/main/java/com/trd/item/industrial/energy/WireCoilItem.java', 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('tag.put("FirstPos", NbtUtils.writeBlockPos(pos));\n            if (player != null) player.displayClientMessage(Component.translatable("message.trd.wire_coil.started"), \ntrue);\n            return InteractionResult.SUCCESS;',
'tag.put("FirstPos", NbtUtils.writeBlockPos(pos));\n            net.minecraft.world.item.component.CustomData.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag);\n            if (player != null) player.displayClientMessage(Component.translatable("message.trd.wire_coil.started"), \ntrue);\n            return InteractionResult.SUCCESS;')

c = c.replace('tag.put("FirstPos", NbtUtils.writeBlockPos(pos));\n            if (player != null) player.displayClientMessage(Component.translatable("message.trd.wire_coil.started"), true);\n            return InteractionResult.SUCCESS;',
'tag.put("FirstPos", NbtUtils.writeBlockPos(pos));\n            net.minecraft.world.item.component.CustomData.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag);\n            if (player != null) player.displayClientMessage(Component.translatable("message.trd.wire_coil.started"), true);\n            return InteractionResult.SUCCESS;')

c = c.replace('tag.remove("FirstPos");\n\n            // 1.',
'tag.remove("FirstPos");\n            net.minecraft.world.item.component.CustomData.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag);\n\n            // 1.')

with open('src/main/java/com/trd/item/industrial/energy/WireCoilItem.java', 'w', encoding='utf-8') as f:
    f.write(c)

