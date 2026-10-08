package com.trd.client.gecko.item.grenades;

import com.mojang.logging.LogUtils;
import com.trd.item.ModItems;
import com.trd.main.MainRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.slf4j.Logger;

/**
 * Клиентские расширения ударной гранаты: свой рендерер предмета и поза руки.
 *
 * <h2>Почему не {@code Item#initializeClient}</h2>
 * Старый способ — перекрыть {@code initializeClient} и отдать рендерер через
 * {@code IClientItemExtensions#getCustomRenderer()} — в NeoForge 21.1 уже не
 * работает. Метод {@code ClientExtensionsManager#earlyInit()}, который обходил
 * реестр предметов и звал его, не вызывается ниоткуда: {@code ClientHooks} зовёт
 * только {@code init()}, а тот лишь публикует {@link RegisterClientExtensionsEvent}.
 * В итоге расширение молча не регистрировалось, и предмет рисовался плоским
 * спрайтом из {@code models/item}, хотя рендерер был написан.
 *
 * <h2>Почему своя подписка, а не метод в общем клиентском классе</h2>
 * Событие помечено {@code IModBusEvent}, то есть летит по <b>шине модов</b>.
 * Общий {@code ModClientSetup} подписан на игровую, и подписчик рядом с ним
 * событие бы не увидел: оно прошло бы мимо молча, ровно как раньше.
 *
 * <h2>Почему рендерер свой на каждый вид</h2>
 * {@code GemRenderItemRenderer} держит состояние перехода между клипами, и оно
 * общее на все контексты отрисовки. Один такой рендерер на несколько предметов
 * означал бы, что переход одного вида видит другой, поэтому на каждый предмет
 * приходится свой экземпляр.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GrenadeIfClientItem {

    private static final Logger LOGGER = LogUtils.getLogger();

    private GrenadeIfClientItem() {
    }

    /** Выдаёт всем ударным гранатам их glTF-рендерер. */
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        for (var deferred : ModItems.GRENADE_IF_ITEMS) {
            Item item = deferred.get();
            event.registerItem(new Appearance(), item);
            LOGGER.info("Impact grenade {}: registered the glTF item renderer", deferred.getId().getPath());
        }
    }

    /** Расширение одного вида ударной гранаты. */
    private static final class Appearance implements IClientItemExtensions {

        private GrenadeIfRenderer renderer;

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer == null) {
                renderer = new GrenadeIfRenderer();
            }
            return renderer;
        }

        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            // Граната больше не натягивается, а летит сразу, поэтому поза лука у
            // неё не осталось: обычная поза предмета в руке.
            return HumanoidModel.ArmPose.ITEM;
        }
    }
}