import os

def fix_override_above_douse(path):
    with open(path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    with open(path, 'w', encoding='utf-8') as f:
        for i in range(len(lines)):
            if 'public InteractionResult doUse' in lines[i] and i > 0 and '@Override' in lines[i-1]:
                lines[i-1] = ''
            f.write(lines[i])

fix_override_above_douse('src/main/java/com/trd/block/basic/industrial/energy/ConverterBlock.java')
fix_override_above_douse('src/main/java/com/trd/block/basic/industrial/energy/SwitchBlock.java')
fix_override_above_douse('src/main/java/com/trd/block/basic/industrial/energy/PaintableWireBlock.java')

