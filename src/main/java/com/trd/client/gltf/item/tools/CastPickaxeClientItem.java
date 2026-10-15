package com.trd.client.gltf.item.tools;

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

import java.util.List;

/**
 * Клиентские расширения литых кирок: свой рендерер предмета и поза руки.
 *
 * <p>Устроено ровно как у гранат, см. {@link
 * com.trd.client.gltf.item.grenades.ClassicGrenadeClientItem}: подписка на
 * модовую шину вместо {@code Item#initializeClient}, который в NeoForge 21.1
 * молча не вызывается, и свой экземпляр рендерера на каждый предмет.
 *
 * <p>Обе кирки делят одну модель и различаются лишь текстурой, но рендерер у них
 * свой у каждого: у него есть состояние подставной текстуры, и общее на оба
 * предмета означало бы, что значок железной кирки рисуется со стальной
 * текстурой или наоборот — в зависимости от того, что отрисовывали последним.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CastPickaxeClientItem {

    private static final Logger LOGGER = LogUtils.getLogger();

    private CastPickaxeClientItem() {
    }

    /** Выдаёт обеим киркам их glTF-рендерер. */
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        for (var deferred : List.of(ModItems.CAST_PICKAXE_IRON, ModItems.CAST_PICKAXE_STEEL)) {
            Item item = deferred.get();
            event.registerItem(new Appearance(), item);
            LOGGER.info("Cast pickaxe {}: registered the glTF item renderer",
                    deferred.getId().getPath());
        }
    }

    /** Расширение одной кирки. */
    private static final class Appearance implements IClientItemExtensions {

        private CastPickaxeRenderer renderer;

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer == null) {
                renderer = new CastPickaxeRenderer();
            }
            return renderer;
        }

        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            // Поза лука, как у автопушки: кирка двуручная и от третьего лица
            // держится обеими руками перед собой, а не болтается в одной.
            //
            // На вид от первого лица поза не влияет — там ракурс задаёт
            // ItemInHandRenderer, и он смотрит на UseAnim, а не на ArmPose.
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
    }
}