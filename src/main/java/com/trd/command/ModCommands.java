package com.trd.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.trd.api.oil.VeinShape;
import com.trd.main.MainRegistry;
import com.trd.worldgen.feature.oil.OilCrossChunkVeins;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@EventBusSubscriber(modid = MainRegistry.MOD_ID)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Основная ветка: /trd oil locate & /trd oil sector
        dispatcher.register(
                Commands.literal("trd")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("oil")
                                .then(Commands.literal("locate")
                                        .executes(ModCommands::locateNearestOil))
                                .then(Commands.literal("sector")
                                        .executes(ModCommands::scanCurrentSector))
                                .then(Commands.literal("scan")
                                        .executes(ModCommands::scanCurrentSector))
                        )
        );

        // Быстрые псевдонимы (shortcuts)
        dispatcher.register(
                Commands.literal("locate_oil")
                        .requires(source -> source.hasPermission(2))
                        .executes(ModCommands::locateNearestOil)
        );

        dispatcher.register(
                Commands.literal("oil_sector")
                        .requires(source -> source.hasPermission(2))
                        .executes(ModCommands::scanCurrentSector)
        );
    }

    private static int locateNearestOil(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        long seed = level.getSeed();

        Vec3 pos = source.getPosition();
        int playerX = Mth.floor(pos.x);
        int playerY = Mth.floor(pos.y);
        int playerZ = Mth.floor(pos.z);

        int curBx = Math.floorDiv(playerX, OilCrossChunkVeins.BASIN_SIZE);
        int curBz = Math.floorDiv(playerZ, OilCrossChunkVeins.BASIN_SIZE);

        OilCrossChunkVeins.OilVein bestVein = null;
        double bestDistSq = Double.MAX_VALUE;

        // Сканируем кольцами вокруг текущего бассейна (радиус до 5 бассейнов = ~9000 блоков)
        for (int ring = 0; ring <= 5; ring++) {
            List<OilCrossChunkVeins.OilVein> ringVeins = new ArrayList<>();
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;

                    int bx = curBx + dx;
                    int bz = curBz + dz;
                    List<OilCrossChunkVeins.OilVein> basinVeins = OilCrossChunkVeins.getVeinsForBasin(level, seed, bx, bz);
                    ringVeins.addAll(basinVeins);
                }
            }

            for (OilCrossChunkVeins.OilVein v : ringVeins) {
                double dy = (v.cy - playerY);
                double dSq = (double) (v.cx - playerX) * (v.cx - playerX) + dy * dy * 0.25 + (double) (v.cz - playerZ) * (v.cz - playerZ);
                if (dSq < bestDistSq) {
                    bestDistSq = dSq;
                    bestVein = v;
                }
            }

            // Если в текущем кольце уже найдена жила и она ближе, чем минимальная дистанция до следующего кольца
            if (bestVein != null) {
                double minNextRingDist = (ring) * OilCrossChunkVeins.BASIN_SIZE - OilCrossChunkVeins.BASIN_SIZE * 0.5;
                if (minNextRingDist > 0 && Math.sqrt(bestDistSq) < minNextRingDist) {
                    break;
                }
                if (ring >= 1 && bestVein != null) {
                    break;
                }
            }
        }

        if (bestVein == null) {
            source.sendFailure(Component.literal("§c[TRD Oil Locator] В радиусе 9000 блоков нефтяных залежей не обнаружено."));
            return 0;
        }

        final OilCrossChunkVeins.OilVein targetVein = bestVein;
        int dist = (int) Math.round(Math.sqrt((double) (targetVein.cx - playerX) * (targetVein.cx - playerX) + (double) (targetVein.cz - playerZ) * (targetVein.cz - playerZ)));

        source.sendSuccess(() -> Component.literal("§6══════════════ [TRD OIL LOCATOR] ══════════════"), false);

        if (targetVein.isField) {
            source.sendSuccess(() -> Component.literal("§a★ Месторождение: §f\"" + targetVein.fieldName + "\"")
                    .append(Component.literal(String.format(Locale.ROOT, " §2[Карман %d из %d]", targetVein.pocketIndex, targetVein.totalPockets))), false);
        } else {
            source.sendSuccess(() -> Component.literal("§7Залежь: §fОдиночный нефтяной карман"), false);
        }

        if (targetVein.shape == VeinShape.DIAPIR) {
            source.sendSuccess(() -> Component.literal("§7Форма коллектора: §e" + targetVein.shape.getDisplayNameRu()
                    + " §7(Радиус R=" + Math.round(targetVein.horizontalReach()) + " бл., глубина Y от -50 до +45)"), false);
            source.sendSuccess(() -> Component.literal("§7Тип породы: §fКомбинированный: Сланец (Y<0) / Пористый песчаник (Y≥0)"), false);
        } else {
            source.sendSuccess(() -> Component.literal("§7Форма коллектора: §e" + targetVein.shape.getDisplayNameRu()
                    + " §7(Радиус R=" + Math.round(targetVein.horizontalReach()) + " бл.)"), false);
            source.sendSuccess(() -> Component.literal("§7Тип породы: §f" + targetVein.reservoirType.getDisplayNameRu()), false);
        }

        MutableComponent tpButton = Component.literal(String.format(Locale.ROOT, "[%d, %d, %d]", targetVein.cx, targetVein.cy, targetVein.cz))
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                String.format(Locale.ROOT, "/tp @s %d %d %d", targetVein.cx, targetVein.cy, targetVein.cz)))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("§eНажмите, чтобы телепортироваться к центру залежи"))));

        source.sendSuccess(() -> Component.literal("§aКоординаты: ").append(tpButton)
                .append(Component.literal(String.format(Locale.ROOT, " §7(дистанция: §e%d§7 блоков)", dist))), false);

        source.sendSuccess(() -> Component.literal("§6═════════════════════════════════════════════"), false);
        return 1;
    }

    private static int scanCurrentSector(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        long seed = level.getSeed();

        Vec3 pos = source.getPosition();
        int playerX = Mth.floor(pos.x);
        int playerZ = Mth.floor(pos.z);

        int curBx = Math.floorDiv(playerX, OilCrossChunkVeins.BASIN_SIZE);
        int curBz = Math.floorDiv(playerZ, OilCrossChunkVeins.BASIN_SIZE);

        int minX = curBx * OilCrossChunkVeins.BASIN_SIZE;
        int maxX = minX + OilCrossChunkVeins.BASIN_SIZE - 1;
        int minZ = curBz * OilCrossChunkVeins.BASIN_SIZE;
        int maxZ = minZ + OilCrossChunkVeins.BASIN_SIZE - 1;

        source.sendSuccess(() -> Component.literal("§6════════════ [НЕФТЯНОЙ СЕКТОР] ════════════"), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§7Сектор макро-бассейна: §e[%d, %d] §7(X: %d..%d, Z: %d..%d)", curBx, curBz, minX, maxX, minZ, maxZ)), false);

        List<OilCrossChunkVeins.OilVein> veins = OilCrossChunkVeins.getVeinsForBasin(level, seed, curBx, curBz);

        if (veins.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§cВ данном секторе нет нефтяных пластов (сухой бассейн)."), false);
            source.sendSuccess(() -> Component.literal("§7Используйте §e/locate_oil §7для поиска ближайшей жилы в других секторах."), false);
        } else {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "§a✓ В секторе найдена нефтеносная система! Всего карманов: §e%d", veins.size())), false);

            if (veins.get(0).isField) {
                source.sendSuccess(() -> Component.literal("§a★ Месторождение: §f\"" + veins.get(0).fieldName + "\""), false);
            }

            for (OilCrossChunkVeins.OilVein v : veins) {
                int dist = (int) Math.round(Math.sqrt((double) (v.cx - playerX) * (v.cx - playerX) + (double) (v.cz - playerZ) * (v.cz - playerZ)));

                MutableComponent tpLink = Component.literal(String.format(Locale.ROOT, "[%d, %d, %d]", v.cx, v.cy, v.cz))
                        .withStyle(style -> style
                                .withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        String.format(Locale.ROOT, "/tp @s %d %d %d", v.cx, v.cy, v.cz)))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§eТелепортироваться к карману"))));

                String rockDesc = v.shape == VeinShape.DIAPIR
                        ? "Комбинированный (Сланец/Песчаник)"
                        : v.reservoirType.getDisplayNameRu();

                source.sendSuccess(() -> Component.literal("  • ")
                        .append(tpLink)
                        .append(Component.literal(String.format(Locale.ROOT, " §f%s §7[%s, R=%d, Дист.: §e%d§7 бл.]",
                                v.shape.getDisplayNameRu(), rockDesc, Math.round(v.horizontalReach()), dist))), false);
            }
        }

        source.sendSuccess(() -> Component.literal("§6═══════════════════════════════════════════"), false);
        return 1;
    }
}
