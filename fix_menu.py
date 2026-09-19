import sys

path = 'src/main/java/com/trd/menu/ModMenuTypes.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

reg = 'public static final DeferredRegister<MenuType<?>> MENUS =\n            DeferredRegister.create(Registries.MENU, MainRegistry.MOD_ID);'
efm = 'public static final java.util.function.Supplier<MenuType<com.trd.menu.industrial.ElectricFurnaceMenu>> ELECTRIC_FURNACE_MENU = MENUS.register("electric_furnace_menu", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id, inv, data) -> new com.trd.menu.industrial.ElectricFurnaceMenu(id, inv, data)));'

content = content.replace(efm, '').replace(reg, reg + '\n    ' + efm)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
