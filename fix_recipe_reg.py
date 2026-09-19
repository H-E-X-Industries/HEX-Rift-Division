import os

with open('src/main/java/com/trd/main/MainRegistry.java', 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('com.trd.api.recipe.ModRecipes.register(modEventBus);', 'com.trd.api.recipe.ModRecipes.register(modEventBus);\n        com.trd.api.energy.ModRecipes.register(modEventBus);')

with open('src/main/java/com/trd/main/MainRegistry.java', 'w', encoding='utf-8') as f:
    f.write(c)
