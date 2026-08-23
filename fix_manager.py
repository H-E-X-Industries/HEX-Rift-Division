import re

file_path = r'c:\developing\HEX-Rift-Division\src\main\java\com\trd\api\energy\EnergyNetworkManager.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

new_methods = '''public boolean isBlockObstructingAnyWire(net.minecraft.world.phys.shapes.VoxelShape shape, BlockPos placedPos) {
        if (shape.isEmpty()) return false;
        
        AABB blockBox = shape.bounds().move(placedPos);

        for (EnergyNode node : allNodes.values()) {
            BlockPos startPos = node.getPos();

            if (startPos.distSqr(placedPos) > 1024) continue;
            if (!level.isLoaded(startPos)) continue;

            BlockEntity be = level.getBlockEntity(startPos);
            if (be instanceof ConnectorBlockEntity connector) {
                for (BlockPos endPos : connector.getConnections()) {
                    if (startPos.asLong() < endPos.asLong()) {
                        AABB wireBox = new AABB(startPos).minmax(new AABB(endPos)).inflate(0.5);
                        if (wireBox.intersects(blockBox)) {
                            if (wireIntersectsShape(connector, endPos, shape, placedPos)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean wireIntersectsShape(ConnectorBlockEntity startBe, BlockPos endPos, net.minecraft.world.phys.shapes.VoxelShape blockShape, BlockPos placedPos) {
        if (!level.isLoaded(endPos)) return false;
        BlockEntity be = level.getBlockEntity(endPos);
        if (!(be instanceof ConnectorBlockEntity endBe)) return false;

        Vec3 start = startBe.getWireAttachmentPoint();
        Vec3 end = endBe.getWireAttachmentPoint();

        com.trd.util.CatenaryHelper.CatenaryData data = com.trd.util.CatenaryHelper.compute(start, end);
        
        double distance = start.distanceTo(end);
        int segments = Math.max(12, (int) (distance * 3));
        Vec3 prev = start;

        for (int i = 1; i <= segments; i++) {
            Vec3 current = data.getPoint((double) i / segments);
            if (blockShape.clip(prev, current, placedPos) != null) {
                return true;
            }
            prev = current;
        }
        return false;
    }
}'''

# Replace from public boolean isBlockObstructingAnyWire to end of file
pattern = r'public boolean isBlockObstructingAnyWire\(BlockPos placedPos\) \{.*'
content = re.sub(pattern, new_methods, content, flags=re.DOTALL)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
