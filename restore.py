import os
import shutil

src_dir = r'c:\developing\HEX-Rift-Division\src 1.20.1\main\resources\assets\trd\models'
dst_dir = r'c:\developing\HEX-Rift-Division\src\main\resources\assets\trd\models'

untracked_str = os.popen('git ls-files --others "c:/developing/HEX-Rift-Division/src/main/resources/assets/trd/models"').read()
for f in untracked_str.splitlines():
    rel_path = os.path.relpath(f, dst_dir)
    src_f = os.path.join(src_dir, rel_path)
    if os.path.exists(src_f):
        shutil.copy2(src_f, f)
        
        # fix the forge:composite right away
        with open(f, 'r', encoding='utf-8') as file:
            content = file.read()
        if "forge:composite" in content:
            content = content.replace("forge:composite", "neoforge:composite")
            with open(f, 'w', encoding='utf-8') as file:
                file.write(content)
        
        print("Restored:", rel_path)
