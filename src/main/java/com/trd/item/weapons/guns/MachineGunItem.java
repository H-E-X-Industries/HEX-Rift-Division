package com.trd.item.weapons.guns;

import com.trd.client.config.ModKeyBindings;
import com.trd.client.gecko.item.guns.MachineGunRenderer;
import com.trd.entity.weapons.bullets.TurretBulletEntity;
import com.trd.item.weapons.ammo.AmmoRegistry;
import com.trd.main.MainRegistry;
import com.trd.network.packet.guns.PacketReloadGun;
import com.trd.network.packet.guns.PacketShoot;
import com.trd.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * Автоматическая 20-мм пушка. Питается патронами калибра {@code 20mm_turret},
 * которые регистрируются в {@link AmmoRegistry}. Стрельба и перезарядка идут
 * через пакеты: клиент только рисует и отправляет намерение, всё решение
 * принимает сервер.
 */
public class MachineGunItem extends Item implements GeoItem {

    private static final int SHOT_ANIM_TICKS = 14;
    private static final int MAG_CAPACITY = 24;
    private static final int MAX_TOTAL_AMMO = MAG_CAPACITY + 1;
    private static final int RELOAD_ANIM_TICKS = 100;
    private static final int FLIP_ANIM_TICKS = 80;
    private static final int RELOAD_AMMO_ADD_TICK = 95;
    private static final String LOADED_AMMO_ID_TAG = "LoadedAmmoID";
    private static final String GUN_CALIBER = "20mm_turret";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MachineGunItem(Properties properties) {
        super(properties.stacksTo(1));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (oldStack.getItem() == newStack.getItem() && !slotChanged) return false;
        return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }

    // === NBT МЕТОДЫ ===

    private static CompoundTag readTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void writeTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public int getAmmo(ItemStack stack) {
        return readTag(stack).getInt("Ammo");
    }

    public void setAmmo(ItemStack stack, int ammo) {
        CompoundTag tag = readTag(stack);
        tag.putInt("Ammo", Math.max(0, Math.min(ammo, MAX_TOTAL_AMMO)));
        writeTag(stack, tag);
    }

    public int getShootDelay(ItemStack stack) {
        return readTag(stack).getInt("ShootDelay");
    }

    public void setShootDelay(ItemStack stack, int delay) {
        CompoundTag tag = readTag(stack);
        tag.putInt("ShootDelay", delay);
        writeTag(stack, tag);
    }

    public int getReloadTimer(ItemStack stack) {
        return readTag(stack).getInt("ReloadTimer");
    }

    public void setReloadTimer(ItemStack stack, int timer) {
        CompoundTag tag = readTag(stack);
        tag.putInt("ReloadTimer", timer);
        writeTag(stack, tag);
    }

    public int getPendingAmmo(ItemStack stack) {
        return readTag(stack).getInt("PendingAmmo");
    }

    public void setPendingAmmo(ItemStack stack, int ammo) {
        CompoundTag tag = readTag(stack);
        tag.putInt("PendingAmmo", ammo);
        writeTag(stack, tag);
    }

    public String getLoadedAmmoID(ItemStack stack) {
        return readTag(stack).getString(LOADED_AMMO_ID_TAG);
    }

    public void setLoadedAmmoID(ItemStack stack, String ammoID) {
        CompoundTag tag = readTag(stack);
        tag.putString(LOADED_AMMO_ID_TAG, ammoID);
        writeTag(stack, tag);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        if (!level.isClientSide && entity instanceof Player player) {

            if (!isSelected) {
                if (getReloadTimer(stack) > 0) {
                    setReloadTimer(stack, 0);
                    setPendingAmmo(stack, 0);
                    player.getInventory().setChanged();
                }
                return;
            }

            int delay = getShootDelay(stack);
            if (delay > 0) setShootDelay(stack, delay - 1);

            int reloadTimer = getReloadTimer(stack);
            if (reloadTimer > 0) {
                setReloadTimer(stack, reloadTimer - 1);

                // Изъятие патронов из инвентаря на 50-м тике (2.5 сек)
                if (reloadTimer == (RELOAD_ANIM_TICKS - 50) || reloadTimer == (FLIP_ANIM_TICKS - 50)) {
                    int pending = getPendingAmmo(stack);
                    if (pending > 0 && !player.isCreative()) {
                        String loadedId = getLoadedAmmoID(stack);
                        if (loadedId != null && !loadedId.isEmpty()) {
                            consumeAmmoById(player, loadedId, pending);
                            player.getInventory().setChanged();
                        }
                    }
                }

                // Добавление патронов в оружие на 10-м тике (конец анимации)
                if (reloadTimer == (RELOAD_ANIM_TICKS - RELOAD_AMMO_ADD_TICK) ||
                        reloadTimer == (FLIP_ANIM_TICKS - RELOAD_AMMO_ADD_TICK)) {
                    int pending = getPendingAmmo(stack);
                    if (pending > 0) {
                        setAmmo(stack, getAmmo(stack) + pending);
                        setPendingAmmo(stack, 0);
                        syncHand(player, stack);
                    }
                }
            }
        }
    }

    /** Строковый id предмета, как его записывает {@link AmmoRegistry}. */
    private static String itemIdOf(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Подсчитывает количество патронов конкретного id в инвентаре (не изымая). */
    private int countAmmoById(Player player, String ammoId) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) continue;
            if (!AmmoRegistry.isValidAmmo(slot)) continue;
            if (!ammoId.equals(itemIdOf(slot))) continue;
            count += slot.getCount();
        }
        return count;
    }

    private void syncHand(Player player, ItemStack stack) {
        if (player instanceof ServerPlayer serverPlayer) {
            int slot = serverPlayer.getInventory().selected;
            serverPlayer.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2, 0, slot, stack));
        }
    }

    // === ПЕРЕЗАРЯДКА ===

    public void reloadGun(Player player, ItemStack stack) {
        if (player.level().isClientSide) return;
        if (getReloadTimer(stack) > 0) return;

        long instanceId = GeoItem.getOrAssignId(stack, (ServerLevel) player.level());
        int currentAmmo = getAmmo(stack);

        // 1) Полный магазин -> FLIP (разрядить/проверить)
        if (currentAmmo >= MAX_TOTAL_AMMO) {
            triggerAnim(player, instanceId, "controller", "flip");
            setReloadTimer(stack, FLIP_ANIM_TICKS);
            return;
        }

        String currentLoadedID = getLoadedAmmoID(stack);

        // Если в магазине что-то есть -> ищем строго такой же id.
        // Если магазин пуст -> ищем любой подходящий калибра.
        String targetAmmoId = findAmmoIdForReload(player,
                (currentAmmo > 0 && currentLoadedID != null && !currentLoadedID.isEmpty()) ? currentLoadedID : null);

        // 2) Подходящих патронов нет -> FLIP (даже в креативе)
        if (targetAmmoId == null) {
            triggerAnim(player, instanceId, "controller", "flip");
            setReloadTimer(stack, FLIP_ANIM_TICKS);
            return;
        }

        // 3) Патроны есть, начинаем перезарядку
        if (player.isCreative()) {
            int toAdd = MAX_TOTAL_AMMO - currentAmmo;
            setPendingAmmo(stack, toAdd);

            if (currentAmmo == 0) {
                setLoadedAmmoID(stack, targetAmmoId);
            }

            triggerAnim(player, instanceId, "controller", "reload");
            setReloadTimer(stack, RELOAD_ANIM_TICKS);
            return;
        }

        int needed = MAX_TOTAL_AMMO - currentAmmo;
        int available = countAmmoById(player, targetAmmoId);
        int taken = Math.min(needed, available);

        if (taken > 0) {
            if (currentAmmo == 0) {
                setLoadedAmmoID(stack, targetAmmoId);
            }
            setPendingAmmo(stack, taken);
            player.getInventory().setChanged();
            triggerAnim(player, instanceId, "controller", "reload");
            setReloadTimer(stack, RELOAD_ANIM_TICKS);
        } else {
            triggerAnim(player, instanceId, "controller", "flip");
            setReloadTimer(stack, FLIP_ANIM_TICKS);
        }
    }

    // === ЛОГИКА РАЗРЯДКИ ===

    public void unloadGun(ServerPlayer player, ItemStack stack) {
        int currentAmmo = getAmmo(stack);
        if (currentAmmo <= 0) return;
        if (getReloadTimer(stack) > 0) return;

        String loadedID = getLoadedAmmoID(stack);
        if (loadedID == null || loadedID.isEmpty()) return;

        AmmoRegistry.AmmoType ammoInfo = TurretBulletEntity.lookupAmmoType(loadedID);
        if (ammoInfo == null) return;

        int amountToReturn = player.isCreative() ? 1 : currentAmmo;

        ItemStack returnedStack = new ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(loadedID)),
                amountToReturn);

        if (!player.getInventory().add(returnedStack)) {
            player.drop(returnedStack, false);
        }

        setAmmo(stack, 0);
        setLoadedAmmoID(stack, "");

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    /** Ищет первый подходящий id патрона в инвентаре. Если requiredId != null, ищет строго его. */
    private String findAmmoIdForReload(Player player, String requiredId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) continue;
            if (!AmmoRegistry.isValidAmmo(slot)) continue;
            if (!GUN_CALIBER.equals(AmmoRegistry.getCaliber(slot))) continue;

            String id = itemIdOf(slot);

            if (requiredId != null && !requiredId.equals(id)) continue;

            return id;
        }
        return null;
    }

    /** Забирает патроны конкретного id из инвентаря. */
    private int consumeAmmoById(Player player, String ammoId, int needed) {
        int taken = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (taken >= needed) break;

            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) continue;
            if (!AmmoRegistry.isValidAmmo(slot)) continue;
            if (!ammoId.equals(itemIdOf(slot))) continue;

            int toTake = Math.min(slot.getCount(), needed - taken);
            slot.shrink(toTake);
            taken += toTake;
            if (slot.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        return taken;
    }

    // === СТРЕЛЬБА ===

    public void performShooting(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) return;
        if (getReloadTimer(stack) > 0 || getShootDelay(stack) > 0) return;

        int ammo = getAmmo(stack);

        // Пустой выстрел (ammo == 0)
        if (ammo <= 0) {
            SoundEvent drySound = ModSounds.DRY_FIRE.isBound() ? ModSounds.DRY_FIRE.get() : SoundEvents.DISPENSER_FAIL;
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    drySound, SoundSource.PLAYERS, 1.0F, 1.0F);

            setShootDelay(stack, SHOT_ANIM_TICKS);

            if (level instanceof ServerLevel serverLevel) {
                triggerAnim(player, GeoItem.getOrAssignId(stack, serverLevel), "controller", "shot_empty");
            }

            return;
        }

        String loadedID = getLoadedAmmoID(stack);

        if (!player.isCreative()) {
            setAmmo(stack, ammo - 1);
        }

        syncHand(player, stack);
        setShootDelay(stack, SHOT_ANIM_TICKS);

        if (!(level instanceof ServerLevel serverLevel)) return;

        TurretBulletEntity bullet = new TurretBulletEntity(serverLevel, player);

        AmmoRegistry.AmmoType ammoInfo = loadedID != null && !loadedID.isEmpty()
                ? TurretBulletEntity.lookupAmmoType(loadedID)
                : null;

        if (ammoInfo == null) {
            ammoInfo = new AmmoRegistry.AmmoType("default", GUN_CALIBER, 6.0f, 3.0f, false);
        }

        bullet.setAmmoType(ammoInfo);

        Vec3 lookDir = player.getLookAngle();
        Vec3 velocity = lookDir.normalize().add(
                level.random.nextGaussian() * 0.0075 * 1.0F,
                level.random.nextGaussian() * 0.0075 * 1.0F,
                level.random.nextGaussian() * 0.0075 * 1.0F
        ).scale(ammoInfo.speed);

        Vec3 right = lookDir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 spawnPos = player.position().add(right.scale(0.2)).add(0, player.getEyeY() - player.getY() - 0.1, 0);

        bullet.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        bullet.setDeltaMovement(velocity);
        bullet.alignToVelocity();

        serverLevel.addFreshEntity(bullet);

        float pitch = 0.9F + level.random.nextFloat() * 0.2F;
        SoundEvent shotSound = ModSounds.TURRET_FIRE.isBound() ? ModSounds.TURRET_FIRE.get() : SoundEvents.GENERIC_EXPLODE.value();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), shotSound, SoundSource.PLAYERS, 1.0F, pitch);

        triggerAnim(player, GeoItem.getOrAssignId(stack, serverLevel), "controller", "shot");
    }

    // === GECKOLIB КОНТРОЛЛЕР ===

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, event -> {
            // Предикат вызывается и на сервере, а логика ниже работает только с клиентским состоянием
            if (FMLEnvironment.dist.isClient()) {
                return MachineGunClientUtils.handleAnimation(this, event);
            }
            return PlayState.STOP;
        })
                .triggerableAnim("reload", RawAnimation.begin().thenPlay("reload"))
                .triggerableAnim("flip", RawAnimation.begin().thenPlay("flip"))
                .triggerableAnim("shot", RawAnimation.begin().thenPlay("shot"))
                .triggerableAnim("shot_empty", RawAnimation.begin().thenPlay("shot_empty"))
                .setSoundKeyframeHandler(event -> {
                    if (FMLEnvironment.dist.isClient()) {
                        String soundName = event.getKeyframeData().getSound();
                        if (soundName != null && !soundName.isEmpty()) {
                            MachineGunClientUtils.playSoundClient(soundName);
                        }
                    }
                }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private MachineGunRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new MachineGunRenderer();
                return renderer;
            }

            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        int ammoCount = getAmmo(stack);
        String ammoId = getLoadedAmmoID(stack);

        if (ammoCount > 0) {
            int inMag = ammoCount - 1;
            tooltip.add(Component.translatable("tooltip.trd.machinegun.ammo",
                    inMag, MAX_TOTAL_AMMO).withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("tooltip.trd.machinegun.ammo_empty").withStyle(ChatFormatting.RED));
            return;
        }

        if (ammoId == null || ammoId.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.trd.machinegun.type.standard").withStyle(ChatFormatting.GRAY));
            return;
        }

        AmmoRegistry.AmmoType ammoType = AmmoRegistry.getAmmoTypeById(ammoId);
        if (ammoType == null) {
            tooltip.add(Component.translatable("tooltip.trd.machinegun.type.unknown").withStyle(ChatFormatting.GRAY));
            return;
        }

        String typeKey = "tooltip.trd.machinegun.type.standard";
        if (ammoId.contains("piercing")) {
            typeKey = "tooltip.trd.machinegun.type.piercing";
        } else if (ammoId.contains("hollow")) {
            typeKey = "tooltip.trd.machinegun.type.hollow";
        } else if (ammoId.contains("fire") || ammoId.contains("incendiary")) {
            typeKey = "tooltip.trd.machinegun.type.incendiary";
        } else if (ammoId.contains("radio")) {
            typeKey = "tooltip.trd.machinegun.type.radio";
        }

        tooltip.add(Component.translatable(typeKey).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.trd.machinegun.damage", ammoType.damage)
                .withStyle(ChatFormatting.DARK_RED));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public double getBoneResetTime() {
        return 0;
    }

    // === КЛИЕНТ ===

    @EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
    public static class ClientHandlers {
        /**
         * Локальный интервал между пакетами. Намеренно меньше {@link #SHOT_ANIM_TICKS}:
         * темп стрельбы задаёт сервер через {@code ShootDelay}, а клиент только
         * сигнализирует о намерении. Если клиентский интервал равнялся бы
         * серверному, пакет мог прийти на тик раньше, чем сервер дозреет, и
         * выстрел молча терялся — при удержании ЛКМ это давало провалы.
         */
        private static final int CLIENT_MIN_INTERVAL = 2;

        private static int clientShootTimer = 0;

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;

            ItemStack stack = mc.player.getMainHandItem();
            if (!(stack.getItem() instanceof MachineGunItem)) {
                clientShootTimer = SHOT_ANIM_TICKS;
                return;
            }

            if (ModKeyBindings.RELOAD_KEY.consumeClick()) {
                PacketDistributor.sendToServer(new PacketReloadGun());
                return;
            }

            if (ModKeyBindings.UNLOAD_KEY.consumeClick()) {
                PacketDistributor.sendToServer(new com.trd.network.packet.guns.PacketUnloadGun());
                return;
            }

            if (clientShootTimer > 0) clientShootTimer--;

            // Проверки ReloadTimer здесь намеренно нет: сервер всё равно её делает,
            // а значение приходит на клиент только в конце перезарядки, то есть
            // может быть устаревшим и заблокировать стрельбу.
            if (mc.options.keyAttack.isDown() && clientShootTimer <= 0) {
                PacketDistributor.sendToServer(new PacketShoot());
                clientShootTimer = CLIENT_MIN_INTERVAL;
                mc.player.attackAnim = 0;
                mc.player.oAttackAnim = 0;
                mc.player.swinging = false;
            }
        }

        @SubscribeEvent
        public static void onInput(InputEvent.InteractionKeyMappingTriggered event) {
            if (!event.isAttack()) return;
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null && mc.player.getMainHandItem().getItem() instanceof MachineGunItem) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        }
    }

    @EventBusSubscriber(modid = MainRegistry.MOD_ID)
    public static class CommonHandlers {
        @SubscribeEvent
        public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
            if (event.getItemStack().getItem() instanceof MachineGunItem && !event.getEntity().isCreative()) {
                event.setCanceled(true);
            }
        }
    }
}
