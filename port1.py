import os

def port_electric_furnace():
    path = 'src/main/java/com/trd/block/basic/industrial/ElectricFurnaceBlock.java'
    with open(path, 'r', encoding='utf-8') as f:
        c = f.read()
    
    # Remove NetworkHooks import
    c = c.replace('import net.minecraftforge.network.NetworkHooks;\n', '')
    
    # Add codec
    if 'public static final MapCodec<ElectricFurnaceBlock> CODEC' not in c:
        c = c.replace('public class ElectricFurnaceBlock extends BaseEntityBlock {', 
            'public class ElectricFurnaceBlock extends BaseEntityBlock {\n    public static final com.mojang.serialization.MapCodec<ElectricFurnaceBlock> CODEC = simpleCodec(ElectricFurnaceBlock::new);\n    @Override\n    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }')

    # Fix useWithoutItem and openMenu
    old_use = '''    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ElectricFurnaceBlockEntity be) {
            NetworkHooks.openScreen((ServerPlayer) player, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("gui.trd.electric_furnace");
                }
                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new ElectricFurnaceMenu(id, inv, be, be.dataAccess);
                }
            }, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }'''
    
    new_use = '''    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ElectricFurnaceBlockEntity be) {
            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("gui.trd.electric_furnace");
                }
                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new ElectricFurnaceMenu(id, inv, be, be.dataAccess);
                }
            }, pos);
        }
        return InteractionResult.CONSUME;
    }'''
    c = c.replace(old_use, new_use)

    with open(path, 'w', encoding='utf-8') as f:
        f.write(c)

port_electric_furnace()
