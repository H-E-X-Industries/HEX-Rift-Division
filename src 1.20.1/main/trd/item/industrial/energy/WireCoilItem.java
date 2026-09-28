package com.trd.item.industrial.energy;

import com.trd.block.entity.industrial.energy.ConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.trd.util.CatenaryHelper;
import com.trd.multiblock.system.IMultiblockPart;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public class WireCoilItem extends Item {
    public static final int MAX_WIRES = 32;

    public WireCoilItem(Properties properties) {
        super(properties);
    }

    // ===================== ЗАРЯД КАТУШКИ =====================

    public static int getWires(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt("Wires") : 0;
    }

    public static void setWires(ItemStack stack, int wires) {
        if (wires <= 0) {
            stack.removeTagKey("Wires");
        } else {
            stack.getOrCreateTag().putInt("Wires", Math.min(wires, MAX_WIRES));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.trd.wire_coil.wires",
                getWires(stack), MAX_WIRES).withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    // Полоска прочности показывает остаток провода
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round((getWires(stack) / (float) MAX_WIRES) * 13f);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float fraction = getWires(stack) / (float) MAX_WIRES;
        // Классический ванильный градиент: полная — зелёная, к концу — жёлто-красная
        if (fraction <= 0f) return 0xFF555555; // пустая катушка — серая полоска
        return net.minecraft.util.Mth.hsvToRgb(fraction / 3.0f, 1.0f, 1.0f);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);

        boolean creative = player != null && player.isCreative();

        // Если кликнули НЕ по коннектору
        if (!(be instanceof ConnectorBlockEntity currentConnector)) {
            // Если игрок кликнул с Shift по воздуху/другому блоку — сбрасываем сохраненные координаты
            if (player != null && player.isShiftKeyDown() && stack.hasTag() && stack.getTag().contains("FirstPos")) {
                stack.getTag().remove("FirstPos");
                player.displayClientMessage(Component.translatable("message.trd.wire_coil.cancelled"), true);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        // Пустая катушка не работает (в креативе провода бесконечны)
        int wires = getWires(stack);
        if (wires <= 0 && !creative) {
            if (player != null)
                player.displayClientMessage(Component.translatable("message.trd.wire_coil.no_wires"), true);
            return InteractionResult.FAIL;
        }

        CompoundTag tag = stack.getOrCreateTag();

        // ================= ПЕРВЫЙ КЛИК =================
        if (!tag.contains("FirstPos")) {
            // Проверяем, есть ли свободные слоты для проводов у этого коннектора
            if (currentConnector.getConnections().size() >= currentConnector.getTier().maxConnections()) {
                if (player != null) player.displayClientMessage(Component.translatable("message.trd.wire_coil.connector_full"), true);
                return InteractionResult.FAIL;
            }

            // Сохраняем координаты в предмет
            tag.put("FirstPos", NbtUtils.writeBlockPos(pos));
            if (player != null) player.displayClientMessage(Component.translatable("message.trd.wire_coil.started"), true);
            return InteractionResult.SUCCESS;
        }

        // ================= ВТОРОЙ КЛИК =================
        else {
            BlockPos firstPos = NbtUtils.readBlockPos(tag.getCompound("FirstPos"));

            // Очищаем тег в любом случае, чтобы игрок не застрял, если произойдёт ошибка
            tag.remove("FirstPos");

            // 1. Проверка на клик по тому же самому блоку
            if (pos.equals(firstPos)) {
                if (player != null) player.displayClientMessage(Component.translatable("message.trd.wire_coil.self_connect"), true);
                return InteractionResult.FAIL;
            }

            BlockEntity firstBe = level.getBlockEntity(firstPos);
            if (!(firstBe instanceof ConnectorBlockEntity firstConnector)) {
                if (player != null) player.displayClientMessage(Component.literal("§cПервый коннектор был разрушен или потерян."), true);
                return InteractionResult.FAIL;
            }

            // 2. Проверка лимитов подключений для ОБОИХ коннекторов
            if (firstConnector.getConnections().size() >= firstConnector.getTier().maxConnections()) {
                if (player != null) player.displayClientMessage(Component.translatable("message.trd.wire_coil.first_destroyed"), true);
                return InteractionResult.FAIL;
            }
            if (currentConnector.getConnections().size() >= currentConnector.getTier().maxConnections()) {
                if (player != null) player.displayClientMessage(Component.translatable("message.trd.wire_coil.second_full"), true);
                return InteractionResult.FAIL;
            }

            // 3. Проверка: не соединены ли они уже друг с другом?
            if (firstConnector.getConnections().contains(pos) || currentConnector.getConnections().contains(firstPos)) {
                if (player != null) player.displayClientMessage(Component.translatable("message.trd.wire_coil.already_connected"), true);
                return InteractionResult.FAIL;
            }

            // 4. Проверка дистанции (берём наименьшую из двух, чтобы нельзя было обмануть систему слабым коннектором)
            double distance = Math.sqrt(firstPos.distSqr(pos));
            int maxDist1 = firstConnector.getTier().maxLength();
            int maxDist2 = currentConnector.getTier().maxLength();
            int maxAllowed = Math.min(maxDist1, maxDist2);

            if (distance > maxAllowed) {
                if (player != null) player.displayClientMessage(Component.translatable("message.trd.wire_coil.too_far", maxAllowed), true);
                return InteractionResult.FAIL;
            }

            // 5. Проверка на препятствия
            if (isPathBlocked(level, firstConnector, currentConnector, player)) {
                return InteractionResult.FAIL;
            }

            // ================= УСПЕХ: СОЕДИНЯЕМ =================
            // Записываем друг друга в память
            firstConnector.connectTo(pos);
            currentConnector.connectTo(firstPos);

            // Расходуем один провод (кроме креатива)
            if (!creative) {
                setWires(stack, wires - 1);
            }

            if (player != null) player.displayClientMessage(Component.translatable("message.trd.wire_coil.success"), true);
            return InteractionResult.SUCCESS;
        }
    }

    private boolean isPathBlocked(Level level, ConnectorBlockEntity startBe, ConnectorBlockEntity endBe, Player player) {
        Vec3 start = startBe.getWireAttachmentPoint();
        Vec3 end = endBe.getWireAttachmentPoint();
        BlockPos startPos = startBe.getBlockPos();
        BlockPos endPos = endBe.getBlockPos();

        CatenaryHelper.CatenaryData data = CatenaryHelper.compute(start, end);
        int segments = 12; // Достаточно для проверки коллизий
        Vec3 prev = start;

        for (int i = 1; i <= segments; i++) {
            Vec3 current = data.getPoint((double) i / segments);
            BlockHitResult hit = level.clip(new ClipContext(prev, current, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos hitPos = hit.getBlockPos();
                if (!hitPos.equals(startPos) && !hitPos.equals(endPos)) {
                    if (player != null) {
                        BlockState hitState = level.getBlockState(hitPos);
                        String blockName = hitState.getBlock().getName().getString();

                        // Если это часть мультиблока, показываем название всей машины
                        BlockEntity be = level.getBlockEntity(hitPos);
                        if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
                            blockName = level.getBlockState(part.getControllerPos()).getBlock().getName().getString();
                        }

                        player.displayClientMessage(Component.translatable("message.trd.wire_coil.blocked", blockName), true);
                    }
                    return true;
                }
            }
            prev = current;
        }
        return false;
    }
}