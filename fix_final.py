import sys

path = 'src/main/java/com/trd/item/industrial/energy/WireCoilWindingRecipe.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('net.minecraft.core.net.minecraft.core.HolderLookup', 'net.minecraft.core.HolderLookup')
content = content.replace('net.minecraft.world.inventory.net.minecraft.world.item.crafting', 'net.minecraft.world.item.crafting')
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

path2 = 'src/main/java/com/trd/client/handler/ClientEnergySyncHandler.java'
import os
if os.path.exists(path2):
    with open(path2, 'r', encoding='utf-8') as f:
        content = f.read()
    content = content.replace('net.minecraftforge.api.distmarker', 'net.neoforged.api.distmarker')
    with open(path2, 'w', encoding='utf-8') as f:
        f.write(content)

path3 = 'src/main/java/com/trd/menu/industrial/ElectricFurnaceMenu.java'
with open(path3, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('net.minecraftforge.common.capabilities', 'net.neoforged.neoforge.capabilities')
with open(path3, 'w', encoding='utf-8') as f:
    f.write(content)

path4 = 'src/main/java/com/trd/network/packet/energy/SyncMotorRpmPacket.java'
with open(path4, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('import com.trd.api.rotation.', '// import com.trd.api.rotation.')
with open(path4, 'w', encoding='utf-8') as f:
    f.write(content)
