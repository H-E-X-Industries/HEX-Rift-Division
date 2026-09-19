import os

with open('src/main/java/com/trd/client/ModClientSetup.java', 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('event.register(com.trd.menu.ModMenuTypes.MACHINE_BATTERY_MENU.get(), com.trd.client.overlay.gui.GUIMachineBattery::new);',
'event.register(com.trd.menu.ModMenuTypes.MACHINE_BATTERY_MENU.get(), com.trd.client.overlay.gui.GUIMachineBattery::new);\n        event.register(com.trd.menu.ModMenuTypes.ELECTRIC_FURNACE_MENU.get(), com.trd.client.overlay.gui.GUIElectricFurnace::new);')

with open('src/main/java/com/trd/client/ModClientSetup.java', 'w', encoding='utf-8') as f:
    f.write(c)
