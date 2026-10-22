package com.trd.block.basic.industrial.lighting;

import com.trd.block.basic.industrial.energy.ConnectorBlock;
import com.trd.block.basic.industrial.energy.WireBlock;
import com.trd.block.entity.industrial.energy.WireBlockEntity;
import com.trd.block.entity.industrial.lighting.FloodlightBlockEntity;
import com.trd.block.entity.industrial.lighting.SocketBlockEntity;
import com.trd.item.industrial.energy.WireCoilItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Утилітарний клас для єдиного керування портами підключення живлення (activeConnectionSide)
 * у прожекторах, патронах та світильниках.
 * Забезпечує:
 * 1. Авто-захоплення вільного порту (Smart Connect) при встановленні проводу.
 * 2. Ручне перемикання сторони при взаємодії з дротом у руці (LMB / RMB).
 * 3. Ізоляцію обертання прожектора (Aim-to-Look) суворо для рук без дроту.
 */
public final class PowerPortHelper {

    private PowerPortHelper() {}

    /**
     * Перевіряє, чи є предмет кабелем або котушкою проводу.
     */
    public static boolean isWireItem(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof WireCoilItem) {
            return true;
        }
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof WireBlock) {
            return true;
        }
        String id = stack.getItem().getDescriptionId().toLowerCase();
        return id.contains("wire") || id.contains("cable");
    }

    /**
     * Перевіряє, чи тримає гравець дріт або кабель у будь-якій руці.
     */
    public static boolean isHoldingWire(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        return isWireItem(player.getMainHandItem()) || isWireItem(player.getOffhandItem());
    }

    /**
     * Перевіряє, чи є блок у світі силовим кабелем або коннектором.
     */
    public static boolean isCableBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof WireBlock || block instanceof ConnectorBlock) {
            return true;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof WireBlockEntity) {
            return true;
        }
        String id = block.getDescriptionId().toLowerCase();
        return id.contains("wire") || id.contains("cable");
    }

    /**
     * Ручне перемикання порту живлення (ПКМ або ЛКМ з дротом у руці).
     */
    public static boolean handlePortSwitch(Level level, BlockPos pos, BlockState state, Player player, @Nullable Direction clickedFace) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return false;
        }

        Direction currentPort;
        Consumer<Direction> setPortFunc;
        Predicate<Direction> isValidSide;

        if (be instanceof FloodlightBlockEntity floodlightBE) {
            currentPort = floodlightBE.getActiveConnectionSide();
            setPortFunc = floodlightBE::setActiveConnectionSide;
            isValidSide = d -> true;
        } else if (be instanceof SocketBlockEntity socketBE) {
            currentPort = socketBE.getActiveConnectionSide();
            setPortFunc = socketBE::setActiveConnectionSide;
            Direction facing = state.hasProperty(BlockStateProperties.FACING)
                    ? state.getValue(BlockStateProperties.FACING)
                    : Direction.NORTH;
            isValidSide = d -> d != facing; // Колба світильника/патрона не може бути портом
        } else {
            return false;
        }

        // Збираємо список усіх сторін із реально підключеними сусідніми кабелями
        List<Direction> connectedCables = new ArrayList<>();
        for (Direction d : Direction.values()) {
            if (isValidSide.test(d) && isCableBlock(level, pos.relative(d))) {
                connectedCables.add(d);
            }
        }

        Direction newPort;
        if (clickedFace != null && isValidSide.test(clickedFace) && clickedFace != currentPort) {
            // Клік по конкретній вільній грані перемикає порт на неї
            newPort = clickedFace;
        } else if (connectedCables.size() > 1) {
            // Якщо кілька підключених кабелів — циклічно перемикаємо між ними
            int idx = connectedCables.indexOf(currentPort);
            newPort = connectedCables.get((idx + 1) % connectedCables.size());
        } else if (connectedCables.size() == 1 && !connectedCables.contains(currentPort)) {
            newPort = connectedCables.get(0);
        } else {
            // Циклічний перебір наступної валідної грані за годинниковою стрілкою
            Direction candidate = currentPort;
            for (int i = 1; i <= 6; i++) {
                Direction d = Direction.from3DDataValue((currentPort.get3DDataValue() + i) % 6);
                if (isValidSide.test(d)) {
                    candidate = d;
                    break;
                }
            }
            newPort = candidate;
        }

        setPortFunc.accept(newPort);
        level.updateNeighborsAt(pos, state.getBlock());

        if (!level.isClientSide) {
            player.displayClientMessage(
                    Component.literal("§6[Живлення] §fАктивний вхід: §e" + newPort.getName().toUpperCase()),
                    true
            );
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 1.2f);
        }
        return true;
    }

    /**
     * Авто-захоплення вільного порту (Smart Connect).
     * Якщо до поточного порту НЕ підключено кабель — захоплює новий суміжний провід.
     * Якщо поточний порт зайнятий — не перемикає (зберігає існуюче з'єднання).
     */
    public static boolean tryAutoConnect(Level level, BlockPos pos, BlockState state, @Nullable BlockPos fromPos) {
        if (level.isClientSide) {
            return false;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return false;
        }

        Direction currentPort;
        Consumer<Direction> setPortFunc;
        Predicate<Direction> isValidSide;

        if (be instanceof FloodlightBlockEntity floodlightBE) {
            currentPort = floodlightBE.getActiveConnectionSide();
            setPortFunc = floodlightBE::setActiveConnectionSide;
            isValidSide = d -> true;
        } else if (be instanceof SocketBlockEntity socketBE) {
            currentPort = socketBE.getActiveConnectionSide();
            setPortFunc = socketBE::setActiveConnectionSide;
            Direction facing = state.hasProperty(BlockStateProperties.FACING)
                    ? state.getValue(BlockStateProperties.FACING)
                    : Direction.NORTH;
            isValidSide = d -> d != facing;
        } else {
            return false;
        }

        // 1. Перевіряємо, чи зайнятий поточний активний порт реальним кабелем
        boolean currentPortOccupied = isCableBlock(level, pos.relative(currentPort));
        if (currentPortOccupied) {
            // Зберігаємо існуюче з'єднання — правило одного порту
            return false;
        }

        // 2. Поточний порт порожній. Перевіряємо сторону зміни:
        Direction changedDir = null;
        if (fromPos != null) {
            for (Direction d : Direction.values()) {
                if (pos.relative(d).equals(fromPos)) {
                    changedDir = d;
                    break;
                }
            }
        }

        if (changedDir != null && isValidSide.test(changedDir) && isCableBlock(level, fromPos)) {
            setPortFunc.accept(changedDir);
            level.updateNeighborsAt(pos, state.getBlock());
            return true;
        }

        // 3. Якщо оновлення з іншого боку або fromPos не вказано (наприклад при встановленні приладу),
        // шукаємо перший валідний суміжний кабель
        for (Direction d : Direction.values()) {
            if (isValidSide.test(d) && isCableBlock(level, pos.relative(d))) {
                setPortFunc.accept(d);
                level.updateNeighborsAt(pos, state.getBlock());
                return true;
            }
        }

        return false;
    }
}
