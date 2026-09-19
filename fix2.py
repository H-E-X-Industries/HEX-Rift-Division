import os

def fix_complex_use(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    if 'import net.minecraft.world.item.ItemStack;' not in content:
        content = content.replace('import net.minecraft.world.item.Item;', 'import net.minecraft.world.item.Item;\nimport net.minecraft.world.item.ItemStack;')
        
    old = '''public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit)'''
    old2 = '''public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                   Player player, InteractionHand hand, BlockHitResult hit)'''
    
    new = '''@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult res = doUse(state, level, pos, player, hand, hit);
        if (res == InteractionResult.SUCCESS) return net.minecraft.world.ItemInteractionResult.SUCCESS;
        if (res == InteractionResult.CONSUME) return net.minecraft.world.ItemInteractionResult.CONSUME;
        if (res == InteractionResult.PASS) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return doUse(state, level, pos, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
    }

    public InteractionResult doUse(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)'''

    content = content.replace(old, new)
    content = content.replace(old2, new)

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

fix_complex_use('src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java')
fix_complex_use('src/main/java/com/trd/block/basic/industrial/energy/SwitchBlock.java')
fix_complex_use('src/main/java/com/trd/block/basic/industrial/energy/ConverterBlock.java')
fix_complex_use('src/main/java/com/trd/block/basic/industrial/energy/PaintableWireBlock.java')

