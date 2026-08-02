package com.trd.api.conveyor;

import com.trd.block.basic.industrial.ConveyorBufferBlock;
import com.trd.block.entity.industrial.ConveyorBufferBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class ConveyorNetwork {
    private final UUID id;
    private final List<BlockPos> path = new ArrayList<>();
    private final List<ConveyorItem> items = new ArrayList<>();

    public static final double SPEED = 1.5 / 20.0;
    public static final double SPACING = 0.5; // Минимальное расстояние между предметами

    public ConveyorNetwork() {
        this.id = UUID.randomUUID();
    }

    public ConveyorNetwork(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public List<BlockPos> getPath() {
        return path;
    }

    public List<ConveyorItem> getItems() {
        return items;
    }

    public void addBlockToEnd(BlockPos pos) {
        path.add(pos);
    }

    public void addBlockToStart(BlockPos pos) {
        path.add(0, pos);
        // Смещаем все предметы вперед, так как добавился блок в начало
        for (ConveyorItem item : items) {
            item.setProgress(item.getProgress() + 1.0);
        }
    }

    public boolean tryInsertItem(ItemStack stack, double progress) {
        // Убрали лимит SPACING: предметы могут накладываться друг на друга "дорожкой"
        ConveyorItem newItem = new ConveyorItem(stack, progress);
        items.add(newItem);
        // Сортируем предметы по убыванию прогресса, чтобы обрабатывать сначала те, что ближе к концу
        sortItems();
        return true;
    }

    private void sortItems() {
        items.sort((a, b) -> Double.compare(b.getProgress(), a.getProgress()));
    }

    public boolean tick(ServerLevel level, ConveyorNetworkManager manager) {
        if (path.isEmpty() || items.isEmpty()) return false;

        boolean changed = false;
        double maxProgress = path.size();

        // Items are sorted descending by progress
        double nextObstacle = maxProgress + 1.0; // Изначально препятствие за пределами сети

        Iterator<ConveyorItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            ConveyorItem item = iterator.next();
            double currentProgress = item.getProgress();
            double desiredProgress = currentProgress + SPEED;
            
            if (desiredProgress >= maxProgress) {
                // Пытаемся передать в буфер (вставщик)
                BlockPos lastPos = path.get(path.size() - 1);
                BlockState lastState = level.getBlockState(lastPos);
                if (lastState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                    Direction facing = lastState.getValue(BlockStateProperties.HORIZONTAL_FACING);
                    BlockPos targetPos = lastPos.relative(facing);
                    
                    BlockEntity targetBe = level.getBlockEntity(targetPos);
                    boolean transferred = false;

                    if (targetBe instanceof ConveyorBufferBlockEntity buffer) {
                        if (buffer.getMode() == ConveyorBufferBlockEntity.Mode.INSERTER) {
                            if (buffer.tryAcceptItem(item.getStack().copy())) {
                                transferred = true;
                            } else {
                                // Инвентарь переполнен - выбрасываем предмет рядом
                                ejectItemAt(level, targetPos, item.getStack(), facing);
                                transferred = true;
                            }
                        }
                    }

                    if (transferred) {
                        iterator.remove();
                        changed = true;
                        continue;
                    } else if (targetBe instanceof com.trd.block.entity.industrial.ConveyorBlockEntity) {
                        ConveyorNetwork nextNet = manager.getNetworkFor(targetPos);
                        if (nextNet != null && nextNet != this) {
                            if (nextNet.tryInsertItem(item.getStack().copy(), 0.0)) {
                                iterator.remove();
                                changed = true;
                                continue;
                            } else {
                                // Если следующая сеть забита, мы стопоримся
                                desiredProgress = Math.min(desiredProgress, maxProgress - 0.01);
                                transferred = true;
                            }
                        }
                    }

                    if (!transferred) {
                        // Если впереди ничего нет или это не вставщик, просто выбрасываем
                        ejectItemAt(level, targetPos, item.getStack(), facing);
                        iterator.remove();
                        changed = true;
                        continue;
                    }
                }
                
                // Если дошли сюда, предмет не смог выйти (например, стопор)
                desiredProgress = Math.min(desiredProgress, maxProgress - 0.01);
            }

            if (desiredProgress > currentProgress) {
                item.setProgress(desiredProgress);
                changed = true;
            }
        }

        return changed;
    }

    public void ejectItemAt(ServerLevel level, BlockPos pos, ItemStack stack, Direction facing) {
        Vec3 ejectPos = Vec3.atCenterOf(pos).add(0, 0.2, 0);
        ItemEntity itemEntity = new ItemEntity(level, ejectPos.x, ejectPos.y, ejectPos.z, stack);
        itemEntity.setDeltaMovement(facing != null ? facing.getStepX() * 0.1 : 0, 0.1, facing != null ? facing.getStepZ() * 0.1 : 0);
        itemEntity.setPickUpDelay(10);
        level.addFreshEntity(itemEntity);
    }
    
    public void ejectItemAt(ServerLevel level, BlockPos pos, ItemStack stack) {
        ejectItemAt(level, pos, stack, null);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);

        ListTag pathTag = new ListTag();
        for (BlockPos pos : path) {
            pathTag.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
        }
        tag.put("Path", pathTag);

        ListTag itemsTag = new ListTag();
        for (ConveyorItem item : items) {
            itemsTag.add(item.serializeNBT());
        }
        tag.put("Items", itemsTag);

        return tag;
    }

    public static ConveyorNetwork deserializeNBT(CompoundTag tag) {
        ConveyorNetwork net = new ConveyorNetwork(tag.getUUID("Id"));

        ListTag pathTag = tag.getList("Path", Tag.TAG_LONG);
        for (int i = 0; i < pathTag.size(); i++) {
            net.path.add(BlockPos.of(((net.minecraft.nbt.LongTag) pathTag.get(i)).getAsLong()));
        }

        ListTag itemsTag = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < itemsTag.size(); i++) {
            net.items.add(new ConveyorItem(itemsTag.getCompound(i)));
        }
        net.sortItems();

        return net;
    }
}
