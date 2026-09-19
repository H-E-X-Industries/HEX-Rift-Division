import sys

path = 'src/main/java/com/trd/block/basic/industrial/energy/WireBlock.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

replacement = '''
        BlockEntity be = world.getBlockEntity(neighborPos);
        if (be == null) {
            return false;
        }

        if (world instanceof net.minecraft.world.level.Level level) {
            if (level.getCapability(ModCapabilities.ENERGY_CONNECTOR, neighborPos, be.getBlockState(), be, sideFromNeighbor) != null) {
                return true;
            }
            if (level.getCapability(ModCapabilities.ENERGY_PROVIDER, neighborPos, be.getBlockState(), be, sideFromNeighbor) != null) {
                return true;
            }
            if (level.getCapability(ModCapabilities.ENERGY_RECEIVER, neighborPos, be.getBlockState(), be, sideFromNeighbor) != null) {
                return true;
            }
            return level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, neighborPos, be.getBlockState(), be, sideFromNeighbor) != null;
        }
        return false;
    }
'''

import re
content = re.sub(r'BlockEntity be = world\.getBlockEntity\(neighborPos\);[\s\S]*return level\.getCapability\(net\.neoforged\.neoforge\.capabilities\.Capabilities\.EnergyStorage\.BLOCK, pos, be\.getBlockState\(\), be, sideFromNeighbor\) != null;\n    }', replacement.strip() + '\n    }', content)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
