import sys

path = 'src/main/java/com/trd/main/ModCreativeTabs.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('output.accept(ModBlocks.ELECTRO_FURNACE.get());', 'output.accept(ModBlocks.ELECTRO_FURNACE.get());\n                        output.accept(ModItems.WIRE_COIL.get());\n                        output.accept(ModItems.INDUSTRIAL_COPPER_WIRE.get());\n                        output.accept(ModItems.ENERGY_CELL.get());\n                        output.accept(ModItems.BATTERY.get());')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
