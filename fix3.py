import os
import re

def fix_all():
    # ElectricFurnaceBlock
    with open('src/main/java/com/trd/block/basic/industrial/ElectricFurnaceBlock.java', 'r', encoding='utf-8') as f:
        c = f.read()
    c = c.replace('@Override\n    @Override\n', '@Override\n')
    c = c.replace('NetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {', 'player.openMenu(new MenuProvider() {')
    c = c.replace('}, buf -> buf.writeBlockPos(pos));', '}, pos);')
    with open('src/main/java/com/trd/block/basic/industrial/ElectricFurnaceBlock.java', 'w', encoding='utf-8') as f:
        f.write(c)
        
    # MachineBatteryBlock
    with open('src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java', 'r', encoding='utf-8') as f:
        c = f.read()
    c = c.replace('@Override\n    @Override\n', '@Override\n')
    c = c.replace('net.minecraft.core.component.DataComponents comps = net.minecraft.core.component.DataComponents.CUSTOM_DATA; net.minecraft.world.item.component.CustomData itemNbt = pStack.getOrDefault(comps, net.minecraft.world.item.component.CustomData.EMPTY);',
                  'net.minecraft.world.item.component.CustomData itemNbt = pStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);')
    c = c.replace('level.registryAccess());', 'pLevel.registryAccess());')
    with open('src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java', 'w', encoding='utf-8') as f:
        f.write(c)

    # SwitchBlock
    with open('src/main/java/com/trd/block/basic/industrial/energy/SwitchBlock.java', 'r', encoding='utf-8') as f:
        c = f.read()
    c = c.replace('import com.trd.sound.ModSounds;\n', '')
    c = c.replace('ModSounds.LEVER1.get()', 'net.minecraft.sounds.SoundEvents.LEVER_CLICK')
    c = c.replace('@Override\n    public InteractionResult doUse', 'public InteractionResult doUse')
    with open('src/main/java/com/trd/block/basic/industrial/energy/SwitchBlock.java', 'w', encoding='utf-8') as f:
        f.write(c)

    # ConverterBlock
    with open('src/main/java/com/trd/block/basic/industrial/energy/ConverterBlock.java', 'r', encoding='utf-8') as f:
        c = f.read()
    c = c.replace('@Override\n    public InteractionResult doUse', 'public InteractionResult doUse')
    c = c.replace('super.use(state, level, pos, player, hand, hit)', 'super.useWithoutItem(state, level, pos, player, hit)')
    c = c.replace('ModItems.SCREWDRIVER.get()', 'com.trd.item.ModItems.SCREWDRIVER.get()')
    with open('src/main/java/com/trd/block/basic/industrial/energy/ConverterBlock.java', 'w', encoding='utf-8') as f:
        f.write(c)

    # PaintableWireBlock
    with open('src/main/java/com/trd/block/basic/industrial/energy/PaintableWireBlock.java', 'r', encoding='utf-8') as f:
        c = f.read()
    c = c.replace('@Override\n    public InteractionResult doUse', 'public InteractionResult doUse')
    c = c.replace('SoundEvents.UI_BUTTON_CLICK.get()', 'SoundEvents.UI_BUTTON_CLICK')
    c = c.replace('super.use(state, level, pos, player, hand, hit)', 'super.useWithoutItem(state, level, pos, player, hit)')
    with open('src/main/java/com/trd/block/basic/industrial/energy/PaintableWireBlock.java', 'w', encoding='utf-8') as f:
        f.write(c)

fix_all()
