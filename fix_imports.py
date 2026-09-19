import os

def fix_imports(path):
    with open(path, 'r', encoding='utf-8') as f:
        c = f.read()

    if 'import net.minecraft.world.level.block.BaseEntityBlock;' not in c:
        c = c.replace('import net.minecraft.world.level.block.Block;', 'import net.minecraft.world.level.block.Block;\nimport net.minecraft.world.level.block.BaseEntityBlock;')
    
    with open(path, 'w', encoding='utf-8') as f:
        f.write(c)

fix_imports('src/main/java/com/trd/block/basic/industrial/energy/PaintableWireBlock.java')
