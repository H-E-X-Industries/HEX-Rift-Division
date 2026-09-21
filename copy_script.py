import os
import shutil

src_base = r'c:\developing\HEX-Rift-Division\src 1.20.1\main\java\com\trd'
dst_base = r'c:\developing\HEX-Rift-Division\src\main\java\com\trd'

paths_to_copy = [
    r'block\basic\industrial\fluids',
    r'block\entity\industrial\fluids',
    r'menu\industrial\FluidBarrelMenu.java',
    r'client\overlay\gui\GUIFluidBarrel.java'
]

def process_file(src_path, dst_path):
    os.makedirs(os.path.dirname(dst_path), exist_ok=True)
    with open(src_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # Replace net.minecraftforge with net.neoforged.neoforge
    content = content.replace('net.minecraftforge', 'net.neoforged.neoforge')

    with open(dst_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f'Copied and updated {dst_path}')

for p in paths_to_copy:
    src_full = os.path.join(src_base, p)
    dst_full = os.path.join(dst_base, p)
    
    if os.path.isdir(src_full):
        for root, _, files in os.walk(src_full):
            for file in files:
                if file.endswith('.java'):
                    s = os.path.join(root, file)
                    d = os.path.join(dst_base, os.path.relpath(s, src_base))
                    process_file(s, d)
    else:
        process_file(src_full, dst_full)
