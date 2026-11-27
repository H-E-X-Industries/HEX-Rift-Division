import nbtlib, inspect
print('File methods:', [m for m in dir(nbtlib.File) if not m.startswith('_')])
print('load', inspect.signature(nbtlib.load))
print('parse_nbt', inspect.signature(nbtlib.parse_nbt))
print('serialize_tag', inspect.signature(nbtlib.serialize_tag))
print('Serializer methods:', [m for m in dir(nbtlib.Serializer) if not m.startswith('_')])
print('File.from_fileobj', hasattr(nbtlib.File, 'from_fileobj'))
try:
    print(inspect.getsource(nbtlib.serialize_tag))
except Exception as e:
    print('no source', e)
