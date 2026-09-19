import os

with open('src/main/java/com/trd/api/recipe/MillstoneRecipe.java', 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list())', 'ItemStack.LIST_STREAM_CODEC')

with open('src/main/java/com/trd/api/recipe/MillstoneRecipe.java', 'w', encoding='utf-8') as f:
    f.write(c)

