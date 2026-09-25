package com.trd.client.render;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.trd.block.basic.CraterBasaltBlock;
import com.trd.main.MainRegistry;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Клиентский тинт воронки водородной гранаты.
 *
 * <p>Тинт выдан на сервере позиционно (см. {@code ExplosionHydrogen}): для каждого твёрдого блока,
 * существовавшего на момент взрыва, сервер хранит ступень затемнения 0..7 (линейно от эпицентра
 * до края второй зоны поражения) и шлёт клиенту {@code SyncCraterTintsPacket}. Клиент держит карту
 * «позиция → затемнение» на текущее измерение и окрашивает блоки при сборке чанка цветовым
 * хендлером. Сломал/поставил блок — сервер удаляет запись и шлёт удаление: тинт пропадает.
 *
 * <p>Чтобы красить любые твёрдые блоки (не только ванильные и не только своего мода), на
 * {@link ModelEvent.ModifyBakingResult} ВСЕ блоки без собственных цветовых тинтов оборачиваются
 * в {@link TintableModel} (в клиджах проставляется tintIndex), а для них регистрируется один
 * позиционный {@link BlockColors}-хендлер. Блоки с уже существующим тинтом (трава, листва,
 * мягкий базальт/выжженная земля с свойством DARKNESS) не трогаются и красятся штатно.
 * Блоки с кастомным рендерером ({@code isCustomRenderer}) не оборачиваются: они всё равно
 * рисуются через {@code BlockEntityRenderer} и квадов не отдают.
 */
@Mod.EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CraterTints {

    /** Максимальная порция темноты: 50% (канал цвета падает до ~127). */
    public static final float MAX_DARKNESS_RATIO = 0.50f;

    /** Позиционная база затемнения по измерениям: позиция → ступень 0..MAX_DARK. */
    private static final ConcurrentHashMap<ResourceLocation, Long2IntOpenHashMap> TINT_MAP = new ConcurrentHashMap<>();

    public record CraterInfo(ResourceLocation dimension, double x, double y, double z, float radius, float band) {
    }

    private static final List<CraterInfo> CRATERS = new CopyOnWriteArrayList<>();

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile ResourceLocation currentDimension = null;

    private static final int MAX_CRATERS = 512;

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("trd_craters.json");
    private static final Gson GSON = new Gson();

    public static void addCrater(double x, double y, double z, float radius, float band) {
        var level = Minecraft.getInstance().level;
        if (level != null) currentDimension = level.dimension().location();
        ResourceLocation dim = currentDimension;
        if (dim == null) dim = new ResourceLocation("minecraft", "overworld");
        for (CraterInfo c : CRATERS) {
            if (c.dimension().equals(dim) && c.x() == x && c.y() == y && c.z() == z) return;
        }
        if (CRATERS.size() >= MAX_CRATERS) CRATERS.remove(0);
        CRATERS.add(new CraterInfo(dim, x, y, z, radius, band));
        saveCraters();
    }

    private static void saveCraters() {
        try {
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (CraterInfo c : CRATERS) {
                JsonObject o = new JsonObject();
                o.addProperty("dim", c.dimension().toString());
                o.addProperty("x", c.x());
                o.addProperty("y", c.y());
                o.addProperty("z", c.z());
                o.addProperty("radius", c.radius());
                o.addProperty("band", c.band());
                arr.add(o);
            }
            root.add("craters", arr);
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void loadCraters() {
        CRATERS.clear();
        if (!Files.exists(FILE)) return;
        try {
            JsonElement el = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonElement.class);
            JsonObject root = el.getAsJsonObject();
            JsonArray arr = root.has("craters") ? root.getAsJsonArray("craters") : new JsonArray();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                ResourceLocation dim = ResourceLocation.tryParse(o.get("dim").getAsString());
                if (dim == null) continue;
                CraterInfo c = new CraterInfo(dim,
                        o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(),
                        o.get("radius").getAsFloat(), o.get("band").getAsFloat());
                if (CRATERS.size() < MAX_CRATERS) CRATERS.add(c);
            }
        } catch (Exception ignored) {
        }
    }

    /** Пересборка чанков вокруг воронки (одноразовое действие сразу после взрыва). */
    public static void refreshSections(net.minecraft.client.multiplayer.ClientLevel level) {
        LevelRenderer lr = Minecraft.getInstance().levelRenderer;
        ResourceLocation dim = level.dimension().location();
        for (CraterInfo c : CRATERS) {
            if (!c.dimension().equals(dim)) continue;
            int r = (int) Math.ceil(c.radius() + c.band() + 2);
            int minX = (int) Math.floor(c.x()) - r;
            int maxX = (int) Math.floor(c.x()) + r;
            int minY = Math.max(level.getMinBuildHeight(), (int) Math.floor(c.y()) - r);
            int maxY = Math.min(level.getMaxBuildHeight() - 1, (int) Math.floor(c.y()) + r);
            int minZ = (int) Math.floor(c.z()) - r;
            int maxZ = (int) Math.floor(c.z()) + r;
            markSectionsDirty(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    /** Приём добавлений затемнения от сервера. Помечает затронутые секции на пересборку. */
    public static void addTints(ResourceLocation dim, long[] adds, int[] dark) {
        if (adds == null || adds.length == 0) return;
        Long2IntOpenHashMap map = TINT_MAP.computeIfAbsent(dim, d -> new Long2IntOpenHashMap());
        for (int i = 0; i < adds.length; i++) {
            long p = adds[i];
            if (dark[i] > 0) map.put(p, dark[i]);
            else map.remove(p);
        }
        markSectionsDirty(adds);
    }

    /** Приём удалений затемнения от сервера (блок сломали/поставили). */
    public static void removeTints(ResourceLocation dim, long[] removes) {
        if (removes == null || removes.length == 0) return;
        Long2IntOpenHashMap map = TINT_MAP.get(dim);
        if (map != null) {
            for (long p : removes) map.remove(p);
        }
        markSectionsDirty(removes);
    }

    /** Пометить секции, покрывающие указанные позиции, как «грязные» — пересборка происходит плавно, кадр за кадром. */
    private static void markSectionsDirty(long[] positions) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (long p : positions) {
            int x = BlockPos.getX(p);
            int y = BlockPos.getY(p);
            int z = BlockPos.getZ(p);
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }
        markSectionsDirty(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Пометить грязными все секции, перекрывающие прямоугольник блоков
     * {@code (minX..maxX, minY..maxY, minZ..maxZ)}.
     *
     * <p><b>Важно:</b> {@code LevelRenderer.setSectionDirty(int, int, int)} в 1.20.1 принимает
     * <b>координаты секции</b>, а не блока — внутри идёт
     * {@code Math.floorMod(y - level.getMinSection(), chunkGridSize)}. Публичного оверлоада с
     * конвертацией нет, её делают только внутренние вызовы {@code setBlocksDirty}. Поэтому блочные
     * координаты конвертируются явно через {@link SectionPos#blockToSectionCoord(int)}; иначе
     * помечаются секции в 16 раз дальше по оси и пересборка не затрагивает саму воронку —
     * тинт не появляется до перезахода в мир.
     */
    private static void markSectionsDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        LevelRenderer lr = mc.levelRenderer;
        int sx0 = SectionPos.blockToSectionCoord(minX);
        int sx1 = SectionPos.blockToSectionCoord(maxX);
        int sy0 = SectionPos.blockToSectionCoord(minY);
        int sy1 = SectionPos.blockToSectionCoord(maxY);
        int sz0 = SectionPos.blockToSectionCoord(minZ);
        int sz1 = SectionPos.blockToSectionCoord(maxZ);
        for (int sx = sx0; sx <= sx1; ++sx) {
            for (int sy = sy0; sy <= sy1; ++sy) {
                for (int sz = sz0; sz <= sz1; ++sz) {
                    lr.setSectionDirty(sx, sy, sz);
                }
            }
        }
    }

    /**
     * Цвет тинта для позиции: белый (без изменений), если запись отсутствует.
     * Кратерные блоки с собственным свойством DARKNESS красятся им, все остальные —
     * позиционной базой. Вызывается движком ВО ВРЕМЯ СБОРКИ чанка, не каждый кадр.
     */
    public static int tintColor(BlockState state, BlockGetter level, BlockPos pos) {
        if (level == null || pos == null || state == null) return 0xFFFFFFFF;
        int dark;
        if (state.hasProperty(CraterBasaltBlock.DARKNESS)) {
            dark = state.getValue(CraterBasaltBlock.DARKNESS);
        } else {
            dark = tintLevel(level, pos);
        }
        if (dark <= 0) return 0xFFFFFFFF;
        float f = 1.0f - MAX_DARKNESS_RATIO * (dark / (float) CraterBasaltBlock.MAX_DARK);
        int c = (int) (255.0f * f);
        return 0xFF000000 | (c << 16) | (c << 8) | c;
    }

    /**
     * Ступень затемнения (0..MAX_DARK) позиции в текущем измерении: 0, если записи нет.
     * Общая точка доступа для {@link #tintColor} и {@link #darknessMultiplier}.
     */
    public static int tintLevel(BlockGetter level, BlockPos pos) {
        if (level == null || pos == null) return 0;
        ResourceLocation dim = level instanceof net.minecraft.world.level.Level l
                ? l.dimension().location() : currentDimension;
        if (dim == null) return 0;
        Long2IntOpenHashMap map = TINT_MAP.get(dim);
        return map == null ? 0 : map.get(pos.asLong());
    }

    /**
     * Множитель яркости (0..1) для позиции: 1.0 вне базы, иначе ослабление
     * по ступени. Переиспользуется обработчиками цвета блоков с собственными тинтами
     * (дёрн, трава, листва), чтобы затемнять их, не теряя биомный цвет.
     */
    public static float darknessMultiplier(BlockGetter level, BlockPos pos) {
        if (level == null || pos == null) return 1.0f;
        int dark = tintLevel(level, pos);
        if (dark <= 0) return 1.0f;
        return 1.0f - MAX_DARKNESS_RATIO * (dark / (float) CraterBasaltBlock.MAX_DARK);
    }

    /**
     * Оборачивает baked-модели всех блоков, у которых НЕТ собственных цветовых тинтов,
     * в {@link TintableModel} (в квадах проставляется tintIndex), и регистрирует единый
     * позиционный цветовой хендлер. Блоки с существующим тинтом (листва, трава, мягкий
     * базальт, выжженная земля) не трогаются — они красятся своими штатными хендлерами.
     */
    @SubscribeEvent
    public static void onModelBake(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        RandomSource rand = RandomSource.create();
        List<Block> tintableBlocks = new ArrayList<>();
        int wrappedStates = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.getStateDefinition().getPossibleStates().isEmpty()) continue;

            boolean existsTint = false;
            for (BlockState st : block.getStateDefinition().getPossibleStates()) {
                BakedModel m = models.get(BlockModelShaper.stateToModelLocation(st));
                if (m != null && hasTintQuads(m, st, rand)) {
                    existsTint = true;
                    break;
                }
            }
            if (existsTint) continue;

            boolean any = false;
            for (BlockState st : block.getStateDefinition().getPossibleStates()) {
                ResourceLocation key = BlockModelShaper.stateToModelLocation(st);
                BakedModel m = models.get(key);
                // Модель могла достаться в обёртку от соседнего блока с тем же model location —
                // блок всё равно считаем тинтируемым, иначе для него не будет BlockColors-хендлера.
                if (m instanceof TintableModel) {
                    any = true;
                    continue;
                }
                // Блоки с кастомным рендерером (BlockEntityRenderer) квадов не отдают — не трогаем.
                if (m == null || m.isCustomRenderer()) continue;
                models.put(key, new TintableModel(m));
                any = true;
            }
            if (any) {
                tintableBlocks.add(block);
                wrappedStates += block.getStateDefinition().getPossibleStates().size();
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (!tintableBlocks.isEmpty() && mc != null && mc.getBlockColors() != null) {
            BlockColors colors = mc.getBlockColors();
            colors.register((state, getter, bp, tintIndex) -> tintColor(state, getter, bp),
                    tintableBlocks.toArray(new Block[0]));
        }
        LOGGER.info("CraterTints: tint-wrapped {} block(s), {} state model(s), handler registered for {} block(s)",
                tintableBlocks.size(), wrappedStates, tintableBlocks.size());
    }

    /** Есть ли у модели хоть одна грань с собственным tintindex (такие блоки не трогаем). */
    private static boolean hasTintQuads(BakedModel model, BlockState state, RandomSource rand) {
        // 5-арг overload: у кастомных моделей геометрия может зависеть от ModelData/RenderType.
        for (Direction side : Direction.values()) {
            for (BakedQuad q : model.getQuads(state, side, rand, ModelData.EMPTY, null)) {
                if (q.getTintIndex() >= 0) return true;
            }
        }
        for (BakedQuad q : model.getQuads(state, null, rand, ModelData.EMPTY, null)) {
            if (q.getTintIndex() >= 0) return true;
        }
        return false;
    }

    /**
     * Обёртка модели: те же квады, но с tintIndex=0 во всех гранях без собственного тинта.
     *
     * <p><b>Почему проксирование обязательно.</b> {@link BakedModel} расширяет
     * {@link net.minecraftforge.client.extensions.IForgeBakedModel}, и все его точки расширения
     * по умолчанию возвращают {@code self()} — то есть <b>обходят обёртку</b>. Если их не
     * переопределить, у чужой модели теряются её {@link ModelData}, рендер-проходы, наборы
     * {@link RenderType} и трансформации предмета: она рисуется не своей геометрией. В
     * частности модель, чьи квады зависят от {@code ModelData}/{@code RenderType}, отдаёт через
     * устаревший 3-арг {@code getQuads} заглушку, и прозрачные texel'ы заливаются геометрией
     * «запасного» варианта — это и ломало альфу у блоков сторонних модов.
     *
     * <p>Цвет самих вершин не трогаем: тинт в Forge/vanilla идёт только в RGB
     * ({@code ModelBlockRenderer.putQuadData}), alpha берётся из байта 15 цвета вершины.
     */
    private static final class TintableModel implements BakedModel {

        private static final Object NULL_RENDER_TYPE = new Object();

        private final BakedModel base;

        /** Кеш тинтированных квадов: renderType → (blockId, side) → квады. */
        private final ConcurrentHashMap<Object, ConcurrentHashMap<Long, List<BakedQuad>>> cache =
                new ConcurrentHashMap<>();

        TintableModel(BakedModel base) {
            this.base = base;
        }

        private static boolean isWrapped(BakedModel model) {
            return model instanceof TintableModel;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
            return getQuads(state, side, rand, ModelData.EMPTY, null);
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand,
                                        ModelData data, RenderType renderType) {
            List<BakedQuad> quads = base.getQuads(state, side, rand, data, renderType);
            if (quads.isEmpty() || state == null || data != ModelData.EMPTY) {
                // Чужая ModelData может менять геометрию — кешировать по (block, side) нельзя.
                return tinted(quads);
            }
            ConcurrentHashMap<Long, List<BakedQuad>> bucket = cache.computeIfAbsent(
                    renderType == null ? NULL_RENDER_TYPE : renderType,
                    k -> new ConcurrentHashMap<>());
            long key = (side == null ? 6L : side.ordinal())
                    | ((long) BuiltInRegistries.BLOCK.getId(state.getBlock()) << 4);
            return bucket.computeIfAbsent(key, k -> tinted(quads));
        }

        /** Возвращает тот же список, если тинтировать нечего, иначе копию с tintIndex=0. */
        private static List<BakedQuad> tinted(List<BakedQuad> quads) {
            List<BakedQuad> out = null;
            for (int i = 0; i < quads.size(); i++) {
                BakedQuad q = quads.get(i);
                if (q.getTintIndex() >= 0) continue;
                if (out == null) out = new ArrayList<>(quads);
                out.set(i, new BakedQuad(q.getVertices().clone(), 0,
                        q.getDirection(), q.getSprite(), q.isShade(), q.hasAmbientOcclusion()));
            }
            return out == null ? quads : out;
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
            return base.getModelData(level, pos, state, modelData);
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
            return base.getRenderTypes(state, rand, data);
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack itemStack, boolean fabulous) {
            return base.getRenderTypes(itemStack, fabulous);
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack itemStack, boolean fabulous) {
            List<BakedModel> passes = base.getRenderPasses(itemStack, fabulous);
            List<BakedModel> out = null;
            for (int i = 0; i < passes.size(); i++) {
                BakedModel pass = passes.get(i);
                if (pass == null || isWrapped(pass)) continue;
                if (out == null) out = new ArrayList<>(passes);
                out.set(i, new TintableModel(pass));
            }
            return out == null ? passes : out;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext transformType, PoseStack poseStack, boolean applyLeftHandTransform) {
            BakedModel result = base.applyTransform(transformType, poseStack, applyLeftHandTransform);
            if (result == base) return this;
            return isWrapped(result) ? result : new TintableModel(result);
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            return base.getParticleIcon(data);
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return base.getParticleIcon();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return base.useAmbientOcclusion();
        }

        @Override
        public boolean useAmbientOcclusion(BlockState state) {
            return base.useAmbientOcclusion(state);
        }

        @Override
        public boolean useAmbientOcclusion(BlockState state, RenderType renderType) {
            return base.useAmbientOcclusion(state, renderType);
        }

        @Override
        public boolean isGui3d() {
            return base.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return base.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return base.isCustomRenderer();
        }

        @Override
        public ItemTransforms getTransforms() {
            return base.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return base.getOverrides();
        }
    }

    private CraterTints() {
    }

    /** Forge-шина: при загрузке мира сбрасываем позиционную базу и запоминаем измерение. */
    @Mod.EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent
        public static void onLevelLoad(net.minecraftforge.event.level.LevelEvent.Load event) {
            if (event.getLevel() instanceof net.minecraft.world.level.Level level && level.isClientSide()) {
                currentDimension = level.dimension().location();
                loadCraters();
                TINT_MAP.clear();
            }
        }
    }
}