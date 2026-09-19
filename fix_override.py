import sys

path = 'src/main/java/com/trd/block/entity/industrial/energy/ConnectorBlockEntity.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('@Override\n    public AABB getRenderBoundingBox', 'public AABB getRenderBoundingBox')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

path2 = 'src/main/java/com/trd/block/entity/industrial/energy/WireBlockEntity.java'
import os
if os.path.exists(path2):
    with open(path2, 'r', encoding='utf-8') as f:
        content = f.read()
    content = content.replace('@Override\n    public AABB getRenderBoundingBox', 'public AABB getRenderBoundingBox')
    with open(path2, 'w', encoding='utf-8') as f:
        f.write(content)
