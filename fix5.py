import os
import re

def final_fix(path):
    with open(path, 'r', encoding='utf-8') as f:
        c = f.read()

    # Replace @Override above doUse
    c = re.sub(r'@Override\s+public InteractionResult doUse', 'public InteractionResult doUse', c)

    if 'ConverterBlock' in path:
        c = c.replace('stack.getItem() == com.trd.item.ModItems.SCREW_DRIVER.get()', 'stack.getItem() instanceof com.trd.item.tools.ScrewdriverItem')
        c = c.replace('stack.getItem() == com.trd.item.ModItems.SCREWDRIVER.get()', 'stack.getItem() instanceof com.trd.item.tools.ScrewdriverItem')
        c = c.replace('stack.getItem() == ModItems.SCREWDRIVER.get()', 'stack.getItem() instanceof com.trd.item.tools.ScrewdriverItem')
        c = c.replace('stack.getItem() == ModItems.SCREW_DRIVER.get()', 'stack.getItem() instanceof com.trd.item.tools.ScrewdriverItem')

    with open(path, 'w', encoding='utf-8') as f:
        f.write(c)

final_fix('src/main/java/com/trd/block/basic/industrial/energy/ConverterBlock.java')
final_fix('src/main/java/com/trd/block/basic/industrial/energy/SwitchBlock.java')
final_fix('src/main/java/com/trd/block/basic/industrial/energy/PaintableWireBlock.java')
