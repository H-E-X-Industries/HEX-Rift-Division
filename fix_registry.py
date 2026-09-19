import sys

path = 'src/main/java/com/trd/main/MainRegistry.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('com.trd.api.recipe.ModRecipes.register(modEventBus);', 'com.trd.api.recipe.ModRecipes.register(modEventBus);\n        com.trd.menu.ModMenuTypes.register(modEventBus);')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
