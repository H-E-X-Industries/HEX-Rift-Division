import os
import shutil

dst_dir = 'src/main/resources/assets/trd/models'
untracked = os.popen(f'git ls-files --others "{dst_dir}"').read().splitlines()
count = 0
for f in untracked:
    src_f = f.replace('src/main/', 'src 1.20.1/main/')
    if os.path.exists(src_f) and os.path.getsize(f) == 0:
        shutil.copy2(src_f, f)
        with open(f, 'r', encoding='utf-8') as file:
            content = file.read()
        
        changed = False
        if 'forge:composite' in content:
            content = content.replace('forge:composite', 'neoforge:composite')
            changed = True
        if 'forge:obj' in content:
            content = content.replace('forge:obj', 'neoforge:obj')
            changed = True
            
        if changed:
            with open(f, 'w', encoding='utf-8') as file:
                file.write(content)
                
        count += 1

print(f"Restored {count} files")
