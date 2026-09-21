import os
import glob

count = 0
for f in glob.glob('src/main/resources/assets/trd/models/**/*.json', recursive=True):
    with open(f, 'r', encoding='utf-8') as file:
        content = file.read()
    
    changed = False
    if 'neoneoforge:composite' in content:
        content = content.replace('neoneoforge:composite', 'neoforge:composite')
        changed = True
    if 'neoneoforge:obj' in content:
        content = content.replace('neoneoforge:obj', 'neoforge:obj')
        changed = True
        
    if changed:
        with open(f, 'w', encoding='utf-8') as file:
            file.write(content)
        count += 1

print(f'Fixed duplicate loaders in {count} files')
