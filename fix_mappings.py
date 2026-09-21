import glob
import json
import os

count = 0
for f in glob.glob('src/main/resources/assets/trd/models/**/*.json', recursive=True):
    try:
        with open(f, 'r', encoding='utf-8') as file:
            d = json.load(file)
            
        changed = [False]
        
        def process(obj_dict):
            if obj_dict.get('loader') == 'neoforge:obj' and 'model' in obj_dict:
                obj_path = obj_dict['model'].replace('trd:', 'src/main/resources/assets/trd/')
                if not os.path.exists(obj_path):
                    return
                
                with open(obj_path, 'r', encoding='utf-8') as of:
                    usemtls = list(set([l.strip().split(' ', 1)[1] for l in of.readlines() if l.startswith('usemtl ')]))
                
                if len(usemtls) == 1:
                    usemtl = usemtls[0]
                    texs = obj_dict.get('textures', {})
                    non_particle = [k for k in texs.keys() if k != 'particle' and k != usemtl]
                    
                    if len(non_particle) == 1:
                        old_key = non_particle[0]
                        # Rename key
                        texs[usemtl] = texs[old_key]
                        del texs[old_key]
                        changed[0] = True

        process(d)
        for child in d.get('children', {}).values():
            process(child)
            
        if changed[0]:
            with open(f, 'w', encoding='utf-8') as file:
                json.dump(d, file, indent=2)
            count += 1
            print(f"Fixed {f}")
            
    except Exception as e:
        pass

print(f"Fixed mapping in {count} JSON files")
