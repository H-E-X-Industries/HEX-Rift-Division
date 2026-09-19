import os

path = 'src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('});', '}')

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

