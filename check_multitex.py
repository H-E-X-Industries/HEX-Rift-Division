import os
import glob
import json

for f in glob.glob('src/main/resources/assets/trd/models/**/*.json', recursive=True):
    try:
        data = json.load(open(f))
        
        def check(obj_data, name):
            if obj_data.get('loader') == 'neoforge:obj':
                texs = [k for k in obj_data.get('textures', {}).keys() if k != 'particle']
                if len(texs) > 1:
                    print(f'MULTI-TEX in {name}: {texs}')
                    
        check(data, f)
        for cname, cdata in data.get('children', {}).items():
            check(cdata, f + " -> " + cname)
    except:
        pass
