import os

def fix_override_and_sound(path):
    with open(path, 'r', encoding='utf-8') as f:
        c = f.read()
    c = c.replace('@Override\n    public InteractionResult doUse', 'public InteractionResult doUse')
    if 'PaintableWireBlock' in path:
        c = c.replace('SoundEvents.UI_BUTTON_CLICK,', 'SoundEvents.UI_BUTTON_CLICK.value(),')
    if 'ConverterBlock' in path:
        c = c.replace('SCREWDRIVER', 'SCREW_DRIVER')
    with open(path, 'w', encoding='utf-8') as f:
        f.write(c)

fix_override_and_sound('src/main/java/com/trd/block/basic/industrial/energy/ConverterBlock.java')
fix_override_and_sound('src/main/java/com/trd/block/basic/industrial/energy/SwitchBlock.java')
fix_override_and_sound('src/main/java/com/trd/block/basic/industrial/energy/PaintableWireBlock.java')
