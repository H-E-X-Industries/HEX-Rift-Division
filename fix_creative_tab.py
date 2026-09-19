import sys

path = 'src/main/java/com/trd/main/ModCreativeTabs.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('output.accept(ModItems.BATTERY.get());', 'output.accept(ModItems.BATTERY.get());\n                        output.accept(ModItems.CREATIVE_BATTERY.get());')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
