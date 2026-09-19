import os

files = [
    'src/main/java/com/trd/block/basic/industrial/ElectricFurnaceBlock.java',
    'src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java',
    'src/main/java/com/trd/block/basic/industrial/energy/SwitchBlock.java',
    'src/main/java/com/trd/block/basic/industrial/energy/ConverterBlock.java',
    'src/main/java/com/trd/block/basic/industrial/energy/PaintableWireBlock.java',
    'src/main/java/com/trd/block/basic/industrial/MillstoneBlock.java'
]

new_use = '''@Override
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

    public InteractionResult doUse(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit)'''

for file in files:
    if not os.path.exists(file): continue
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()

    # Revert my previous mistake for ElectricFurnaceBlock, ConverterBlock, etc.
    content = content.replace('@Override\n    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)',
        'public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)')
    
    # Now replace properly
    content = content.replace('public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)', new_use)
    
    # Formatting differences
    content = content.replace('''public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit)''', new_use)

    with open(file, 'w', encoding='utf-8') as f:
        f.write(content)

