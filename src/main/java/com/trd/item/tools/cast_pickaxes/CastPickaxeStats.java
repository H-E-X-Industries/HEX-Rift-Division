package com.trd.item.tools.cast_pickaxes;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class CastPickaxeStats {
    private final Tier tier;
    private final int chargeTicks;
    private final float maxDamage;
    private final float reach;
    private final float maxHardnessMultiplier;
    private final int veinMinerLimit;
    private final float veinMinerDurabilityCost;
    private final Predicate<BlockState> veinMinerPredicate;
    private final int veinMinerRange;
    private final int tunnelLength;
    private final float tunnelDecay;
    private final List<PerkTooltip> perks;

    public static class PerkTooltip {
        public final String translationKey;
        public final Object[] args;
        public final int color;

        public PerkTooltip(String translationKey, Object[] args, int color) {
            this.translationKey = translationKey;
            this.args = args;
            this.color = color;
        }

        public PerkTooltip(String translationKey, int color) {
            this(translationKey, new Object[0], color);
        }
    }

    public CastPickaxeStats(Tier tier, int chargeTicks, float maxDamage, float reach,
                            float maxHardnessMultiplier, int veinMinerLimit,
                            float veinMinerDurabilityCost, Predicate<BlockState> veinMinerPredicate,
                            int veinMinerRange, int tunnelLength, float tunnelDecay,
                            List<PerkTooltip> perks) {
        this.tier = tier;
        this.chargeTicks = chargeTicks;
        this.maxDamage = maxDamage;
        this.reach = reach;
        this.maxHardnessMultiplier = maxHardnessMultiplier;
        this.veinMinerLimit = veinMinerLimit;
        this.veinMinerDurabilityCost = veinMinerDurabilityCost;
        this.veinMinerPredicate = veinMinerPredicate;
        this.veinMinerRange = veinMinerRange;
        this.tunnelLength = tunnelLength;
        this.tunnelDecay = tunnelDecay;
        this.perks = perks;
    }

    public static CastPickaxeStats iron() {
        List<PerkTooltip> perks = new ArrayList<>();

        // Железная: VeinMiner для руд (малый, 3 блока)
        //
        // Предикат — чёрный список: перечислено то, что жилковым майнером
        // выбивать нельзя. Он не даёт замахнуться на камень и на рудную породу,
        // потому что руда в них живёт: выбивая породу, майнер уносил бы на
        // несколько блоков больше, чем рассчитывает лимит, и руды хватало бы там,
        // где игрок её не видел.
        //
        // Поверх чёрного списка стоит условие по тегу: майнеру годятся только
        // блоки, которые копаются именно киркой ({@code mineable/pickaxe}).
        // Без него чёрный список молча пропускал древесину и всё, что не требует
        // инструмента вовсе, и полностью заряженная кирка выбивала за один удар
        // три бревна ствола подряд. Топорные блоки лежат в {@code mineable/axe},
        // листва и трава — ни в одном из тегов добычи, так что и те, и другие
        // отсеиваются одним условием. Модовные руды при этом проходят: тег
        // открытый, и руда из модов попадает в него же через {@code pickaxes_efficient}
        // либо напрямую.
        Predicate<BlockState> veinMinerPredicate = state -> state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                && !state.is(Blocks.STONE)
                && !state.is(Blocks.COBBLESTONE)
                && !state.is(Blocks.DIRT)
                && !state.is(Blocks.GRASS_BLOCK)
                && !state.is(Blocks.NETHERRACK)
                && !state.is(Blocks.END_STONE)
                && !state.is(Blocks.BASALT)
                && !state.is(Blocks.BLACKSTONE)
                && !state.is(Blocks.TUFF)
                && !state.is(Blocks.CALCITE)
                && !state.is(Blocks.DRIPSTONE_BLOCK)
                && !state.is(Blocks.SMOOTH_BASALT)
                && !state.is(Blocks.COBBLED_DEEPSLATE)
                && !state.is(Blocks.DEEPSLATE)
                && !state.is(BlockTags.STONE_ORE_REPLACEABLES);

        return new CastPickaxeStats(
                net.minecraft.world.item.Tiers.DIAMOND,
                40,
                12.0f,
                5.0f,
                120.0f,
                3,              // veinMinerLimit = 3
                0.25f,          // veinMinerDurabilityCost = 25%
                veinMinerPredicate,
                2,              // veinMinerRange
                0, 0.0f,        // tunnelLength=0
                perks
        );
    }

    public static CastPickaxeStats steel() {
        List<PerkTooltip> perks = new ArrayList<>();

        // Стальная: TunnelMiner (длинный туннель, 7 блоков)
        return new CastPickaxeStats(
                net.minecraft.world.item.Tiers.NETHERITE,
                50,
                15.6f,
                5.0f,
                200.0f,
                0, 0, s -> false, 0,  // нет VeinMiner
                5, 0.25f,
                perks
        );
    }

    // Геттеры
    public Tier getTier() { return tier; }
    public int getChargeTicks() { return chargeTicks; }
    public float getMaxDamage() { return maxDamage; }
    public float getReach() { return reach; }

    public float getMaxHardness(float chargePercent) {
        float maxHardnessAtFullCharge = (tier.getSpeed() * maxHardnessMultiplier) / 30.0f;
        return maxHardnessAtFullCharge * chargePercent;
    }

    public int getMiningSecondsEquivalent() {
        return (int)(maxHardnessMultiplier / 20f);
    }

    public int getVeinMinerLimit() { return veinMinerLimit; }
    public float getVeinMinerDurabilityCost() { return veinMinerDurabilityCost; }
    public boolean canVeinMine(BlockState state) { return veinMinerPredicate.test(state); }
    public int getVeinMinerRange() { return veinMinerRange; }
    public List<PerkTooltip> getPerks() { return perks; }

    public int getTunnelLength() { return tunnelLength; }
    public float getTunnelDecay() { return tunnelDecay; }

    public int getConglomerateTierLevel() {
        if (tier == net.minecraft.world.item.Tiers.IRON) return 0;
        if (tier == net.minecraft.world.item.Tiers.DIAMOND) return 1;
        if (tier == net.minecraft.world.item.Tiers.NETHERITE) return 2;
        return 0;
    }
}
