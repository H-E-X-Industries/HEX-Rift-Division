import os

def fix_use(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Simple blocks (no item interaction needed)
    old = '''public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit)'''
    old2 = '''public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                   Player player, InteractionHand hand, BlockHitResult hit)'''
    
    new = '''@Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)'''
    
    content = content.replace(old, new)
    content = content.replace(old2, new)
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

fix_use('src/main/java/com/trd/block/basic/industrial/ElectricFurnaceBlock.java')
fix_use('src/main/java/com/trd/block/basic/industrial/MillstoneBlock.java')
