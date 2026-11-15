package com.trd.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.trd.client.config.ModClientConfig;
import com.trd.main.MainRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Клиентские команды мода (работают только на клиенте).
 * <p>
 * Отладочный режим тепла — чисто клиентская штука, поэтому и настройки его
 * задаются клиентскими командами, а не серверными.
 *
 * <pre>
 *   /trdheat radius [1..100] — радиус сферы отладки вокруг игрока
 *   /trdheat allow            — разрешить отладку в выживании и приключении
 * </pre>
 */
@EventBusSubscriber(modid = MainRegistry.MOD_ID, value = Dist.CLIENT)
public class ModClientCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("trdheat")
                        .then(Commands.literal("radius")
                                .executes(ModClientCommands::showRadius)
                                .then(Commands.argument("value",
                                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
                                        .executes(ModClientCommands::setRadius))
                        )
                        .then(Commands.literal("allow")
                                .executes(ModClientCommands::toggleAllow)
                        )
                        .executes(ModClientCommands::showHelp)
        );
    }

    private static int showRadius(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(
                () -> Component.translatable("chat.trd.heat_debug.radius",
                        Component.literal(String.valueOf(ModClientConfig.heatDebugRadius()))),
                false);
        return 1;
    }

    private static int setRadius(CommandContext<CommandSourceStack> ctx) {
        Integer value = ctx.getArgument("value", Integer.class);
        ModClientConfig.setHeatDebugRadius(value);
        ctx.getSource().sendSuccess(
                () -> Component.translatable("chat.trd.heat_debug.radius_set",
                        Component.literal(String.valueOf(value))),
                false);
        return 1;
    }

    private static int toggleAllow(CommandContext<CommandSourceStack> ctx) {
        boolean allow = !ModClientConfig.heatDebugAllowSurvival();
        ModClientConfig.setHeatDebugAllowSurvival(allow);
        ctx.getSource().sendSuccess(
                () -> Component.translatable(allow
                        ? "chat.trd.heat_debug.allow_on"
                        : "chat.trd.heat_debug.allow_off"),
                false);
        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatable("chat.trd.heat_debug.help"), false);
        return 1;
    }
}
