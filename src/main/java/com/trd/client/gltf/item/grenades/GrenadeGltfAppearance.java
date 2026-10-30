package com.trd.client.gltf.item.grenades;

import com.hex.hexrender.client.ItemModelDescriptor;
import com.hex.hexrender.loader.GltfModelAsset;
import com.hex.hexrender.core.ModelRegistry;
import com.hex.hexrender.motion.SkeletalAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.trd.item.weapons.grenades.ChargableGrenadeItem;
import com.trd.item.weapons.grenades.GrenadeIfAnimation;
import com.trd.item.weapons.grenades.GrenadeIfClientAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.Function;

/**
 * Облик гранаты в руке на glTF через HexRender.
 *
 * <p>Общий для всех видов: геометрия у них одна и та же, а различаются они только
 * тем, какую текстуру зашили в свой файл модели.
 */
public class GrenadeGltfAppearance implements ItemModelDescriptor {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final boolean DEBUG_CHARGE_SWAY =
            Boolean.getBoolean("trd.debugGrenadeSway");

    private final Function<Item, ResourceLocation> modelId;

    public GrenadeGltfAppearance(Function<Item, ResourceLocation> modelId) {
        this.modelId = modelId;
    }

    private static final float MODEL_SCALE = 1.0F;
    private static final float CHARGE_SWAY_DEGREES = 6.0F;
    private static final float CROSS_AXIS_RATIO = 0.45F;
    private static final float CHARGE_FREQUENCY = 0.62F;
    private static final float REQUIP_DROP = 0.6F;

    private static float chargeOf(ItemStack stack, @Nullable ItemDisplayContext context) {
        if (handOf(context) == null) {
            return 0.0F;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isUsingItem()) {
            return 0.0F;
        }
        if (player.getUseItem().getItem() != stack.getItem()) {
            return 0.0F;
        }

        return Math.min(1.0F, player.getTicksUsingItem() / (float) ChargableGrenadeItem.MAX_CHARGE_TICKS);
    }

    private static InteractionHand handOf(@Nullable ItemDisplayContext context) {
        if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            return InteractionHand.MAIN_HAND;
        }
        if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    @Override
    public @Nullable GltfModelAsset model(ItemStack stack, ItemDisplayContext context) {
        GltfModelAsset model = ModelRegistry.fetch(modelId.apply(stack.getItem()));
        if (model != null) {
            GrenadeIfClientAnim.syncDurations(model);
        }
        return model;
    }

    @Override
    public @Nullable SkeletalAnimation clip(ItemStack stack, ItemDisplayContext context) {
        InteractionHand hand = handOf(context);
        if (hand == null || GrenadeIfClientAnim.current(hand) == null) {
            return null;
        }
        GltfModelAsset model = model(stack, context);
        return model != null ? model.animation(GrenadeIfAnimation.PIN_PULL) : null;
    }

    @Override
    public float seconds(ItemStack stack, ItemDisplayContext context, float partialTick) {
        InteractionHand hand = handOf(context);
        return hand == null || GrenadeIfClientAnim.current(hand) == null
                ? 0.0F
                : GrenadeIfClientAnim.seconds(hand, partialTick);
    }

    @Override
    public void transform(ItemStack stack, ItemDisplayContext context, PoseStack poseStack) {
        if (handOf(context) != null) {
            poseStack.translate(0.0F, -0.09F, 0.0F);
        }

        float requip = GrenadeIfClientAnim.requip(handOf(context));
        if (requip > 0.0F) {
            poseStack.translate(0.0F, -REQUIP_DROP * requip, 0.0F);
        }

        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        float charge = chargeOf(stack, context);
        if (charge > 0.0F) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;

            float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            float phase = (player.getTicksUsingItem() + partialTick) * CHARGE_FREQUENCY;

            float sway = CHARGE_SWAY_DEGREES * charge;
            float swayZ = Mth.sin(phase) * sway;
            float swayX = Mth.cos(phase * 1.6F) * sway * CROSS_AXIS_RATIO;

            debugSway(player.getTicksUsingItem(), partialTick, charge, swayZ, swayX);

            float pivotY = getPivotY(stack.getItem());
            poseStack.translate(0.0F, pivotY, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(swayZ));
            poseStack.mulPose(Axis.XP.rotationDegrees(swayX));
            poseStack.translate(0.0F, -pivotY, 0.0F);
        }
    }

    private static float getPivotY(Item item) {
        if (item instanceof com.trd.item.weapons.grenades.GrenadeIfItem) {
            return 0.23515F;
        }
        if (item instanceof com.trd.item.weapons.grenades.GrenadeNucItem) {
            return ClassicGrenadeVariants.HYDROGEN_CENTER_Y;
        }
        return ClassicGrenadeVariants.SHARED_CENTER_Y;
    }

    private static void debugSway(int ticksUsingItem, float partialTick, float charge, float swayZ, float swayX) {
        if (!DEBUG_CHARGE_SWAY) {
            return;
        }
        LOGGER.info("grenade sway: ticks={} partial={} charge={} swayZ={} swayX={}",
                ticksUsingItem, partialTick, charge, swayZ, swayX);
    }
}
