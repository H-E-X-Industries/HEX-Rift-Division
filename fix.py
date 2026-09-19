import sys
import re

path = 'src/main/java/com/trd/block/basic/industrial/energy/WireBlock.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# I will just restore the file from git and then apply the proper replacements
