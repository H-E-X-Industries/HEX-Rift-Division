package com.trd.client.gltf.item.tools;

import com.trd.main.MainRegistry;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Подсказывает облику кирки, чья это рука рисуется в третьем лице.
 *
 * <h2>Зачем это нужно</h2>
 * У анимации замаха состояние своё у каждого игрока: двое замахнувшихся в одном
 * кадре должны выглядеть каждый по-своему. Но предметному рендереру сущность не
 * передаётся — {@code BlockEntityWithoutLevelRenderer#renderByItem} получает
 * только стак и контекст, а контекст говорит про руку, но не про её хозяина.
 *
 * <p>Поэтому хозяин берётся из события отрисовки живого существа:
 * {@link RenderLivingEvent.Pre} прилетает до отрисовки слоя рук, и на всё время
 * отрисовки игрока известно, чьи это руки. От третьего лица это и есть ровно то,
 * что нужно.
 *
 * <p>Руки самого игрока от первого лица событие не затрагивает: там облик и так
 * берёт локального игрока напрямую.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public final class CastPickaxeHandOwner {

    private CastPickaxeHandOwner() {
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        CastPickaxeGltfAppearance.setHolder(event.getEntity() instanceof Player player ? player : null);
    }

    /**
     * Сбрасывает владельца после отрисовки.
     *
     * <p>Нужен, чтобы следующая отрисовка рук — уже от первого лица — не унаследовала
     * чужого игрока.
     */
    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        CastPickaxeGltfAppearance.setHolder(null);
    }
}