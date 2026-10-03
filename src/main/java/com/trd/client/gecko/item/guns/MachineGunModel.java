package com.trd.client.gecko.item.guns;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.NodeTable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.BufferedReader;
import javax.annotation.Nullable;

/**
 * Локатор дула и матрица пушки в руке, прочитанные из самой glTF-модели.
 *
 * <h2>Зачем это в модели</h2>
 * Вспышка выстрела должна быть на дуле, а не там, где её насчитает формула от
 * позиции игрока: та не смотрит ни на поворот кисти, ни на покачивание камеры,
 * ни на отдачу ствола. {@link #worldMuzzle} берёт кость из модели, прогоняет
 * через неё текущий клип ({@link GltfPose}) и домножает на матрицу, которой
 * GemRender рисует предмет в руке. Дальше вспышка едет вместе со стволом.
 *
 * <h2>Где что лежит</h2>
 * Помеченные кадры — времена, локаторы и пути эффектов — живут не здесь, а в
 * геколибовском экспорте анимаций, см.
 * {@link com.trd.item.weapons.guns.MachineGunAnimation}: в glTF понятия «звук на
 * кадре» нет, и {@code extras} при экспорте из Blockbench ещё и вытирается.
 * Здесь осталось только то, что есть исключительно в glTF: смещение точки
 * вылета внутри кости и сама матрица предмета.
 *
 * <h2>Формат</h2>
 * <pre>{@code
 * "extras": { "trd_muzzle": { "bone": "barrel", "x": 0, "y": 0, "z": -0.06 } }
 * }</pre>
 * {@code bone} можно опустить, если кость с меткой и есть локатор.
 */
public final class MachineGunModel {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    /** Кость-локатор по умолчанию: ствол в текущей модели. */
    public static final String DEFAULT_MUZZLE_BONE = "barrel";

    /** Ключ в extras кости: смещение точки вылета внутри кости. */
    private static final String MUZZLE_KEY = "trd_muzzle";

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath("trd", "models/item/ap_17.gltf");

    /** Кость, на которой стоит точка вылета. */
    private static String muzzleBone = DEFAULT_MUZZLE_BONE;

    /** Смещение точки вылета внутри кости, в единицах модели. */
    private static Vector3f muzzleOffset = new Vector3f();

    /**
     * Матрица, которой GemRender рисует пушку в руке.
     * <p>
     * Снимается в {@code MachineGunGltfAppearance#transform}: он зовётся
     * последним перед {@code DirectRenderer.submit}, так что матрица внутри уже
     * содержит все повороты и сдвиги предметного контекста. Без неё кость из
     * модели живёт в системе координат модели, а не мира, и вспышка уезжает от
     * ствола.
     * <p>
     * Матрица снимается в рендере, а вспышка спавнится в тике, поэтому позиция
     * отстаёт на кадр-другой. Для вспышки в 1/8 блока это незаметно.
     */
    private static final Matrix4f ITEM_MATRIX = new Matrix4f();

    private static boolean parsed;

    private MachineGunModel() {
    }

    // === РАЗБОР МОДЕЛИ ===

    /** Кость, на которой стоит точка вылета, если её не задали иначе. */
    public static String muzzleBone() {
        parse();
        return muzzleBone;
    }

    /** Перечитывает модель при перезагрузке ресурсов. */
    public static synchronized void invalidate() {
        parsed = false;
    }

    private static synchronized void parse() {
        if (parsed) return;
        parsed = true;

        Muzzle marker = null;
        try {
            Resource resource = resourceOf();
            if (resource != null) {
                try (BufferedReader reader = resource.openAsReader()) {
                    marker = readMuzzle(JsonParser.parseReader(reader).getAsJsonObject());
                }
            }
        } catch (Exception e) {
            marker = null;
        }

        muzzleBone = marker == null ? DEFAULT_MUZZLE_BONE : marker.bone();
        muzzleOffset = marker == null ? new Vector3f() : marker.offset();
    }

    /** Что нашлось в extras: кость и смещение внутри неё. */
    private record Muzzle(String bone, Vector3f offset) {
    }

    private static Resource resourceOf() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) {
            return null;
        }
        return mc.getResourceManager().getResource(MODEL).orElse(null);
    }

    /**
     * Проходит по костям и достаёт {@code trd_muzzle}.
     * <p>
     * Метка может лежать на любой кости, поэтому ищем по всему списку, а не по
     * заранее известному имени.
     */
    private static Muzzle readMuzzle(JsonObject root) {
        if (!root.has("nodes") || !root.get("nodes").isJsonArray()) {
            return null;
        }

        for (JsonElement element : root.getAsJsonArray("nodes")) {
            if (!element.isJsonObject()) continue;
            JsonObject node = element.getAsJsonObject();
            if (!node.has("extras") || !node.get("extras").isJsonObject()) continue;

            JsonObject extras = node.getAsJsonObject("extras");
            if (!extras.has(MUZZLE_KEY) || !extras.get(MUZZLE_KEY).isJsonObject()) continue;

            JsonObject muzzle = extras.getAsJsonObject(MUZZLE_KEY);

            String bone;
            if (muzzle.has("bone") && muzzle.get("bone").isJsonPrimitive()) {
                bone = muzzle.get("bone").getAsString();
            } else if (node.has("name") && node.get("name").isJsonPrimitive()) {
                // Кость с меткой и есть локатор — отдельный bone не нужен.
                bone = node.get("name").getAsString();
            } else {
                bone = DEFAULT_MUZZLE_BONE;
            }

            return new Muzzle(bone, new Vector3f(
                    readFloat(muzzle, "x"),
                    readFloat(muzzle, "y"),
                    readFloat(muzzle, "z")));
        }

        return null;
    }

    private static float readFloat(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return 0.0F;
        }
        try {
            return object.get(key).getAsFloat();
        } catch (Exception e) {
            return 0.0F;
        }
    }

    // === ТОЧКА ВЫЛЕТА ===

    /**
     * Запоминает матрицу, которой сейчас рисуется пушка.
     * <p>
     * Зовётся из {@code ItemAppearance#transform}: к этому моменту внутри
     * {@code PoseStack} уже лежит полная матрица «модель → мир» — со всеми
     * смещениями предметного контекста.
     * <p>
     * Кэшируются только контексты, в которых оружие реально находится в руках.
     * Модель за кадр рисуется ещё и в инвентаре, на стойке и в голове, и
     * матрица оттуда увела бы локатор в сторону от камеры.
     */
    public static void captureItemMatrix(net.minecraft.world.item.ItemDisplayContext context,
                                         Matrix4f modelToWorld) {
        if (!isHandContext(context)) {
            return;
        }
        ITEM_MATRIX.set(modelToWorld);
        itemMatrixSeen = true;
    }

    /** Рука в первом или третьем лице — только там оружие видимо как оружие. */
    private static boolean isHandContext(net.minecraft.world.item.ItemDisplayContext context) {
        return context == net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    /** Была ли пушка в руках хоть раз: без этого матрица ещё единичная. */
    public static boolean hasItemMatrix() {
        return itemMatrixSeen;
    }

    private static boolean itemMatrixSeen;

    /**
     * Где в мире сейчас кончик ствола.
     * <p>
     * Считается в три шага: кость прогоняется через текущий клип, её матрица
     * домножается на точку внутри кости, а результат — на матрицу пушки в руке.
     * Всё это учитывает и отдачу ствола, и поворот кисти, и покачивание камеры.
     * <p>
     * Клип может быть {@code null}, и это нормальный случай, а не поломка:
     * {@link GltfPose#evaluate} сам пропускает анимацию и собирает позу покоя.
     * Анимация играет долю секунды, а выстрел клиент шлёт каждый тик, поэтому
     * точки вылета без этого не существовало бы в паузах между клипами, и
     * пушка стреляла бы из серверной формулы.
     *
     * @param locator кость из блокбенчовского локатора; если такой кости в модели
     *                нет, берётся {@link #DEFAULT_MUZZLE_BONE}
     * @return мировая точка дула либо {@code null}, если кость не нашлась
     */
    public static Vec3 worldMuzzle(GemRenderGltfModel model, @Nullable GltfAnimation clip, float seconds,
                                   @Nullable String locator, Camera camera) {
        if (model == null || camera == null || !itemMatrixSeen) return null;

        try {
            return computeMuzzle(model, clip, seconds, locator, camera);
        } catch (Exception e) {
            // Ошибка здесь не должна ронять клиентский тик: вспышка и точка
            // вылета — украшение, а не условие выстрела. Молча уходим на
            // серверную формулу.
            if (!warned) {
                warned = true;
                LOGGER.warn("Failed to place the machine gun muzzle from the model; "
                        + "falling back to the server-side formula", e);
            }
            return null;
        }
    }

    private static boolean warned;

    @Nullable
    private static Vec3 computeMuzzle(GemRenderGltfModel model, GltfAnimation clip, float seconds,
                                      @Nullable String locator, Camera camera) {
        int slot = slotOf(model, locator);
        if (slot < 0) {
            return null;
        }

        // Палитра заполняется матрицами нод в системе координат модели: compose
        // проходит по evaluationOrder() и домножает родителей, то есть на выходе
        // мировая матрица ноды, а не её локальная.
        //
        // Элементы массива обязаны существовать. compose пишет в них через
        // NodeTable#localTransform(node, out), и в массиве из null он падает на
        // первой же кости с NullPointerException — а worldMuzzle глотает
        // исключение и отдаёт null. Так локатор дула не работал никогда: точка
        // вылета молча уезжала на серверную формулу.
        //
        // Клип может быть null — это не поломка, а поза покоя, и evaluate её
        // обрабатывает сам (просто не применяет анимацию). Покой нужен не
        // реже, чем клип: анимация выстрела живёт доли секунды, а выстрел
        // клиент шлёт каждый тик, и в паузах между клипами точка вылета была бы
        // недоступна. Именно из-за этого пушка при вертикальном выстреле
        // вылетала из блока, в котором стоит игрок.
        //
        // Размер — именно layout().size(), а НЕ model.newPalette(): последний
        // sized по jointCount(), то есть по числу суставов скина. У пушки с
        // четырьмя суставами и пятью костями он на кость короче, и GltfPose при
        // записи в палитру уходил за её конец.
        Matrix4f[] palette = new Matrix4f[model.layout().size()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = new Matrix4f();
        }
        GltfPose.evaluate(model.layout(), clip, seconds, palette);

        if (slot >= palette.length) {
            return null;
        }

        Vector4f modelSpace = new Vector4f(muzzleOffset.x(), muzzleOffset.y(), muzzleOffset.z(), 1.0F);
        modelSpace.mul(palette[slot]);

        Vector4f world = modelSpace.mul(ITEM_MATRIX);
        if (world.w() == 0.0F) {
            return null;
        }

        // Матрица предметного рендера уже повёрнута камерой, но не сдвинута на
        // её позицию: координаты в ней камеро-относительные.
        return new Vec3(
                world.x() / world.w() + camera.getPosition().x,
                world.y() / world.w() + camera.getPosition().y,
                world.z() / world.w() + camera.getPosition().z
        );
    }

    /**
     * Ищет кость локатора: сперва имя из блокбенчовского локатора, потом
     * заданное в {@code trd_muzzle}, потом ствол по умолчанию.
     * <p>
     * Локатор в Blockbench — пустая кость, и в glTF она попадает не всегда.
     * Без цепочки вспышка просто не появилась бы там, где локатор не доехал.
     */
    private static int slotOf(GemRenderGltfModel model, @Nullable String locator) {
        NodeTable table = model.layout().nodeTable();
        int size = model.layout().size();

        if (locator != null && !locator.isEmpty()) {
            int slot = table.slotOfName(locator);
            if (slot >= 0 && slot < size) return slot;
        }

        int slot = table.slotOfName(muzzleBone());
        return slot >= 0 && slot < size ? slot : -1;
    }
}
