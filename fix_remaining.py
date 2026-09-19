import sys
import re

path = 'src/main/java/com/trd/item/industrial/energy/WireCoilItem.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('Level level = context.getLevel();', 'Level level = null;')
content = content.replace('net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound("FirstPos")).orElse(null)', 'net.minecraft.nbt.NbtUtils.readBlockPos(tag, "FirstPos").orElse(null)')
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

path2 = 'src/main/java/com/trd/item/industrial/energy/WireCoilWindingRecipe.java'
with open(path2, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('for (ItemStack stack : inv.getItems()) {', 'for (int i = 0; i < inv.size(); i++) { ItemStack stack = inv.getItem(i);')
content = content.replace('Serializer implements RecipeSerializer<WireCoilWindingRecipe> {', '''Serializer implements RecipeSerializer<WireCoilWindingRecipe> {
    private static final com.mojang.serialization.MapCodec<WireCoilWindingRecipe> CODEC = com.mojang.serialization.MapCodec.unit(new WireCoilWindingRecipe(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID, "wire_coil_winding")));
    private static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, WireCoilWindingRecipe> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.unit(new WireCoilWindingRecipe(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.trd.main.MainRegistry.MOD_ID, "wire_coil_winding")));
    @Override public com.mojang.serialization.MapCodec<WireCoilWindingRecipe> codec() { return CODEC; }
    @Override public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, WireCoilWindingRecipe> streamCodec() { return STREAM_CODEC; }
''')
content = re.sub(r'@Override\s+public WireCoilWindingRecipe fromJson.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'@Override\s+public WireCoilWindingRecipe fromNetwork.*?\}', '', content, flags=re.DOTALL)
content = re.sub(r'@Override\s+public void toNetwork.*?\}', '', content, flags=re.DOTALL)

with open(path2, 'w', encoding='utf-8') as f:
    f.write(content)

path3 = 'src/main/java/com/trd/main/ModCreativeTabs.java'
with open(path3, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('pOutput.accept(ModBlocks.MACHINE_BATTERY.get());', 'output.accept(ModBlocks.MACHINE_BATTERY.get());')
content = content.replace('pOutput.accept(ModBlocks.CONVERTER_BLOCK.get());', 'output.accept(ModBlocks.CONVERTER_BLOCK.get());')
content = content.replace('pOutput.accept(ModBlocks.WIRE_COATED.get());', 'output.accept(ModBlocks.WIRE_COATED.get());')
content = content.replace('pOutput.accept(ModBlocks.SWITCH.get());', 'output.accept(ModBlocks.SWITCH.get());')
with open(path3, 'w', encoding='utf-8') as f:
    f.write(content)
