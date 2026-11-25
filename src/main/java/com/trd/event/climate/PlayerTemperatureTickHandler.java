package com.trd.event.climate;

import com.trd.api.nature.climate.system.ModAttachments;
import com.trd.api.nature.climate.system.calculation.ShelterHelper;
import com.trd.api.nature.climate.system.calculation.TemperatureCalculator;
import com.trd.api.nature.climate.system.PlayerTemperatureData;
import com.trd.api.nature.climate.system.TemperatureEffectApplier;
import com.trd.api.nature.climate.system.network.SyncTemperaturePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Серверный обработчик тика и событий игрока.
 * - Управляет ванильной заморозкой (ticksFrozen) каждый тик, обеспечивая
 *   полный визуальный и звуковой эффект замерзания как в рыхлом снеге.
 * - Раз в секунду рассчитывает климат, укрытие, инерцию и применяет эффекты.
 * - Синхронизирует данные с клиентом при тиках, входе и респавне.
 */
@EventBusSubscriber(modid = "trd")
public class PlayerTemperatureTickHandler {

    private static final float ADAPTATION_SPEED = 0.1f; // Скорость адаптации температуры тела

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        
        // Обработка только на стороне сервера
        if (player.level().isClientSide() || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        // В креативе и спектаторе эффекты климата отключены
        if (player.isSpectator() || player.isCreative()) {
            if (player.getTicksFrozen() > 0) {
                player.setTicksFrozen(0);
            }
            return;
        }

        PlayerTemperatureData data = player.getData(ModAttachments.PLAYER_TEMPERATURE);
        float currentTemp = data.getCurrentTemp();

        // -------------------------------------------------------------
        // ПОТИКОВАЯ ЛОГИКА (20 раз в секунду):
        // Поддержание эффекта замерзания (powder snow mechanics)
        // -------------------------------------------------------------
        int maxFreeze = player.getTicksRequiredToFreeze();
        if (currentTemp < 0.0f) {
            player.setTicksFrozen(maxFreeze);
        } else if (currentTemp < 10.0f) {
            // Сильное переохлаждение: плавная изморозь
            float coldProgress = (10.0f - currentTemp) / 10.0f;
            int targetFreeze = (int) (coldProgress * (maxFreeze - 1));
            player.setTicksFrozen(targetFreeze);
        } else {
            if (!player.isInPowderSnow && player.getTicksFrozen() > 0) {
                player.setTicksFrozen(Math.max(0, player.getTicksFrozen() - 2));
            }
        }

        // -------------------------------------------------------------
        // СЕКУНДНАЯ ЛОГИКА (1 раз в 20 тиков со сдвигом по ID игрока):
        // -------------------------------------------------------------
        if ((player.tickCount + player.getId()) % 20 != 0) {
            return;
        }

        BlockPos pos = player.blockPosition();
        
        // 1. Расчет уровня укрытия (пещеры + роза ветров или изолированная комната)
        float shelterLevel;
        com.trd.api.nature.climate.system.room.SealedRoom room = com.trd.api.nature.climate.system.room.SealedRoomManager.findRoomAt(level, pos);
        if (room != null && room.isStructureValid()) {
            shelterLevel = room.isSealed() ? 1.0f : 0.85f;
        } else {
            shelterLevel = ShelterHelper.calculateShelterLevel(level, pos);
        }
        data.setShelterLevel(shelterLevel);
        
        // 2. Расчет целевой температуры среды
        float targetEnv = TemperatureCalculator.calculateTargetEnvTemp(player, level, shelterLevel);
        data.setTargetTemp(targetEnv);

        // 3. Тепловая инерция организма
        float newCurrent = TemperatureCalculator.applyThermalInertia(currentTemp, targetEnv, ADAPTATION_SPEED);
        data.setCurrentTemp(newCurrent);

        // 4. Применение игровых эффектов (урон от замерзания, жара, дебаффы)
        TemperatureEffectApplier.applyTemperatureEffects(player, data);

        // 5. Синхронизация с клиентом
        PacketDistributor.sendToPlayer(
            (ServerPlayer) player,
            new SyncTemperaturePayload(newCurrent, targetEnv)
        );

        // 6. Синхронизация герметичных комнат рядом с игроком (для клиентского расчёта
        // температуры: дебаг-оверлей и термометры должны видеть комнатный климат)
        java.util.List<com.trd.api.nature.climate.system.room.SealedRoom> rooms =
                com.trd.api.nature.climate.system.room.SealedRoomManager.getRoomsNear(level, pos, 32.0);
        java.util.List<com.trd.api.nature.climate.system.network.SyncRoomClimatePayload.RoomData> roomData =
                new java.util.ArrayList<>(rooms.size());
        for (com.trd.api.nature.climate.system.room.SealedRoom r : rooms) {
            net.minecraft.world.phys.AABB box = r.getInteriorBox();
            if (box == null) continue;
            roomData.add(new com.trd.api.nature.climate.system.network.SyncRoomClimatePayload.RoomData(
                    r.getControllerPos(),
                    box.minX, box.minY, box.minZ,
                    box.maxX, box.maxY, box.maxZ,
                    r.getVolume(), r.getRoomTemperature(), r.isSealed()
            ));
        }
        PacketDistributor.sendToPlayer(
            (ServerPlayer) player,
            new com.trd.api.nature.climate.system.network.SyncRoomClimatePayload(roomData)
        );

        // 7. Живое состояние источников: клиентский дебаг-оверлей и ПКМ-термометр
        //    должны читать тот же запас/физичность, что и серверные расчёты.
        java.util.List<com.trd.api.nature.climate.system.network.SyncThermalStatesPayload.Entry> thermalData =
                new java.util.ArrayList<>();
        for (var entry : com.trd.api.nature.climate.system.room.ThermalStateStore.get(level).entriesNear(pos, 112.0)) {
            if (thermalData.size() >= 512) {
                break; // лимит StreamCodec — больше нет смысла слать состояния вне поля зрения
            }
            thermalData.add(new com.trd.api.nature.climate.system.network.SyncThermalStatesPayload.Entry(
                    entry.pos(), entry.state().reserve(), entry.state().physical()));
        }
        PacketDistributor.sendToPlayer(
            (ServerPlayer) player,
            new com.trd.api.nature.climate.system.network.SyncThermalStatesPayload(thermalData)
        );
    }

    /**
     * Мгновенная синхронизация температуры при входе игрока на сервер.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerTemperatureData data = player.getData(ModAttachments.PLAYER_TEMPERATURE);
            PacketDistributor.sendToPlayer(
                player,
                new SyncTemperaturePayload(data.getCurrentTemp(), data.getTargetTemp())
            );
        }
    }

    /**
     * Синхронизация при респавне игрока (после смерти).
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerTemperatureData data = player.getData(ModAttachments.PLAYER_TEMPERATURE);
            // При респавне возвращаем к нейтральной комфортной температуре
            data.setCurrentTemp(20.0f);
            data.setTargetTemp(20.0f);
            player.setTicksFrozen(0);
            PacketDistributor.sendToPlayer(
                player,
                new SyncTemperaturePayload(20.0f, 20.0f)
            );
        }
    }
}
