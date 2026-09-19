import sys

path = 'src/main/java/com/trd/block/basic/ModBlocks.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('    public static java.util.List<DeferredBlock<Block>> BATTERY_BLOCKS = new java.util.ArrayList<>();\n\n', '')
content = content.replace('    public static final DeferredBlock<Block> MACHINE_BATTERY', '    public static java.util.List<DeferredBlock<Block>> BATTERY_BLOCKS = new java.util.ArrayList<>();\n\n    public static final DeferredBlock<Block> MACHINE_BATTERY')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
