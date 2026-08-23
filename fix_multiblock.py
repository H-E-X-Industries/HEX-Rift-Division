import re

file_path = r'c:\developing\HEX-Rift-Division\src\main\java\com\trd\multiblock\system\MultiblockStructureHelper.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Remove 'if (relativePos.equals(BlockPos.ZERO)) continue;' from all checkPlacement methods
# Wait, let's just remove that line entirely wherever it appears inside a checkPlacement method.
# Actually, I'll just remove all occurrences of if (relativePos.equals(BlockPos.ZERO)) continue;
content = content.replace("if (relativePos.equals(BlockPos.ZERO)) continue;", "")

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
