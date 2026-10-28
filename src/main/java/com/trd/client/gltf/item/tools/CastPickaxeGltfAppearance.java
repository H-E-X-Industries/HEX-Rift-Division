package com.trd.client.gltf.item.tools;

import com.hex.hexrender.client.ItemModelDescriptor;
import com.hex.hexrender.loader.GltfModelAsset;
import com.hex.hexrender.core.ModelRegistry;
import com.hex.hexrender.motion.SkeletalAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.trd.item.tools.cast_pickaxes.CastPickaxeClientAnim;
import com.trd.item.tools.cast_pickaxes.CastPickaxeItem;
import com.trd.item.tools.cast_pickaxes.materials.CastPickaxeSteelItem;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Облик литой кирки на glTF через HexRender.
 *
 * <p>Модель общая у обеих кирок — {@code assets/trd/models/item/cast_pickaxe.gltf},
 * различаются они только текстурой, подменяемой через {@link #textureOverrides}.
 */
public class CastPickaxeGltfAppearance implements ItemModelDescriptor {

    public static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "models/item/cast_pickaxe.gltf");

    private static final ResourceLocation IRON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("trd", "textures/item/cast_pickaxe_iron.png");
    private static final ResourceLocation STEEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("trd", "textures/item/cast_pickaxe_steel.png");

    @Nullable
    private static Player holder;

    private CastPickaxeGltfAppearance() {
    }

    public static CastPickaxeGltfAppearance create() {
        return new CastPickaxeGltfAppearance();
    }

    public static boolean isHand(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    @Nullable
    public static InteractionHand handOf(ItemDisplayContext context) {
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

    public static void setHolder(@Nullable Player player) {
        holder = player;
    }

    @Override
    public @Nullable GltfModelAsset model(ItemStack stack, ItemDisplayContext context) {
        GltfModelAsset model = ModelRegistry.fetch(MODEL);
        if (model != null) {
            CastPickaxeClientAnim.syncDurations(model);
        }
        return model;
    }

    @Override
    public @Nullable SkeletalAnimation clip(ItemStack stack, ItemDisplayContext context) {
        Player player = playerOf(stack, context);
        if (player == null) {
            return null;
        }

        String current = CastPickaxeClientAnim.current(player, handOf(context));
        if (current == null) {
            return null;
        }

        GltfModelAsset model = model(stack, context);
        return model != null ? model.animation(current) : null;
    }

    @Override
    public float seconds(ItemStack stack, ItemDisplayContext context, float partialTick) {
        Player player = playerOf(stack, context);
        if (player == null) {
            return 0.0F;
        }

        return CastPickaxeClientAnim.seconds(player, handOf(context), partialTick);
    }

    @Override
    public void transform(ItemStack stack, ItemDisplayContext context, PoseStack poseStack) {
    }

    @Override
    public @Nullable Map<ResourceLocation, ResourceLocation> textureOverrides(ItemStack stack, ItemDisplayContext context) {
        ResourceLocation target = stack.getItem() instanceof CastPickaxeSteelItem ? STEEL_TEXTURE : IRON_TEXTURE;
        return Map.of(STEEL_TEXTURE, target);
    }

    @Nullable
    private static Player playerOf(ItemStack stack, ItemDisplayContext context) {
        if (!isHand(context) || !(stack.getItem() instanceof CastPickaxeItem)) {
            return null;
        }

        if (context.firstPerson()) {
            return Minecraft.getInstance().player;
        }

        Player current = holder;
        if (current == null || !current.isAlive()) {
            return null;
        }
        return current;
    }
}
