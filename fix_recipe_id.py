import sys

path = 'src/main/java/com/trd/item/industrial/energy/WireCoilWindingRecipe.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('RegistryAccess', 'net.minecraft.core.HolderLookup.Provider')
content = content.replace('public ResourceLocation getId() {', '/* public ResourceLocation getId() {')
content = content.replace('return id;\n    }', 'return id;\n    } */')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
