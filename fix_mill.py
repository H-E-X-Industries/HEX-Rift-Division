import os

with open('src/main/java/com/trd/client/render/flywheel/MillstoneVisual.java', 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('float speed = 360.0f / ((MillstoneBlockEntity.GRIND_COOLDOWN + 1.5f) / 20.0f);', 'float speed = 360.0f / (MillstoneBlockEntity.GRIND_COOLDOWN / 20.0f);')

with open('src/main/java/com/trd/client/render/flywheel/MillstoneVisual.java', 'w', encoding='utf-8') as f:
    f.write(c)

