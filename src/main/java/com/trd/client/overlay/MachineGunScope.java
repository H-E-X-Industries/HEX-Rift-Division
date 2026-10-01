package com.trd.client.overlay;

import com.trd.item.weapons.guns.MachineGunItem;
import com.trd.main.MainRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;

/**
 * Оптический прицел пушки: зум, скрытый интерфейс и оверлей.
 *
 * <p>Состояние держится в статике, потому что его читают три независимых места:
 * рендер (оверлей и скрытие GUI), ввод (выход из прицела) и серверный расчёт
 * разброса. Отдельный класс-представитель тут ничего не решает.
 *
 * <p>Управление простое: ПКМ — вход и выход, ЛКМ в прицеле стреляет, любая
 * другая клавиша прицел сбрасывает. Автоматического выхода по таймеру нет —
 * прицел держится, пока игрок его не закрыл сам.
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public final class MachineGunScope {

    /** Текстура оверлея: чёрные углы закрывают мир, крест в центре остаётся. */
    private static final ResourceLocation SCOPE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "textures/overlay/scope.png");
    /**
     * Настоящий размер текстуры, 301x301. Важно указывать ровно его: blit
     * считает UV как {@code пиксели / textureWidth}, и при заниженном значении
     * UV уходит за 1.0 — текстура тайлится и прицел рисуется сеткой.
     */
    private static final int SCOPE_TEX_SIZE = 301;

    /** Во сколько раз поднимается точность в прицеле. */
    public static final float ACCURACY_BONUS = 2.0f;

    private static final double ZOOM_FACTOR = 2.5;
    private static final int FOV_APPLY_INTERVAL = 2;

    private static boolean scoped;
    private static boolean pendingToggle;
    private static int ticks;

    /** Кнопка ПКМ зажата: без этого удержание переключало бы прицел каждый повтор. */
    private static boolean useHeld;

    /**
     * FOV игрока до входа в прицел: зум считается от него, и на выходе это
     * значение возвращается. Брать «текущий» FOV нельзя — тогда повторные
     * входы делили бы его всё сильнее.
     */
    private static int savedFov = 70;

    private static final float SCOPE_MOVE_MULTIPLIER = 0.5f;

    /** id временного модификатора скорости, снимается при выходе из прицела. */
    private static final ResourceLocation MOVEMENT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "scope_slowdown");

    private MachineGunScope() {
    }

    public static boolean isScoped() {
        return scoped;
    }

    /** Множитель разброса: в прицеле точность выше, то есть разброс ниже. */
    public static float spreadMultiplier() {
        return scoped ? 1.0f / ACCURACY_BONUS : 1.0f;
    }

    public static float moveMultiplier() {
        return scoped ? SCOPE_MOVE_MULTIPLIER : 1.0f;
    }

    @SubscribeEvent
    public static void onInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem()) {
            if (heldGun() != null) {
                event.setCanceled(true);
                event.setSwingHand(false);
                // Переключаем только на нажатии. Событие приходит и на
                // автоповторе удержания, а без проверки прицел открывался бы
                // и тут же закрывался, пока игрок держит ПКМ.
                if (!useHeld) {
                    useHeld = true;
                    pendingToggle = true;
                }
            }
            return;
        }

        // Прицел сбрасывает всё, кроме самого огня: и клавиши, и ЛКМ по
        // блоку, и движение. Выстрел прицел не закрывает — для этого он и нужен.
        // ПКМ сюда не попадает: он обрабатывается выше и переключает прицел.
        if (scoped && !event.isAttack() && !event.isUseItem()) {
            close();
        }

        }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            close();
            applyMoveMultiplier(null);
            return;
        }

        // Состояние кнопок берём из самого key mapping, а не из события:
        // событие срабатывает только в момент нажатия и на автоповторе, и
        // отпускания не порождает вовсе. Отпускание ПКМ снимает прицел —
        // иначе удержание переключало бы его десятки раз в секунду.
        boolean useDown = mc.options.keyUse.isDown();
        if (useDown != useHeld) {
            useHeld = useDown;
            if (!useDown && scoped) {
                close();
            }
        }
        boolean gun = heldGun() != null;

        if (pendingToggle) {
            pendingToggle = false;
            // Переключатель безусловный: повторный ПКМ обязан закрывать прицел.
            // Раньше здесь стояло `!player.isUsingItem()`, и при уже открытом
            // прицеле условие не выполнялось — выйти было нечем.
            if (scoped) {
                close();
            } else if (gun) {
                open(mc);
            }
        }

        // Пушка убрана из руки или открыт экран — прицел сам собой снимается.
        if (!gun || mc.screen != null) {
            if (scoped) close();
            applyMoveMultiplier(null);
            return;
        }

        if (!scoped) {
            applyMoveMultiplier(null);
            return;
        }

        // Держим FOV ровно: эффекты зума и биомов его двигают, а нам нужен
        // предсказуемый множитель. Обновляем не каждый тик, а раз в два.
        if (++ticks >= FOV_APPLY_INTERVAL) {
            ticks = 0;
            mc.options.fov().set((int) Math.round(savedFov / ZOOM_FACTOR));
        }

        applyMoveMultiplier(player);
    }

    private static void open(Minecraft mc) {
        if (scoped) return;
        scoped = true;
        savedFov = mc.options.fov().get();
        ticks = 0;
    }

    private static void close() {
        if (!scoped) return;
        scoped = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.fov().set(savedFov);
        }

        LocalPlayer player = mc.player;
        if (player != null) applyMoveMultiplier(player);
    }

    /**
     * Замедляет игрока вдвое через временный модификатор атрибута скорости.
     * <p>
     * Важно: множитель считается от {@code getBaseValue()}, то есть от голого
     * значения атрибута, а не от текущего. Иначе модификатор накапливался бы
     * тик за тиком и игрок замедлялся всё сильнее.
     */
    private static void applyMoveMultiplier(LocalPlayer player) {
        if (player == null) return;

        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;

        var existing = attribute.getModifier(MOVEMENT_MODIFIER_ID);

        if (!scoped) {
            if (existing != null) {
                attribute.removeModifier(existing);
            }
            return;
        }

        // Модификатор уже стоит — переустанавливать не нужно.
        if (existing != null) return;

        double base = attribute.getBaseValue();
        attribute.addTransientModifier(new AttributeModifier(
                MOVEMENT_MODIFIER_ID,
                base * (SCOPE_MOVE_MULTIPLIER - 1.0),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static MachineGunItem heldGun() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return null;
        ItemStack stack = mc.player.getMainHandItem();
        return stack.getItem() instanceof MachineGunItem gun ? gun : null;
    }

    /**
     * Рисуем оверлей и глушим интерфейс. Отменять событие целиком нельзя:
     * тогда пропадёт и сам оверлей, поэтому скрываем стандартные слои
     * (HUD, подсказки) и рисуем прицел поверх.
     */
    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (!scoped) return;

        var graphics = event.getGuiGraphics();

        // Рисуем оверлей здесь же, а не в Post: GuiLayerManager.render() при
        // отмене Pre делает return и до Post попросту не доходит, поэтому
        // рисование в Post не выполнялось никогда.
        drawScope(graphics, graphics.guiWidth(), graphics.guiHeight());

        // Отменяем слой целиком, чтобы под оверлеем не осталось хотбара,
        // полосок и подсказок: оверлей непрозрачен по краям и накрывает их.
        event.setCanceled(true);
    }

    /**
     * Оверлей рисуется так, чтобы круг прицела всегда попал в центр экрана и
     * занимал его целиком: сперва считаем сторону по большему габариту
     * (иначе по краям останутся дыры), затем рисуем с одинаковым смещением по
     * обеим осям. Сама текстура — квадрат, поэтому квадратный блок и есть
     * правильный выбор.
     */
    private static void drawScope(net.minecraft.client.gui.GuiGraphics graphics, int screenW, int screenH) {
        int size = Math.max(screenW, screenH);
        int x = (screenW - size) / 2;
        int y = (screenH - size) / 2;

        // Именно эта, 11-аргументная перегрузка. У 9-аргументной
        // (atlas, x, y, u, v, width, height, texW, texH) параметр width идёт
        // одновременно и размером на экране, и шириной участка в пикселях
        // текстуры, а UV считается как uWidth / textureWidth. Если туда отдать
        // size, UV уходит далеко за 1.0 и прицел рисуется сеткой поверхностей:
        // при 1024 это ровно 1024/256 = 4 повтора. Поэтому здесь явно
        // указываем и размер на экране (size), и участок текстуры (301).
        graphics.blit(SCOPE_TEXTURE, x, y, size, size,
                0.0f, 0.0f, SCOPE_TEX_SIZE, SCOPE_TEX_SIZE, SCOPE_TEX_SIZE, SCOPE_TEX_SIZE);
    }
}