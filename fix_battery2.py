import os
import re

with open('src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

out = []
skip = False
for line in lines:
    if "if (heldItem.getItem() instanceof ScrewdriverItem) {" in line:
        skip = True
        out.append("                if (heldItem.isEmpty()) {\n")
        out.append("                    if (!battery.isCellEmpty(cellSlot)) {\n")
        out.append("                        ItemStack extracted = battery.extractCell(cellSlot);\n")
        out.append("                        if (!extracted.isEmpty()) {\n")
        out.append("                            if (!player.getInventory().add(extracted)) {\n")
        out.append("                                popResource(level, pos.relative(facing), extracted);\n")
        out.append("                            }\n")
        out.append("                            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.2f);\n")
        out.append("                            player.displayClientMessage(Component.translatable(\"gui.trd.machine_battery.cell_extracted\", cellSlot + 1), true);\n")
        out.append("                            return InteractionResult.CONSUME;\n")
        out.append("                        }\n")
        out.append("                    }\n")
        out.append("                }\n")
        continue
    if skip:
        if "if (heldItem.getItem() instanceof EnergyCellItem) {" in line:
            skip = False
        else:
            continue
    
    if "if (heldItem.isEmpty() || (!(heldItem.getItem() instanceof EnergyCellItem) && !(heldItem.getItem() instanceof ScrewdriverItem))) {" in line:
        out.append("        if (heldItem.isEmpty() || !(heldItem.getItem() instanceof EnergyCellItem)) {\n")
        continue
        
    out.append(line)

with open('src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java', 'w', encoding='utf-8') as f:
    f.writelines(out)

