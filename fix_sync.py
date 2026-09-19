import sys

path = 'src/main/java/com/trd/client/ClientEnergySyncHandler.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('net.minecraftforge.api.distmarker', 'net.neoforged.api.distmarker')
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

path2 = 'src/main/java/com/trd/network/packet/energy/SyncMotorRpmPacket.java'
with open(path2, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('if (!(level.getBlockEntity(pos) instanceof MotorElectroBlockEntity motor)) return;', '')
content = content.replace('KineticNetwork net = KineticNetworkManager.get(level).getNetworkFor(pos);', '')
content = content.replace('if (net != null) {\n                net.syncRpm();\n            }', '')
with open(path2, 'w', encoding='utf-8') as f:
    f.write(content)
