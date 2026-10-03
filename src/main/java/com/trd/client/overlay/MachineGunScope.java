package com.trd.client.overlay;

import com.trd.client.config.ModKeyBindings;
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
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

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

    /**
     * Доля стороны текстуры, занятая прозрачным кругом: диаметр 199 px из 301.
     * Измерено по самой текстуре, а не взято на глаз.
     */
    private static final float CIRCLE_FRAC = 199.0f / 301.0f;

    /**
     * Доля наименьшей стороны экрана, которую занимает круг прицела.
     * <p>
     * Раньше диаметр брался равным {@code min(w, h)}, и круг упирался в верх
     * и низ экрана: чёрная оправа обрезалась краями кадра. Чуть меньший
     * диаметр оставляет видную рамку со всех сторон.
     */
    private static final float CIRCLE_SCREEN_FRAC = 0.94f;

    private static final double ZOOM_FACTOR = 4.0;

    private static boolean scoped;
    private static boolean pendingToggle;
    private static boolean useHeld;

    /**
     * ПКМ нажата прямо сейчас. Отслеживается по фронту нажатия, а не по событию:
     * {@code InteractionKeyMappingTriggered} срабатывает и на нажатии, и на
     * автоповторе удержания, и на отпускании не приходит вовсе. Одно нажатие
     * включает прицел, следующее — выключает, и держится он дальше сам.
     */
    private static final float SCOPE_MOVE_MULTIPLIER = 0.5f;

    /** id временного модификатора скорости, снимается при выходе из прицела. */
    private static final ResourceLocation MOVEMENT_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(MainRegistry.MOD_ID, "scope_slowdown");

    /**
     * Клавиши, нажатие которых сбрасывает прицел. ЛКМ (огонь) и ПКМ (сам
     * прицел) сюда не входят намеренно.
     * <p>
     * Клавиш движения — WASD, прыжка, приседания и бега — здесь тоже нет, и
     * это осознанно. Раньше они стояли в списке, и прицел слетал с первого же
     * шага: игрок не мог ни пройти, ни присесть, ни подпрыгнуть, не выключив
     * прицел, то есть в прицеле он был попросту неуправляем. Взвод и спуск
     * клавиши теперь прицел не трогают, а сбрасывают его только действия с
     * оружием и смены слота.
     * <p>
     * Список строится один раз и кэшируется: поля у {@code Options} final, и
     * пересобирать список каждый тик незачем.
     */
    private static KeyMapping[] cachedActionKeys;

    private static KeyMapping[] actionKeys(Minecraft mc) {
        KeyMapping[] cached = cachedActionKeys;
        if (cached == null) {
            cached = new KeyMapping[]{
                    ModKeyBindings.RELOAD_KEY,
                    ModKeyBindings.UNLOAD_KEY,
                    mc.options.keySwapOffhand,
                    mc.options.keyDrop
            };
            cachedActionKeys = cached;
        }
        return cached;
    }

    private MachineGunScope() {
    }

    public static boolean isScoped() {
        return scoped;
    }

    /**
     * Активен ли прицел прямо сейчас, то есть включён <em>и</em> камера от первого лица.
     * <p>
     * Зум и оверлей спрашивают именно это, а не просто флаг: игрок может
     * переключить камеру на F5 между тиками, и состояние снимется только в
     * следующем {@code ClientTickEvent.Post}. Без этой проверки на один кадр
     * оставалась виньетка прицела поверх вида от третьего лица.
     */
    private static boolean isScopeActive() {
        return scoped && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    public static float moveMultiplier() {
        return scoped ? SCOPE_MOVE_MULTIPLIER : 1.0f;
    }

    /**
     * Зум.
     * <p>
     * Раньше FOV менялся через {@code options.fov().set(...)}. Это не работало:
     * у настройки FOV в 1.21 стоит {@code IntRange(30, 110)}, а 70/4 = 17 за
     * границы не влезает, и {@code OptionInstance#set} молча откатывал
     * значение к исходному. Поэтому зум был нулевым даже при делении на два.
     * <p>
     * Теперь FOV не трогаем вовсе, а домножаем уже готовый итог в
     * {@code ComputeFov}: событие срабатывает после применения настроек, и
     * ограничение диапазона на него не распространяется.
     */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (isScopeActive()) {
            event.setFOV(event.getFOV() / ZOOM_FACTOR);
        }
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
        if (scoped && !event.isAttack()) {
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

        // Отпускание ПКМ больше не снимает прицел: он должен включаться и держаться
        // по одному нажатию. Фронт нажатия нужен только чтобы отсечь автоповтор
        // удержания, который иначе переключал бы прицел десятки раз в секунду.
        boolean useDown = mc.options.keyUse.isDown();
        if (useDown != useHeld) {
            useHeld = useDown;
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
                open();
            }
        }

        // Пушка убрана из руки или открыт экран — прицел сам собой снимается.
        if (!gun || mc.screen != null) {
            if (scoped) close();
            applyMoveMultiplier(null);
            return;
        }

        // Прицел — это вид из глаза: в третьем лице камера стоит за спиной,
        // оверлей с виньеткой наезжает на модель игрока, и прицел оказывается
        // перекошенным и бесполезным. Поэтому вход из третьего лица запрещён,
        // и уже открытый прицел от такого переключения снимается сам.
        if (!mc.options.getCameraType().isFirstPerson()) {
            if (scoped) close();
            applyMoveMultiplier(null);
            return;
        }

        if (!scoped) {
            applyMoveMultiplier(null);
            return;
        }

        // В прицеле его закрывает только действие с оружием и смена слота:
        // перезарядка, разрядка, выпадение предмета, смена руки. Ходьба,
        // прыжок и приседание прицел не сбрасывают, иначе в прицеле нельзя
        // было бы сделать ни шагу. Проверяем именно факт нажатия (фронт), а
        // не удержание — иначе прицел слетал бы с первого же кадра стрельбы,
        // где удерживается ЛКМ.
        if (actionKeyPressed(mc)) {
            close();
        }

        applyMoveMultiplier(player);
    }

    /**
     * Было ли в этом тике нажатие хотя бы одной из «действующих» клавиш.
     * <p>
     * Клавиши модов (R и G) сюда попадают напрямую: событие
     * {@code InteractionKeyMappingTriggered} в NeoForge срабатывает только на
     * мышиных кнопках — оно вызывается из {@code startAttack},
     * {@code continueAttack}, {@code startUseItem} и pick-block, и обычные
     * клавиатурные бинды в этот список не входят. Поэтому перезарядка и
     * разрядка раньше вообще не сбрасывали прицел.
     * <p>
     * Фронт нажатия отслеживаем сами, через {@code isDown()}, а не через
     * {@code consumeClick()}: тот же клик читает
     * {@link com.trd.item.weapons.guns.MachineGunItem} в своём тике, и
     * consumeClick() забрал бы его у перезарядки — нажатие R закрывало бы
     * прицел, но патроны бы не досылались.
     */
    private static boolean actionKeyPressed(Minecraft mc) {
        KeyMapping[] keys = actionKeys(mc);

        boolean[] wasDown = pressedState;
        if (wasDown == null || wasDown.length != keys.length) {
            wasDown = new boolean[keys.length];
            pressedState = wasDown;
        }

        boolean pressed = false;
        for (int i = 0; i < keys.length; i++) {
            boolean down = keys[i].isDown();
            // Фронт: зажатая клавиша не должна сбрасывать прицел каждый тик.
            if (down && !wasDown[i]) {
                pressed = true;
            }
            wasDown[i] = down;
        }

        return pressed;
    }

    /** Состояние «была ли клавиша зажата» на прошлом тике. */
    private static boolean[] pressedState;

    private static void open() {
        if (scoped) return;
        // Вход из третьего лица запрещён: см. комментарий в onClientTick.
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
        scoped = true;
        syncScopeState();
    }

    private static void close() {
        if (!scoped) return;
        scoped = false;
        syncScopeState();
    }

    /**
     * Сообщает серверу о смене состояния, чтобы замедление скорости
     * применялось и там, а не только на клиенте.
     */
    private static void syncScopeState() {
        PacketDistributor.sendToServer(new com.trd.network.packet.guns.PacketScopeState(scoped));
    }

    /**
     * Замедляет игрока вдвое через временный модификатор атрибута скорости.
     * <p>
     * Сумма модификатора обязана быть равна {@code множитель - 1}, то есть -0.5,
     * а не {@code база * (множитель - 1)}. У {@code ADD_MULTIPLIED_BASE} база
     * уже умножается на amount, поэтому вариант с умножением на базу давал
     * 0.1 + 0.1 * -0.05 = 0.095 вместо 0.1 — то есть замедление на 5% вместо
     * половины, которое на глаз не видно.
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

        attribute.addTransientModifier(new AttributeModifier(
                MOVEMENT_MODIFIER_ID,
                SCOPE_MOVE_MULTIPLIER - 1.0,
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
        if (!isScopeActive()) return;

        var graphics = event.getGuiGraphics();

        // Рисуем оверлей здесь же, а не в Post: GuiLayerManager.render() при
        // отмене Pre делает return и до Post попросту не доходит, поэтому
        // рисование в Post не выполнялось никогда.
        drawScope(graphics, graphics.guiWidth(), graphics.guiHeight());

        // Счётчик патронов поверх оверлея. Слоем он бы и не доехал: событие
        // ниже отменяется целиком, а на отмене GuiLayerManager выходит, не
        // выполнив ни одного слоя. Поэтому рисуем его руками последним — поверх
        // и виньетки, и всего остального.
        com.trd.client.overlay.hud.OverlayAmmoHud.render(graphics);

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
        // Диаметр круга задаём долей меньшей стороны экрана, а не всей стороной
        // целиком. При диаметре, равном min(w, h), круг касался верхнего и
        // нижнего краёв кадра, и чёрная оправа вокруг него срезалась краями
        // экрана — прицел выглядел обрезанным. Теперь вокруг круга всегда есть
        // видимая рамка.
        int diameter = (int) (Math.min(screenW, screenH) * CIRCLE_SCREEN_FRAC);

        // Сторона текстуры = диаметр / доля круга: чёрные углы текстуры при
        // этом уходят за пределы экрана — ровно то, что нужно.
        int size = (int) Math.ceil(diameter / CIRCLE_FRAC);

        int x = (screenW - size) / 2;
        int y = (screenH - size) / 2;

        // По бокам от квадрата текстуры остаётся свободная область. На 16:9 она
        // заметная, и сквозь неё был виден мир. Закрашиваем её чёрным: полосы
        // по краям экрана, не заходя внутрь круга.
        int black = 0xFF000000;
        if (screenW > diameter) {
            int side = (screenW - diameter) / 2;
            graphics.fill(0, 0, side, screenH, black);
            graphics.fill(screenW - side, 0, screenW, screenH, black);
        }
        if (screenH > diameter) {
            int side = (screenH - diameter) / 2;
            graphics.fill(0, 0, screenW, side, black);
            graphics.fill(0, screenH - side, screenW, screenH, black);
        }

        // Именно эта, 11-аргументная перегрузка. У 9-аргументной
        // (atlas, x, y, u, v, width, height, texW, texH) параметр width идёт
        // одновременно и размером на экране, и шириной участка в пикселях
        // текстуры, а UV считается как uWidth / textureWidth. Если туда отдать
        // size, UV уходит далеко за 1.0 и прицел рисуется сеткой повторов.
        //
        // Круг в текстуре прозрачный, поэтому заливка по краям его не задевает.
        graphics.blit(SCOPE_TEXTURE, x, y, size, size,
                0.0f, 0.0f, SCOPE_TEX_SIZE, SCOPE_TEX_SIZE, SCOPE_TEX_SIZE, SCOPE_TEX_SIZE);
    }
}
