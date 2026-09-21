import os
import glob

count = 0
for f in glob.glob('src/main/resources/assets/trd/models/**/*.json', recursive=True):
    with open(f, 'r', encoding='utf-8') as file:
        content = file.read()
    if 'forge:composite' in content or 'forge:obj' in content:
        content = content.replace('forge:composite', 'neoforge:composite').replace('forge:obj', 'neoforge:obj')
        with open(f, 'w', encoding='utf-8') as file:
            file.write(content)
        count += 1
print(f'Fixed loaders in {count} files')
