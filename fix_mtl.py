import os
import glob

obj_files = glob.glob('src/main/resources/assets/trd/models/**/*.obj', recursive=True)

fixed_mtls = 0
for obj in obj_files:
    with open(obj, 'r', encoding='utf-8') as f:
        obj_lines = f.readlines()
        
    usemtls = []
    mtllib = None
    for line in obj_lines:
        line = line.strip()
        if line.startswith('usemtl '):
            usemtls.append(line.split(' ', 1)[1])
        elif line.startswith('mtllib '):
            mtllib = line.split(' ', 1)[1]
            
    if not usemtls:
        continue
        
    mtl_path = None
    if mtllib:
        mtl_path = os.path.join(os.path.dirname(obj), mtllib)
    else:
        mtl_path = obj.replace('.obj', '.mtl')
        
    if not os.path.exists(mtl_path):
        # Create empty mtl if it doesn't exist but is requested
        open(mtl_path, 'w', encoding='utf-8').write('')
        
    with open(mtl_path, 'r', encoding='utf-8') as f:
        mtl_content = f.read()
        
    missing = []
    for mat in set(usemtls):
        # Search for exact newmtl declaration
        if f'newmtl {mat}' not in mtl_content:
            missing.append(mat)
            
    if missing:
        with open(mtl_path, 'a', encoding='utf-8') as f:
            for mat in missing:
                f.write(f'\nnewmtl {mat}\n')
        fixed_mtls += 1
        print(f"Added {missing} to {mtl_path}")

print(f"Fixed {fixed_mtls} MTL files")
