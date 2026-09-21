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
                
                # Check MTL file
                mtl_path = obj_path.replace('.obj', '.mtl')
                if not os.path.exists(mtl_path):
                    # Check mtllib in obj
                    with open(obj_path, 'r', encoding='utf-8') as of:
                        for l in of:
                            if l.startswith('mtllib '):
                                mtllib = l.strip().split(' ', 1)[1]
                                # resolve
                                if mtllib.startswith('../'):
                                    mtllib = mtllib[3:]
                                    mtl_path = os.path.join(os.path.dirname(os.path.dirname(obj_path)), mtllib)
                                else:
                                    mtl_path = os.path.join(os.path.dirname(obj_path), mtllib)
                                break
                                
                if os.path.exists(mtl_path):
                    with open(mtl_path, 'r', encoding='utf-8') as mf:
                        for l in mf:
                            if l.strip().startswith('map_Kd #'):
                                var_name = l.strip().split('#')[1]
                                
                                # Now check if json maps usemtl instead of var_name
                                with open(obj_path, 'r', encoding='utf-8') as of:
                                    usemtls = list(set([ol.strip().split(' ', 1)[1] for ol in of.readlines() if ol.startswith('usemtl ')]))
                                
                                if len(usemtls) == 1:
                                    usemtl = usemtls[0]
                                    texs = obj_dict.get('textures', {})
                                    if usemtl in texs and var_name not in texs:
                                        texs[var_name] = texs[usemtl]
                                        del texs[usemtl]
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

print(f"Restored mapping in {count} JSON files")
