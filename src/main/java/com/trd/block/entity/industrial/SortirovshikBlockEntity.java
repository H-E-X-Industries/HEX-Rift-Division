package com.trd.block.entity.industrial;

import com.trd.api.conveyor.ConveyorItem;
import com.trd.api.conveyor.ConveyorNetwork;
import com.trd.api.conveyor.ConveyorNetworkManager;
import com.trd.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SortirovshikBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SECTIONS = 6;      // красная, оранжевая, жёлтая, зелёная, циановая, маджента
    public static final int FILTERS_PER_SECTION = 5;
    public static final int TOTAL_FILTER_SLOTS = SECTIONS * FILTERS_PER_SECTION;

    // Режимы секции (переключаются кнопкой по кругу)
    public static final int MODE_CLOSED = 0;     // направление закрыто (по умолчанию)
    public static final int MODE_BLACKLIST = 1;  // чёрный список
    public static final int MODE_WHITELIST = 2;  // белый список
    public static final int MODE_UNIVERSAL = 3;  // универсальное направление
    public static final int MODE_COUNT = 4;

    /**
     * Секции в порядке приоритета и в цветах GUI.
     * Красный всегда сверху, маджента всегда снизу.
     */
    public enum Section {
        RED(Direction.UP),
        ORANGE(Direction.NORTH),
        YELLOW(Direction.SOUTH),
        GREEN(Direction.WEST),
        CYAN(Direction.EAST),
        MAGENTA(Direction.DOWN);

        public final Direction direction;

        Section(Direction direction) {
            this.direction = direction;
        }

        public ChatFormatting color() {
            return switch (this) {
                case RED -> ChatFormatting.RED;
                case ORANGE -> ChatFormatting.GOLD;
                case YELLOW -> ChatFormatting.YELLOW;
                case GREEN -> ChatFormatting.GREEN;
                case CYAN -> ChatFormatting.AQUA;
                case MAGENTA -> ChatFormatting.LIGHT_PURPLE;
            };
        }
    }

    private final int[] modes = new int[SECTIONS];
    private final ItemStack[] filters = new ItemStack[TOTAL_FILTER_SLOTS];

    /**
     * Отложенная выдача предметов, принятых с тупиковой ленты (см. tryAcceptFromBelt).
     * Нельзя менять список сети прямо во время её тика — выдаём в начале следующего тика сортировщика.
     */
    private final ArrayList<PendingOutput> pendingOutputs = new ArrayList<>();

    private record PendingOutput(ItemStack stack, Direction direction) {}

    public SortirovshikBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SORTIROVSHIK_BE.get(), pos, state);
        for (int i = 0; i < TOTAL_FILTER_SLOTS; i++) filters[i] = ItemStack.EMPTY;
    }

    // === Данные для меню/GUI ===

    public int getMode(int section) {
        return modes[section];
    }

    public ItemStack getFilter(int index) {
        return filters[index];
    }

    public void cycleMode(int section) {
        modes[section] = (modes[section] + 1) % MODE_COUNT;
        setChanged();
        syncToClients();
    }

    /** Положить фантомный фильтр (копия стека в руке, предмет игрока не тратится). */
    public void setFilter(int index, ItemStack stack) {
        if (index < 0 || index >= TOTAL_FILTER_SLOTS) return;
        filters[index] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
        syncToClients();
    }

    /** Забрать фантомный фильтр — он просто исчезает, сортировщик забывает предмет. */
    public void clearFilter(int index) {
        if (index < 0 || index >= TOTAL_FILTER_SLOTS) return;
        if (filters[index].isEmpty()) return;
        filters[index] = ItemStack.EMPTY;
        setChanged();
        syncToClients();
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    // === Логика сортировки ===

    /**
     * Предмет с ленты, упирающейся торцом в сортировщик (вызывается из ConveyorNetwork
     * в момент достижения предметом конца ленты).
     * Принимает, если предмет прошёл чей-то фильтр; выдача откладывается до ближайшего
     * тика сортировщика, чтобы не мутировать список сети во время её итерации.
     */
    public boolean tryAcceptFromBelt(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(level instanceof net.minecraft.server.level.ServerLevel)) return false;
        int section = matchSection(stack);
        if (section < 0) return false;
        pendingOutputs.add(new PendingOutput(stack, Section.values()[section].direction));
        return true;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SortirovshikBlockEntity be) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        // Сканируем каждый тик: предмет не должен успеть визуально "застрять"
        // напротив сортировщика (особенно в тупике ленты)
        be.sortTick(serverLevel);
    }

    private void sortTick(net.minecraft.server.level.ServerLevel serverLevel) {
        // Сначала отдаём принятое с тупиковых лент — вне итераций сетей
        flushPendingOutputs(serverLevel);

        ConveyorNetworkManager manager = ConveyorNetworkManager.get(serverLevel);

        // Ищем конвейерные сети среди всех 6 соседей (сеть примыкающая двумя сторонами обрабатывается один раз)
        Set<ConveyorNetwork> visited = new HashSet<>();
        for (Direction side : Direction.values()) {
            BlockPos beltPos = worldPosition.relative(side);
            ConveyorNetwork net = manager.getNetworkFor(beltPos);
            if (net == null || !visited.add(net)) continue;

            double index = net.getPath().indexOf(beltPos);
            if (index < 0) continue;

            List<ConveyorItem> items = net.getItems();

            // Сначала собираем совпавшие предметы и только потом мутируем список:
            // выдача может вставить предмет обратно в ЭТУ же сеть (кольцевая схема),
            // и нельзя переставлять список, пока мы его обходим
            List<ConveyorItem> matched = new ArrayList<>(0);
            for (ConveyorItem item : items) {
                double progress = item.getProgress();
                // Предмет находится напротив сортировщика (блок ленты, соседний с ним)
                if (progress < index || progress >= index + 1.0) continue;
                if (matchSection(item.getStack()) < 0) continue;
                matched.add(item);
            }

            if (!matched.isEmpty()) {
                items.removeAll(matched); // equals не переопределён — сравнение по ссылке
                for (ConveyorItem item : matched) {
                    outputStack(serverLevel, manager, sectionOf(item.getStack()), item.getStack().copy());
                }
                manager.markForSync(net);
                manager.setDirty();
            }
        }
    }

    /** Направление секции, принявшей предмет (вызывать только если matchSection >= 0). */
    private Direction sectionOf(ItemStack stack) {
        return Section.values()[matchSection(stack)].direction;
    }

    private void flushPendingOutputs(net.minecraft.server.level.ServerLevel serverLevel) {
        if (pendingOutputs.isEmpty()) return;
        ConveyorNetworkManager manager = ConveyorNetworkManager.get(serverLevel);
        for (PendingOutput pending : pendingOutputs) {
            outputStack(serverLevel, manager, pending.direction(), pending.stack());
        }
        pendingOutputs.clear();
    }

    /**
     * Определяет секцию, по направлению которой должен пойти предмет.
     * Секции проверяются по порядку (красная → маджента), побеждает первая подходящая.
     * Универсальные секции проверяются в последнюю очередь — в них уходят предметы,
     * не прошедшие фильтры всех других секций.
     */
    public int matchSection(ItemStack stack) {
        if (stack.isEmpty()) return -1;

        for (int s = 0; s < SECTIONS; s++) {
            switch (modes[s]) {
                case MODE_BLACKLIST -> {
                    if (!listContains(s, stack)) return s; // пропускает всё, кроме фильтров
                }
                case MODE_WHITELIST -> {
                    if (listContains(s, stack)) return s;  // пропускает только фильтры
                }
                default -> { }
            }
        }
        for (int s = 0; s < SECTIONS; s++) {
            if (modes[s] == MODE_UNIVERSAL) return s;
        }
        return -1;
    }

    /** Совпадение по предмету И его NBT-тегам. */
    private boolean listContains(int section, ItemStack stack) {
        int base = section * FILTERS_PER_SECTION;
        for (int i = 0; i < FILTERS_PER_SECTION; i++) {
            ItemStack filter = filters[base + i];
            if (!filter.isEmpty() && ItemStack.isSameItemSameTags(filter, stack)) return true;
        }
        return false;
    }

    /** Выдача предмета в направлении стороны: конвейер → инвентарь → выброс в мир. */
    private void outputStack(net.minecraft.server.level.ServerLevel serverLevel, ConveyorNetworkManager manager,
                             Direction dir, ItemStack stack) {
        BlockPos target = worldPosition.relative(dir);

        // 1. На соседний конвейер — ЗА сегмент, примыкающий к сортировщику.
        // Если вставить в сам этот сегмент, предмет тут же снова попадёт в окно
        // сканирования, повторно пройдёт фильтр и зациклится на первом блоке.
        ConveyorNetwork outNet = manager.getNetworkFor(target);
        if (outNet != null) {
            double insertAt = outNet.getPath().indexOf(target) + 1.0;
            outNet.tryInsertItem(stack, insertAt);
            manager.markForSync(outNet);
            manager.setDirty();
            return;
        }

        // 2. В инвентарь машины/сундука
        BlockEntity targetBe = serverLevel.getBlockEntity(target);
        if (targetBe != null) {
            IItemHandler handler = targetBe.getCapability(ForgeCapabilities.ITEM_HANDLER, dir.getOpposite()).orElse(null);
            if (handler == null) handler = targetBe.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
            if (handler != null) {
                ItemStack remainder = ItemHandlerHelper.insertItem(handler, stack, false);
                if (remainder.isEmpty()) return;
                stack = remainder;
            }
        }

        // 3. Ничего не принял — выбрасываем в мир в сторону направления
        Vec3 center = Vec3.atCenterOf(worldPosition);
        ItemEntity entity = new ItemEntity(serverLevel,
                center.x + dir.getStepX() * 0.55,
                center.y + dir.getStepY() * 0.55,
                center.z + dir.getStepZ() * 0.55,
                stack);
        double dy = dir.getStepY() == 0 ? 0.05 : dir.getStepY() * 0.15;
        entity.setDeltaMovement(dir.getStepX() * 0.15, dy, dir.getStepZ() * 0.15);
        entity.setPickUpDelay(10);
        serverLevel.addFreshEntity(entity);
    }

    // === Меню ===

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.trd.sortirovshik");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
        return new com.trd.menu.industrial.SortirovshikMenu(id, playerInv, this);
    }

    // === NBT ===

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putIntArray("Modes", modes);

        ListTag list = new ListTag();
        for (int i = 0; i < TOTAL_FILTER_SLOTS; i++) {
            list.add(filters[i].save(new CompoundTag()));
        }
        tag.put("Filters", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int[] savedModes = tag.getIntArray("Modes");
        for (int i = 0; i < SECTIONS; i++) {
            modes[i] = (i < savedModes.length) ? Math.floorMod(savedModes[i], MODE_COUNT) : MODE_CLOSED;
        }

        ListTag list = tag.getList("Filters", Tag.TAG_COMPOUND);
        for (int i = 0; i < TOTAL_FILTER_SLOTS; i++) {
            filters[i] = (i < list.size()) ? ItemStack.of(list.getCompound(i)) : ItemStack.EMPTY;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
