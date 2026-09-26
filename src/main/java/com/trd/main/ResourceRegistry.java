package com.trd.main;

import com.trd.block.basic.ModBlocks;
import com.trd.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ResourceRegistry {

    private static final List<ResourceEntry> RESOURCES = new ArrayList<>();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;

        RESOURCES.clear();

        registerFull("aluminum", ResourceType.METAL,
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(6.0f, 20.0f).requiresCorrectToolForDrops());
        registerFull("bronze", ResourceType.METAL,
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(16.0f, 28.0f).requiresCorrectToolForDrops());
        registerFull("zinc", ResourceType.METAL,
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(10.0f, 23.0f).requiresCorrectToolForDrops());
        registerFull("tin", ResourceType.METAL,
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(6.0f, 10.0f).requiresCorrectToolForDrops());
        registerFull("titanium", ResourceType.METAL,
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(12.0f, 100.0f).requiresCorrectToolForDrops());
        registerFull("beryllium", ResourceType.METAL,
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(19.0f, 30.0f).requiresCorrectToolForDrops());
        registerFull("lead", ResourceType.METAL,
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(45.0f, 150.0f).requiresCorrectToolForDrops());
        registerFull("industrial_copper", ResourceType.METAL,
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(4.0f, 15.0f).requiresCorrectToolForDrops());
        registerFull("steel", ResourceType.METAL,
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(15.0f, 80.0f).requiresCorrectToolForDrops());
        registerFull("tungsten", ResourceType.METAL,
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(50.0f, 230.0f).requiresCorrectToolForDrops());
        registerFull("neodymium", ResourceType.METAL,
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(8.0f, 20.0f).requiresCorrectToolForDrops());

        initialized = true;
        MainRegistry.LOGGER.info("ResourceRegistry initialized with {} resources", RESOURCES.size());
    }

    public enum ResourceType {
        METAL("ingot", "nugget", "block"),
        POLYMER("granule", "piece", "block"),
        CRYSTAL("crystal", "shard", "block"),
        GEM("gem", "fragment", "block"),
        MINERAL("lump", "dust", "block");

        public final String mainUnit;
        public final String smallUnit;
        public final String blockName;

        ResourceType(String mainUnit, String smallUnit, String blockName) {
            this.mainUnit = mainUnit;
            this.smallUnit = smallUnit;
            this.blockName = blockName;
        }
    }

    public static class ResourceConfig {
        private final String name;
        private final ResourceType type;
        private final boolean hasSmallUnit;
        private final boolean hasBlock;
        private final BlockBehaviour.Properties blockProperties;

        public ResourceConfig(String name, ResourceType type, boolean hasSmallUnit, boolean hasBlock,
                              BlockBehaviour.Properties blockProperties) {
            this.name = name;
            this.type = type;
            this.hasSmallUnit = hasSmallUnit;
            this.hasBlock = hasBlock;
            this.blockProperties = blockProperties;
        }
    }

    public static class ResourceEntry {
        public final String name;
        public final ResourceType type;
        public final DeferredItem<Item> mainUnit;
        public final DeferredItem<Item> smallUnit;
        public final DeferredBlock<Block> block;
        public final DeferredItem<BlockItem> blockItem;

        public ResourceEntry(String name, ResourceType type,
                             DeferredItem<Item> mainUnit,
                             DeferredItem<Item> smallUnit,
                             DeferredBlock<Block> block,
                             DeferredItem<BlockItem> blockItem) {
            this.name = name;
            this.type = type;
            this.mainUnit = mainUnit;
            this.smallUnit = smallUnit;
            this.block = block;
            this.blockItem = blockItem;
        }

        public boolean hasSmallUnit() { return smallUnit != null; }
        public boolean hasBlock() { return block != null; }
        public String getMainUnitId() { return mainUnit.getId().getPath(); }
        public String getSmallUnitId() { return smallUnit != null ? smallUnit.getId().getPath() : null; }
        public String getBlockId() { return block != null ? block.getId().getPath() : null; }
    }

    public static ResourceEntry register(ResourceConfig config) {
        String name = config.name;
        ResourceType type = config.type;

        String mainUnitName = name + "_" + type.mainUnit;
        DeferredItem<Item> mainUnit = ModItems.ITEMS.register(mainUnitName,
                () -> new Item(new Item.Properties()));

        DeferredItem<Item> smallUnit = null;
        if (config.hasSmallUnit) {
            String smallUnitName = name + "_" + type.smallUnit;
            smallUnit = ModItems.ITEMS.register(smallUnitName,
                    () -> new Item(new Item.Properties()));
        }

        DeferredBlock<Block> block = null;
        DeferredItem<BlockItem> blockItem = null;
        if (config.hasBlock) {
            String blockName = name + "_" + type.blockName;
            block = ModBlocks.BLOCKS.register(blockName,
                    () -> new Block(config.blockProperties));
            final DeferredBlock<Block> blockRef = block;
            blockItem = ModItems.ITEMS.register(blockName,
                    () -> new BlockItem(blockRef.get(), new Item.Properties()));
        }

        ResourceEntry entry = new ResourceEntry(name, type, mainUnit, smallUnit, block, blockItem);
        RESOURCES.add(entry);
        return entry;
    }

    public static ResourceEntry registerFull(String name, ResourceType type, BlockBehaviour.Properties properties) {
        return register(new ResourceConfig(name, type, true, true, properties));
    }

    public static Optional<ResourceEntry> get(String name) {
        for (ResourceEntry resource : RESOURCES) {
            if (resource.name.equals(name)) {
                return Optional.of(resource);
            }
        }
        return Optional.empty();
    }

    public static Item getMainUnit(String name) {
        return get(name).map(r -> r.mainUnit.get()).orElse(null);
    }

    public static Item getSmallUnit(String name) {
        return get(name).filter(ResourceEntry::hasSmallUnit).map(r -> r.smallUnit.get()).orElse(null);
    }

    public static Block getBlock(String name) {
        return get(name).filter(ResourceEntry::hasBlock).map(r -> r.block.get()).orElse(null);
    }

    public static List<ResourceEntry> getAll() {
        return RESOURCES;
    }
}
