import sys

path = 'src/main/java/com/trd/item/industrial/energy/EnergyCellItem.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('import net.minecraft.world.level.Level;', 'import net.minecraft.world.item.Item.TooltipContext;')
content = content.replace('public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level level, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag)', 'public void appendHoverText(@Nonnull ItemStack stack, TooltipContext context, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag)')
content = content.replace('super.appendHoverText(stack, level, tooltip, flag);', 'super.appendHoverText(stack, context, tooltip, flag);')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
