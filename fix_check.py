import re

file_path = r'c:\developing\HEX-Rift-Division\src\main\java\com\trd\multiblock\system\MultiblockStructureHelper.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the else if block containing EnergyNetworkManager check in checkPlacement methods
pattern = r'\} else if \(level instanceof ServerLevel serverLevel && EnergyNetworkManager\.get\(serverLevel\)\.isBlockObstructingAnyWire\(worldPos\)\) \{\s*(?://[^\n]*\n\s*)*obstructions\.add\(worldPos\);\s*\}'

new_content = re.sub(pattern, '}', content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(new_content)
