import re

file_path = r'c:\developing\HEX-Rift-Division\src\main\java\com\trd\multiblock\system\MultiblockStructureHelper.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add isDestroying method
method_to_add = "    public static boolean isDestroying() { return IS_DESTROYING.get(); }\n\n"

# Insert after IS_DESTROYING declaration
content = content.replace('private static final ThreadLocal<Boolean> IS_DESTROYING = ThreadLocal.withInitial(() -> false);',
                          'private static final ThreadLocal<Boolean> IS_DESTROYING = ThreadLocal.withInitial(() -> false);\n' + method_to_add)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
