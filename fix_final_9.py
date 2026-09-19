import sys

path = 'src/main/java/com/trd/item/industrial/energy/WireCoilWindingRecipe.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('@Override\n/* public ResourceLocation getId() {\n        return id;\n    } */\n\n    @Override', '/* public ResourceLocation getId() {\n        return id;\n    } */\n\n    @Override')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
