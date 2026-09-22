package com.trd.client.render;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.trd.block.basic.CraterBasaltBlock;
import com.trd.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Клиентский реестр кратеров водородной гранаты. Позволяет чисто позиционно затемнять
 * ЛЮБЫЕ твёрдые блоки (в том числе ванильные и из других модов) в кольце у края воронки
 * БЕЗ замены блоков в мире: на мод-событии {@link ModelEvent.ModifyBakingResult} моделям
 * целевых блоков приклеивается тинт-индекс, а {@link #tintColor} в момент сборки чанка
 * вычисляет ступень затемнения по расстоянию до ближайшего кратера (максимум 50%,
 * симметрично вокруг края: и в чаше, и на внешнем ободе вокруг базальта).
 * Мир при этом не меняется: сломал/поставил блок — цвет исходный, лагов нет.
 */
@Mod.EventBusSubscriber(modid = MainRegistry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CraterTints {

    /** Максимальная порция темноты: 50% (канал цвета падает до ~127). */
    public static final float MAX_DARKNESS_RATIO = 0.50f;

    /**
     * Ванильные твёрдые блоки, которые темнеют у края воронки: породы камня и их
     * полированные/замшелые/треснувшие вариации, земли и почвы, пески/песчаники,
     * терракоты (включая мезовые), глина, базальт, глубинный сланец, призмарин.
     */
    public static final Block[] TINTED_BLOCKS = new Block[]{
            // Камень и производные
            Blocks.STONE,
            Blocks.COBBLESTONE,
            Blocks.MOSSY_COBBLESTONE,
            Blocks.STONE_BRICKS,
            Blocks.MOSSY_STONE_BRICKS,
            Blocks.CRACKED_STONE_BRICKS,
            Blocks.CHISELED_STONE_BRICKS,
            Blocks.SMOOTH_STONE,
            Blocks.ANDESITE,
            Blocks.POLISHED_ANDESITE,
            Blocks.DIORITE,
            Blocks.POLISHED_DIORITE,
            Blocks.GRANITE,
            Blocks.POLISHED_GRANITE,
            // Глубинный сланец
            Blocks.DEEPSLATE,
            Blocks.COBBLED_DEEPSLATE,
            Blocks.POLISHED_DEEPSLATE,
            Blocks.DEEPSLATE_BRICKS,
            Blocks.DEEPSLATE_TILES,
            Blocks.CRACKED_DEEPSLATE_TILES,
            // Туф и известняк пещер
            Blocks.TUFF,
            Blocks.CALCITE,
            Blocks.DRIPSTONE_BLOCK,
            // Базальт
            Blocks.BASALT,
            Blocks.POLISHED_BASALT,
            Blocks.SMOOTH_BASALT,
            // Земли и почвы
            Blocks.DIRT,
            Blocks.COARSE_DIRT,
            Blocks.ROOTED_DIRT,
            Blocks.PODZOL,
            Blocks.MYCELIUM,
            Blocks.MOSS_BLOCK,
            Blocks.SNOW_BLOCK,
            // Грязь (мангровые болота)
            Blocks.MUD,
            Blocks.PACKED_MUD,
            Blocks.MUD_BRICKS,
            // Пески и песчаники
            Blocks.SAND,
            Blocks.RED_SAND,
            Blocks.SANDSTONE,
            Blocks.SMOOTH_SANDSTONE,
            Blocks.CUT_SANDSTONE,
            Blocks.CHISELED_SANDSTONE,
            Blocks.RED_SANDSTONE,
            Blocks.SMOOTH_RED_SANDSTONE,
            Blocks.CUT_RED_SANDSTONE,
            Blocks.CHISELED_RED_SANDSTONE,
            // Глина и обсидиан
            Blocks.CLAY,
            Blocks.OBSIDIAN,
            // Терракоты (включая мезовые)
            Blocks.TERRACOTTA,
            Blocks.WHITE_TERRACOTTA,
            Blocks.ORANGE_TERRACOTTA,
            Blocks.MAGENTA_TERRACOTTA,
            Blocks.LIGHT_BLUE_TERRACOTTA,
            Blocks.YELLOW_TERRACOTTA,
            Blocks.LIME_TERRACOTTA,
            Blocks.PINK_TERRACOTTA,
            Blocks.GRAY_TERRACOTTA,
            Blocks.LIGHT_GRAY_TERRACOTTA,
            Blocks.CYAN_TERRACOTTA,
            Blocks.PURPLE_TERRACOTTA,
            Blocks.BLUE_TERRACOTTA,
            Blocks.BROWN_TERRACOTTA,
            Blocks.GREEN_TERRACOTTA,
            Blocks.RED_TERRACOTTA,
            Blocks.BLACK_TERRACOTTA,
            // Призмарин (океанские памятники)
            Blocks.PRISMARINE,
            Blocks.PRISMARINE_BRICKS,
            Blocks.DARK_PRISMARINE,
            // Гравий
            Blocks.GRAVEL
    };

    public record CraterInfo(ResourceLocation dimension, double x, double y, double z, float radius, float band) {
    }

    private static final List<CraterInfo> CRATERS = new CopyOnWriteArrayList<>();

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile ResourceLocation currentDimension = null;

    /** Сколько кратеров клиент держит в памяти: старые вытесняются, чтобы тинт не разросся. */
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
        LOGGER.info("CraterTints: registered crater ({}, {}, {}) dim={} radius={} band={}, total={}",
                String.format("%.1f", x), String.format("%.1f", y), String.format("%.1f", z),
                dim, radius, band, CRATERS.size());
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

    /**
     * Пересборка чанков в области кратера: клиентские чанки уже собраны, без этого
     * перекраска новых кратеров не появится. Одноразовое действие.
     */
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
            for (int bx = minX; bx <= maxX; bx += 16) {
                for (int by = minY; by <= maxY; by += 16) {
                    for (int bz = minZ; bz <= maxZ; bz += 16) {
                        lr.setSectionDirty(bx, by, bz);
                    }
                }
            }
        }
    }

    /**
     * Цвет тинта для позиции: белый (без изменений) вне воронок или в инвентаре.
     * Затемнение — симметричное кольцо вокруг края воронки {radius ± band}: и низ
     * чаши, и внешний обод вокруг базальта. Вызывается движком во время сборки чанка.
     */
    public static int tintColor(BlockGetter level, BlockPos pos) {
        if (level == null || pos == null) return 0xFFFFFFFF;
        ResourceLocation dim;
        if (level instanceof net.minecraft.world.level.Level l) {
            dim = l.dimension().location();
        } else {
            dim = currentDimension;
        }
        if (dim == null) return 0xFFFFFFFF;
        double px = pos.getX() + 0.5;
        double py = pos.getY() + 0.5;
        double pz = pos.getZ() + 0.5;
        int dark = 0;
        for (CraterInfo c : CRATERS) {
            if (!c.dimension.equals(dim)) continue;
            double br = c.radius + c.band;
            double ax = Math.abs(px - c.x);
            if (ax > br) continue;
            double ay = Math.abs(py - c.y);
            if (ay > br) continue;
            double az = Math.abs(pz - c.z);
            if (az > br) continue;
            double dx = px - c.x;
            double dy = py - c.y;
            double dz = pz - c.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > br) continue;
            double delta = Math.abs(dist - c.radius);
            if (delta > c.band) continue;
            double t = 1.0 - delta / c.band;
            dark = Math.max(dark, (int) Math.round(t * CraterBasaltBlock.MAX_DARK));
        }
        if (dark <= 0) return 0xFFFFFFFF;
        float f = 1.0f - MAX_DARKNESS_RATIO * (dark / (float) CraterBasaltBlock.MAX_DARK);
        int cc = (int) (255.0f * f);
        return 0xFF000000 | (cc << 16) | (cc << 8) | cc;
    }

    /**
     * Подменяет baked-модели целевых блоков на их копии с тинт-индексом во всех гранях,
     * чтобы {@link com.trd.event.ModColorHandlers} мог окрашивать их по позиции.
     * Сопоставление по точным ключам {@code stateToModelLocation}, с фолбэком по пути
     * ModelResourceLocation на случай отличий в строке варианта.
     */
    @SubscribeEvent
    public static void onModelBake(ModelEvent.ModifyBakingResult event) {
        Set<ResourceLocation> exact = new HashSet<>();
        Set<String> paths = new HashSet<>();
        for (Block b : TINTED_BLOCKS) {
            paths.add(BuiltInRegistries.BLOCK.getKey(b).getPath());
            for (BlockState st : b.getStateDefinition().getPossibleStates()) {
                exact.add(BlockModelShaper.stateToModelLocation(st));
            }
        }

        Map<ResourceLocation, BakedModel> models = event.getModels();
        List<ResourceLocation> toWrap = new ArrayList<>();
        for (ResourceLocation key : models.keySet()) {
            if (exact.contains(key)) {
                toWrap.add(key);
            } else if (key instanceof ModelResourceLocation mrl && paths.contains(mrl.getPath())) {
                toWrap.add(key);
            }
        }
        int wrapped = 0;
        for (ResourceLocation key : toWrap) {
            models.put(key, new TintableModel(models.get(key)));
            wrapped++;
        }
        LOGGER.info("CraterTints: wrapped {} baked models from {} entry models map", wrapped, models.size());
    }

    /** Обёртка модели: те же квады, но с tintIndex=0 во всех гранях. */
    private static final class TintableModel implements BakedModel {

        private final BakedModel base;

        TintableModel(BakedModel base) {
            this.base = base;
        }

        @Override
        public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(
                BlockState state, Direction side, RandomSource rand) {
            List<net.minecraft.client.renderer.block.model.BakedQuad> quads = base.getQuads(state, side, rand);
            if (quads.isEmpty()) return quads;
            List<net.minecraft.client.renderer.block.model.BakedQuad> out = new ArrayList<>(quads.size());
            for (net.minecraft.client.renderer.block.model.BakedQuad q : quads) {
                out.add(new net.minecraft.client.renderer.block.model.BakedQuad(
                        q.getVertices().clone(), 0, q.getDirection(), q.getSprite(),
                        q.isShade(), q.hasAmbientOcclusion()));
            }
            return out;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return base.useAmbientOcclusion();
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
        public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon() {
            return base.getParticleIcon();
        }

        @Override
        public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() {
            return base.getTransforms();
        }

        @Override
        public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides() {
            return base.getOverrides();
        }
    }

    private CraterTints() {
    }

    /** Forge-шина: запоминаем текущее измерение, чтобы не темнить блоки чужих миров. */
    @Mod.EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent
        public static void onLevelLoad(net.minecraftforge.event.level.LevelEvent.Load event) {
            if (event.getLevel() instanceof net.minecraft.world.level.Level level && level.isClientSide()) {
                currentDimension = level.dimension().location();
                loadCraters();
            }
        }
    }
}