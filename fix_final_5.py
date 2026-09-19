import sys
import re

path = 'src/main/java/com/trd/block/basic/industrial/energy/WireBlock.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = re.sub(
    r'return level\.getCapability\(net\.neoforged\.neoforge\.capabilities\.Capabilities\.EnergyStorage\.BLOCK, pos, be\.getBlockState\(\), be, sideFromNeighbor\) != null \? true : false\(net\.neoforged\.neoforge\.capabilities\.Capabilities\.EnergyStorage\.BLOCK, pos, be\.getBlockState\(\), be, sideFromNeighbor\)', 
    r'return level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, pos, be.getBlockState(), be, sideFromNeighbor) != null;', 
    content
)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
