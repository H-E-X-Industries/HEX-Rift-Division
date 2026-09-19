import sys

path1 = 'src/main/java/com/trd/item/industrial/energy/WireCoilWindingRecipe.java'
with open(path1, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('@Override\n    @Override', '@Override')
with open(path1, 'w', encoding='utf-8') as f:
    f.write(content)

path2 = 'src/main/java/com/trd/block/entity/industrial/energy/EnergyNodeBlockEntity.java'
with open(path2, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('loadAdditional(tag, net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup());', 'loadAdditional(tag, this.level == null ? null : this.level.registryAccess());')
with open(path2, 'w', encoding='utf-8') as f:
    f.write(content)
