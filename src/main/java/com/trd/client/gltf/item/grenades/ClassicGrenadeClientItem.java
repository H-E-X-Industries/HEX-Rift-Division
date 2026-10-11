package com.trd.client.gltf.item.grenades;

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
 * Клиентские расширения обычных гранат: свой рендерер предмета и поза руки.
 *
 * <p>Устроено ровно как у ударных гранат — см. {@link GrenadeIfClientItem}, оттуда
 * же взяты и обоснования: подписка на модовую шину вместо
 * {@code Item#initializeClient}, который в NeoForge 21.1 молча не вызывается, и
 * свой экземпляр рендерера на каждый предмет, потому что {@code GemRenderItemRenderer}
 * держит состояние перехода между клипами общее на все контексты отрисовки.
 *
 * <p><b>Временная мера.</b> Эти гранаты рисуются на чужой модели ударной гранаты,
 * пока у них не будет своих. См. {@link ClassicGrenadeVariants}.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClassicGrenadeClientItem {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ClassicGrenadeClientItem() {
    }

    /** Выдаёт всем обычным гранатам их glTF-рендерер. */
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        for (var deferred : ModItems.CLASSIC_GRENADE_ITEMS) {
            Item item = deferred.get();
            event.registerItem(new Appearance(), item);
            LOGGER.info("Grenade {}: registered the shared glTF item renderer",
                    deferred.getId().getPath());
        }
    }

    /** Расширение одного вида обычной гранаты. */
    private static final class Appearance implements IClientItemExtensions {

        private ClassicGrenadeRenderer renderer;

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer == null) {
                renderer = new ClassicGrenadeRenderer();
            }
            return renderer;
        }

        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            // Обычная поза предмета в руке — ровно то, что отдавал ванильный
            // Item#getArmPose до появления расширения. Натяжение при зарядке
            // рисует не поза руки, а UseAnim.BOW в ItemInHandRenderer, и оно
            // продолжает работать: подмена позы на луковую изменила бы вид от
            // третьего лица, а это уже не про текстуры.
            return HumanoidModel.ArmPose.ITEM;
        }
    }
}