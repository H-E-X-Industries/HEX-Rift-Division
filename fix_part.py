import re

file_path = r'c:\developing\HEX-Rift-Division\src\main\java\com\trd\multiblock\system\MultiblockPartBlock.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

new_methods = '''    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
            BlockPos ctrlPos = part.getControllerPos();
            BlockState ctrlState = level.getBlockState(ctrlPos);
            if (ctrlState != null && ctrlState.getBlock() != this) {
                return ctrlState.getBlock().getExplosionResistance(ctrlState, level, ctrlPos, explosion);
            }
        }
        return super.getExplosionResistance(state, level, pos, explosion);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            if (!MultiblockStructureHelper.isDestroying()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
                    BlockPos ctrlPos = part.getControllerPos();
                    BlockState ctrlState = level.getBlockState(ctrlPos);
                    
                    if (ctrlState.getBlock() instanceof IMultiblockController controller) {
                        // Drop the controller item properly
                        ItemStack ctrlDrop = new ItemStack(ctrlState.getBlock());
                        BlockEntity ctrlBe = level.getBlockEntity(ctrlPos);

                        if (ctrlBe instanceof com.trd.block.entity.industrial.fluids.FluidBarrelBlockEntity) {
                            net.minecraft.nbt.CompoundTag beNbt = ctrlBe.saveWithoutMetadata();
                            beNbt.remove("Inventory");
                            ctrlDrop.addTagElement("BlockEntityTag", beNbt);
                        }
                        
                        Block.popResource(level, ctrlPos, ctrlDrop);

                        // Trigger the structure teardown
                        net.minecraft.core.Direction facing = ctrlState.hasProperty(HorizontalDirectionalBlock.FACING)
                                ? ctrlState.getValue(HorizontalDirectionalBlock.FACING) : net.minecraft.core.Direction.NORTH;
                        
                        if (ctrlState.hasProperty(com.trd.block.basic.industrial.rotation.StatorBlock.AXIS)) {
                            net.minecraft.core.Direction.Axis axis = ctrlState.getValue(com.trd.block.basic.industrial.rotation.StatorBlock.AXIS);
                            controller.getStructureHelper().destroyStructureStator(level, ctrlPos, facing, axis);
                        } else {
                            controller.getStructureHelper().destroyStructure(level, ctrlPos, facing);
                        }
                        
                        level.removeBlock(ctrlPos, false);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
'''

# Insert before playerWillDestroy
pattern = r'(\s+@Override\s+public void playerWillDestroy)'
content = re.sub(pattern, '\n' + new_methods + r'\1', content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
