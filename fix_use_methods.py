import os

files = [
    'src/main/java/com/trd/block/basic/industrial/ElectricFurnaceBlock.java',
    'src/main/java/com/trd/block/basic/industrial/energy/MachineBatteryBlock.java',
    'src/main/java/com/trd/block/basic/industrial/energy/SwitchBlock.java',
    'src/main/java/com/trd/block/basic/industrial/energy/ConverterBlock.java',
    'src/main/java/com/trd/block/basic/industrial/energy/PaintableWireBlock.java',
    'src/main/java/com/trd/block/basic/industrial/MillstoneBlock.java'
]

for file in files:
    if not os.path.exists(file): continue
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()

    # Find the old use signature
    if 'public InteractionResult use(' in content:
        # For simple blocks without complex item logic, we just change to useWithoutItem
        if 'ElectricFurnaceBlock' in file or 'MillstoneBlock' in file or 'SwitchBlock' in file or 'ConverterBlock' in file:
            content = content.replace('public InteractionResult use(BlockState state, Level level, BlockPos pos,\n                                 Player player, InteractionHand hand, BlockHitResult hit)',
                '@Override\n    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)')
            content = content.replace('public InteractionResult use(BlockState state, Level level, BlockPos pos,\n                                   Player player, InteractionHand hand, BlockHitResult hit)',
                '@Override\n    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)')
            content = content.replace('public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)',
                '@Override\n    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)')
        
        elif 'MachineBatteryBlock' in file:
            # MachineBatteryBlock needs useItemOn and useWithoutItem
            old_use = '''public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit)'''
            
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
            
            content = content.replace(old_use, new_use)

        with open(file, 'w', encoding='utf-8') as f:
            f.write(content)

