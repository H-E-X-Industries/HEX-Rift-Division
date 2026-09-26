package com.trd.client.rotation;

import com.trd.block.entity.industrial.rotation.KineticNodeBlockEntity;
import com.trd.main.MainRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Централизованный клиентский трекер углов вращения для кинетических узлов.
 * Гарантирует:
 * 1. Идентичный угол (синхронность по фазе) для всех валов и подключенных механизмов одной сети.
 * 2. Учет направления (facing) блока: валы на одной оси, направленные навстречу (EAST / WEST и т.д.),
 *    вращаются в одну физическую сторону без рассинхрона шва текстуры.
 * 3. Плавное торможение/разгон и привязку (snap) к 45° при остановке.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class ClientKineticAngleTracker {

    private static final Map<UUID, NetworkAngleState> NETWORK_STATES = new ConcurrentHashMap<>();
    private static final Map<BlockPos, NetworkAngleState> POS_STATES = new ConcurrentHashMap<>();

    private static class NetworkAngleState {
        float angle = 0f;
        float smoothedSpeed = 0f;
        float lastFrameTime = -1f;
    }

    public static float getAngle(KineticNodeBlockEntity be, Direction facing, float partialTick) {
        if (be == null || be.getLevel() == null || be.isRemoved()) return 0f;

        float timeInSeconds = (be.getLevel().getGameTime() + partialTick) / 20.0f;
        UUID netId = be.getNetworkId();
        NetworkAngleState state;

        if (netId != null) {
            state = NETWORK_STATES.computeIfAbsent(netId, k -> new NetworkAngleState());
        } else {
            state = POS_STATES.computeIfAbsent(be.getBlockPos(), k -> new NetworkAngleState());
        }

        if (state.lastFrameTime < 0) {
            state.lastFrameTime = timeInSeconds;
        }

        float deltaSeconds = timeInSeconds - state.lastFrameTime;
        if (deltaSeconds > 0.25f || deltaSeconds <= 0f) {
            deltaSeconds = 0.016f;
        }

        if (Math.abs(timeInSeconds - state.lastFrameTime) > 0.0001f) {
            state.lastFrameTime = timeInSeconds;

            float speed = be.getSpeed();
            float scale = be.getNetworkScale();
            float physicalTargetSpeed = (scale != 0) ? (speed / scale) : speed;

            float maxRenderSpeed = 300f;
            float targetSpeed = physicalTargetSpeed;
            if (Math.abs(targetSpeed) > maxRenderSpeed) {
                targetSpeed = Math.signum(targetSpeed) * maxRenderSpeed;
            }

            float speedDiff = targetSpeed - state.smoothedSpeed;
            if (Math.abs(speedDiff) > 0.01f) {
                state.smoothedSpeed += speedDiff * 5.0f * deltaSeconds;
            } else {
                state.smoothedSpeed = targetSpeed;
            }

            state.angle += state.smoothedSpeed * ((float) Math.PI / 30.0f) * deltaSeconds;
            float twoPi = (float) (2 * Math.PI);
            state.angle = state.angle % twoPi;
            if (state.angle < 0) state.angle += twoPi;

            if (targetSpeed == 0 && Math.abs(state.smoothedSpeed) < 5.0f) {
                float PI_OVER_4 = (float) (Math.PI / 4.0);
                float targetSnap = Math.round(state.angle / PI_OVER_4) * PI_OVER_4;
                float snapDiff = targetSnap - state.angle;
                if (Math.abs(snapDiff) > 0.001f) {
                    float pull = 6.0f * (1.0f - (Math.abs(state.smoothedSpeed) / 5.0f));
                    state.angle += snapDiff * pull * deltaSeconds;
                } else {
                    state.angle = targetSnap;
                }
            }
        }

        float resultAngle = state.angle * be.getNetworkScale();

        if (facing != null) {
            boolean invert = (facing == Direction.SOUTH || facing == Direction.WEST || facing == Direction.DOWN);
            if (invert) {
                resultAngle = -resultAngle;
            }
        }

        float twoPi = (float) (2 * Math.PI);
        resultAngle = resultAngle % twoPi;
        if (resultAngle < 0) resultAngle += twoPi;

        return resultAngle;
    }

    public static void clear() {
        NETWORK_STATES.clear();
        POS_STATES.clear();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clear();
        }
    }
}
