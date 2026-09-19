import sys
import re

path = 'src/main/java/com/trd/item/industrial/energy/WireCoilWindingRecipe.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('CraftingContainer', 'net.minecraft.world.item.crafting.CraftingInput')
content = content.replace('RegistryAccess registryAccess', 'net.minecraft.core.HolderLookup.Provider provider')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
