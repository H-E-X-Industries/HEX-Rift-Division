package com.trd.item.armor;

import com.trd.client.gecko.item.armor.GrenadierGogglesItemRenderer;
import com.trd.client.gecko.item.armor.GrenadierGogglesRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Очки гренадёра.
 * <p>
 * 1.20.1 отдавал рендереры через {@code Item#initializeClient} и
 * {@code IClientItemExtensions#getHumanoidArmorModel}. В GeckoLib 4.9.3 этот путь для
 * надетой брони больше не работает: миксин {@code HumanoidArmorLayerMixin} перехватывает
 * {@code renderArmorPiece} и спрашивает провайдера у предмета, а провайдер берётся из
 * кэша анимаций — то есть из {@link #createGeoRenderer(Consumer)}. Сама
 * {@code HumanoidArmorLayer} рисует только слои из {@code ArmorMaterial#layers()}, а у нас
 * их нет: очки целиком geo-модель.
 * <p>
 * Так что предмет реализует только {@code GeoItem}, а рендереры отдаёт через
 * {@link #createGeoRenderer(Consumer)}:
 * {@link GeoRenderProvider#getGeoItemRenderer()} — вид в инвентаре и в руке,
 * {@link GeoRenderProvider#getGeoArmorRenderer} — вид на голове у игрока и у зомби.
 */
public class GrenadierGogglesItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * Рендереры кэшируются: GeckoLib внутри них держит собственные ресурсы (модель,
     * текстура, буферы), а провайдер запрашивается на каждый рендер — и на каждый надетый
     * слот у каждой сущности каждый кадр.
     */
    @Nullable
    private BlockEntityWithoutLevelRenderer itemRenderer;
    @Nullable
    private GrenadierGogglesRenderer armorRenderer;

    public GrenadierGogglesItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    // 1.21.1: вторым параметром раньше был Level, теперь Item.TooltipContext
    @Override
    public void appendHoverText(ItemStack stack, @Nullable TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal("Countdown BOOM!!!!!!!!!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        tooltip.add(Component.translatable("item.trd.grenadier_goggles.desc.explosion_resist", 30).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (itemRenderer == null) {
                    itemRenderer = new GrenadierGogglesItemRenderer();
                }
                return itemRenderer;
            }

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(T entity, ItemStack stack,
                                                                                EquipmentSlot slot,
                                                                                HumanoidModel<T> originalModel) {
                if (slot != EquipmentSlot.HEAD) {
                    return null;
                }
                if (armorRenderer == null) {
                    armorRenderer = new GrenadierGogglesRenderer();
                }
                return armorRenderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> PlayState.CONTINUE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
