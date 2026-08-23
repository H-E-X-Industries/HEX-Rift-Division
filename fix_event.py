import re

file_path = r'c:\developing\HEX-Rift-Division\src\main\java\com\trd\event\EnergyPlacementEvents.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

new_logic = '''        EnergyNetworkManager manager = EnergyNetworkManager.get(serverLevel);
        
        boolean obstructed = false;
        if (state.getBlock() instanceof com.trd.multiblock.system.IMultiblockController controller) {
            obstructed = controller.getStructureHelper().hasWireObstruction(serverLevel, placedPos, state);
        } else {
            obstructed = manager.isBlockObstructingAnyWire(shape, placedPos);
        }

        if (obstructed) {
            // Разрешаем установку, но тут же ломаем блок, чтобы он выпал как предмет 
            // (это спасает мультиблоки от багов при отмене эвента)
            event.setCanceled(false);
            serverLevel.destroyBlock(placedPos, true);
            
            if (event.getEntity() instanceof Player player) {
                player.displayClientMessage(Component.literal("\u00A7c\u042d\u0442\u043e \u043c\u0435\u0441\u0442\u043e \u0437\u0430\u043d\u044f\u0442\u043e \u044d\u043b\u0435\u043a\u0442\u0440\u0438\u0447\u0435\u0441\u043a\u0438\u043c \u043f\u0440\u043e\u0432\u043e\u0434\u043e\u043c!"), true);
            }
        }
    }
}'''

pattern = r'EnergyNetworkManager manager = EnergyNetworkManager\.get\(serverLevel\);.*'
content = re.sub(pattern, new_logic, content, flags=re.DOTALL)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
