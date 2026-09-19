import sys
import re

path = 'src/main/java/com/trd/item/industrial/energy/WireCoilWindingRecipe.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = re.sub(r'@Nullable\s+', '', content, flags=re.DOTALL)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
