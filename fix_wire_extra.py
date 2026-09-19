import sys

path = 'src/main/java/com/trd/block/basic/industrial/energy/WireBlock.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('!= null;\n                != null;', '!= null;')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
