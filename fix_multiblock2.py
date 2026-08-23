import re

file_path = r'c:\developing\HEX-Rift-Division\src\main\java\com\trd\multiblock\system\MultiblockStructureHelper.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

new_method = '''    public boolean hasWireObstruction(Level level, BlockPos controllerPos, BlockState state) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return false;
        com.trd.api.energy.EnergyNetworkManager manager = com.trd.api.energy.EnergyNetworkManager.get(serverLevel);
        
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
        net.minecraft.core.Direction.Axis axis = net.minecraft.core.Direction.Axis.Z;
        boolean hasFacing = false;
        boolean hasAxis = false;

        if (state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
            facing = state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
            hasFacing = true;
        } else if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
            facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
            hasFacing = true;
        }

        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)) {
            axis = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS);
            hasAxis = true;
        }

        for (BlockPos relativePos : structureMap.keySet()) {
            BlockPos worldPos;
            if (hasFacing && hasAxis) {
                worldPos = getRotatedStatorPos(controllerPos, relativePos, facing, axis);
            } else if (hasAxis) {
                worldPos = getRotatedPosAxis(controllerPos, relativePos, axis);
            } else if (hasFacing) {
                worldPos = getRotatedPos(controllerPos, relativePos, facing);
            } else {
                worldPos = controllerPos.offset(relativePos);
            }

            if (manager.isBlockObstructingAnyWire(worldPos)) {
                return true;
            }
        }
        return false;
    }
'''

# Insert it before the first checkPlacement method
pattern = r'public boolean checkPlacement\(Level level, BlockPos controllerPos, Direction facing, Player player\) \{'
content = content.replace('public boolean checkPlacement(Level level, BlockPos controllerPos, Direction facing, Player player) {', new_method + '\n    public boolean checkPlacement(Level level, BlockPos controllerPos, Direction facing, Player player) {')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
