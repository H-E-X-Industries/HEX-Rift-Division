import os

with open('src/main/java/com/trd/capability/ModCapabilities.java', 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('public class ModCapabilities {', '@net.neoforged.fml.common.EventBusSubscriber(modid = com.trd.main.MainRegistry.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)\npublic class ModCapabilities {')

with open('src/main/java/com/trd/capability/ModCapabilities.java', 'w', encoding='utf-8') as f:
    f.write(c)
