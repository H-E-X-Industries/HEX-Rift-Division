import os
import re

def port_block(path, class_name):
    with open(path, 'r', encoding='utf-8') as f:
        c = f.read()

    # Add codec
    if f'public static final com.mojang.serialization.MapCodec<{class_name}> CODEC' not in c:
        c = c.replace(f'public class {class_name} extends BaseEntityBlock {{', 
            f'public class {class_name} extends BaseEntityBlock {{\n    public static final com.mojang.serialization.MapCodec<{class_name}> CODEC = simpleCodec({class_name}::new);\n    @Override\n    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {{ return CODEC; }}')
        c = c.replace(f'public class {class_name} extends WireBlock {{', 
            f'public class {class_name} extends WireBlock {{\n    public static final com.mojang.serialization.MapCodec<{class_name}> CODEC = simpleCodec({class_name}::new);\n    @Override\n    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {{ return CODEC; }}')

    c = c.replace('import net.minecraftforge.network.NetworkHooks;\n', '')
    c = c.replace('import net.minecraftforge.common.capabilities.ForgeCapabilities;\n', '')

    with open(path, 'w', encoding='utf-8') as f:
        f.write(c)

port_block('src/main/java/com/trd/block/basic/industrial/energy/ConverterBlock.java', 'ConverterBlock')
port_block('src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java', 'MachineBatteryBlock')
port_block('src/main/java/com/trd/block/basic/industrial/energy/PaintableWireBlock.java', 'PaintableWireBlock')
port_block('src/main/java/com/trd/block/basic/industrial/energy/SwitchBlock.java', 'SwitchBlock')

