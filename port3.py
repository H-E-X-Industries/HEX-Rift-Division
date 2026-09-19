import os

path = 'src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java'
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('NetworkHooks.openScreen((ServerPlayer) player, battery, pos);', 'player.openMenu(battery, pos);')

c = c.replace('battery.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {', 
'''net.neoforged.neoforge.items.IItemHandler handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, state, battery, null); if (handler != null) {''')
c = c.replace('''            });
        }
        super.onRemove(state, level, pos, newState, isMoving);''', '''            }
        }
        super.onRemove(state, level, pos, newState, isMoving);''')

c = c.replace('heldItem.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));', 'heldItem.hurtAndBreak(1, player, hand == net.minecraft.world.InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);')

c = c.replace('CompoundTag itemNbt = pStack.getTag();', 'net.minecraft.core.component.DataComponents comps = net.minecraft.core.component.DataComponents.CUSTOM_DATA; net.minecraft.world.item.component.CustomData itemNbt = pStack.getOrDefault(comps, net.minecraft.world.item.component.CustomData.EMPTY);')
c = c.replace('batteryBE.load(itemNbt.getCompound("BlockEntityTag"));', 'batteryBE.loadAdditional(itemNbt.copyTag().getCompound("BlockEntityTag"), level.registryAccess());')

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

