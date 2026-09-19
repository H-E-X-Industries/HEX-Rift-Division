import os

files = [
    'src/main/java/com/trd/block/basic/industrial/energy/SwitchBlock.java',
    'src/main/java/com/trd/block/basic/industrial/energy/ConverterBlock.java',
    'src/main/java/com/trd/block/basic/industrial/energy/PaintableWireBlock.java',
]

new_use = '''@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack heldItem, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
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

for file in files:
    with open(file, 'r', encoding='utf-8') as f:
        c = f.read()

    c = c.replace('''@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)''', new_use)
    c = c.replace('''public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)''', new_use)

    with open(file, 'w', encoding='utf-8') as f:
        f.write(c)
