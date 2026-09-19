import os
import re

with open('src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java', 'r', encoding='utf-8') as f:
    c = f.read()

# Replace the screwdriver logic with empty hand logic
# We need to replace:
# if (heldItem.getItem() instanceof ScrewdriverItem) {
#   if (!battery.isCellEmpty(cellSlot)) {
# ...
#   if (!player.isCreative()) { heldItem.hurtAndBreak ... }
# ...
#   return InteractionResult.CONSUME; } } }

# Let's write a regex or string replace
old_code = '''if (heldItem.getItem() instanceof ScrewdriverItem) {
                    if (!battery.isCellEmpty(cellSlot)) {
                        ItemStack extracted = battery.extractCell(cellSlot);
                        if (!extracted.isEmpty()) {
                            if (!player.getInventory().add(extracted)) {
                                popResource(level, pos.relative(facing), extracted);
                            }
                            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.2f);

                            // [??] ? >??? ?'?'?'? ? ?'?
                            if (!player.isCreative()) {
                                heldItem.hurtAndBreak(1, player, hand == net.minecraft.world.InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
                            }

                            player.displayClientMessage(Component.translatable("gui.trd.machine_battery.cell_extracted", cellSlot + 1), true);
                            return InteractionResult.CONSUME;
                        }
                    }
                }'''

new_code = '''if (heldItem.isEmpty()) {
                    if (!battery.isCellEmpty(cellSlot)) {
                        ItemStack extracted = battery.extractCell(cellSlot);
                        if (!extracted.isEmpty()) {
                            if (!player.getInventory().add(extracted)) {
                                popResource(level, pos.relative(facing), extracted);
                            }
                            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.2f);
                            player.displayClientMessage(Component.translatable("gui.trd.machine_battery.cell_extracted", cellSlot + 1), true);
                            return InteractionResult.CONSUME;
                        }
                    }
                }'''

if old_code in c:
    c = c.replace(old_code, new_code)
else:
    print("Could not find exact string, attempting regex...")
    # fallback

with open('src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java', 'w', encoding='utf-8') as f:
    f.write(c)

