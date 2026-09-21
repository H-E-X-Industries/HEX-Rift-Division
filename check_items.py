import glob
import json
import os

for f in glob.glob('src/main/resources/assets/trd/models/item/*.json'):
    try:
        with open(f) as file:
            d = json.load(file)
        if d.get('loader') == 'neoforge:obj' and 'model' in d:
            textures = list(d.get('textures', {}).keys())
            
            # get usemtl
            obj_path = d['model'].replace('trd:', 'src/main/resources/assets/trd/')
            with open(obj_path) as of:
                usemtls = list(set([l.strip().split(' ', 1)[1] for l in of.readlines() if l.startswith('usemtl ')]))
                
            print(f"{os.path.basename(f)}: texs={textures}, usemtls={usemtls}")
    except Exception as e:
        print(f"Error {f}: {e}")
