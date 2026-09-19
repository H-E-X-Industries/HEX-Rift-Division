import sys
import os

replacements = {
    'software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache': 'software.bernie.geckolib.animatable.instance.AnimatableInstanceCache',
    'software.bernie.geckolib.core.animation.AnimatableManager': 'software.bernie.geckolib.animation.AnimatableManager',
    'software.bernie.geckolib.core.animation.AnimationController': 'software.bernie.geckolib.animation.AnimationController',
    'software.bernie.geckolib.core.animation.AnimationState': 'software.bernie.geckolib.animation.AnimationState',
    'software.bernie.geckolib.core.animation.RawAnimation': 'software.bernie.geckolib.animation.RawAnimation',
    'software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar': 'software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar',
    'AnimatableManager.ControllerRegistrar': 'software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar',
    'Level level = null;': ''
}

def process_file(path):
    if not os.path.exists(path): return
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    changed = False
    for k, v in replacements.items():
        if k in content:
            content = content.replace(k, v)
            changed = True
    if changed:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)

process_file('src/main/java/com/trd/block/entity/industrial/energy/MachineBatteryBlockEntity.java')
process_file('src/main/java/com/trd/item/industrial/energy/EnergyCellItem.java')
process_file('src/main/java/com/trd/item/industrial/energy/MachineBatteryBlockItem.java')
process_file('src/main/java/com/trd/item/industrial/energy/WireCoilItem.java')

path_tabs = 'src/main/java/com/trd/main/ModCreativeTabs.java'
with open(path_tabs, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('pOutput', 'output')
with open(path_tabs, 'w', encoding='utf-8') as f:
    f.write(content)
